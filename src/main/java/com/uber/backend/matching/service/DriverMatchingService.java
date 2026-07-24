package com.uber.backend.matching.service;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.config.UberProperties;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.NearbyDriver;
import com.uber.backend.location.service.LocationService;
import com.uber.backend.matching.model.MatchResult;
import com.uber.backend.matching.strategy.MatchingStrategy;
import com.uber.backend.pricing.service.SurgePricingService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Driver matching with optimistic claim to avoid double-booking under concurrency.
 * GEO query → strategy select → CAS-style availability flip AVAILABLE → ON_TRIP.
 */
@Service
public class DriverMatchingService {

    private static final Logger log = LoggerFactory.getLogger(DriverMatchingService.class);

    private final LocationService locationService;
    private final MatchingStrategy matchingStrategy;
    private final SurgePricingService surgePricingService;
    private final UberProperties props;
    private final ConcurrentMap<String, String> claimedDrivers = new ConcurrentHashMap<>();

    public DriverMatchingService(
            LocationService locationService,
            MatchingStrategy matchingStrategy,
            SurgePricingService surgePricingService,
            UberProperties props) {
        this.locationService = locationService;
        this.matchingStrategy = matchingStrategy;
        this.surgePricingService = surgePricingService;
        this.props = props;
    }

    public MatchResult match(String riderId, GeoPoint pickup) {
        if (riderId == null || riderId.isBlank()) {
            throw new IllegalArgumentException("riderId must not be blank");
        }
        surgePricingService.recordRideRequest(pickup);

        double radius = props.matching().searchRadiusKm();
        int limit = props.matching().maxCandidates();
        List<NearbyDriver> candidates = locationService.findNearbyDrivers(pickup, radius, limit);

        Optional<NearbyDriver> selected = matchingStrategy.select(pickup, candidates);
        if (selected.isEmpty()) {
            log.info("no drivers near rider={} pickup={}", riderId, pickup);
            return new MatchResult(riderId, pickup, candidates, null);
        }

        NearbyDriver winner = selected.get();
        String firstDriverId = winner.driverId();
        if (!tryClaim(firstDriverId, riderId)) {
            // Another request claimed this driver; retry once with remaining candidates.
            List<NearbyDriver> remaining = candidates.stream()
                    .filter(c -> !c.driverId().equals(firstDriverId))
                    .toList();
            Optional<NearbyDriver> fallback = matchingStrategy.select(pickup, remaining);
            if (fallback.isEmpty() || !tryClaim(fallback.get().driverId(), riderId)) {
                return new MatchResult(riderId, pickup, candidates, null);
            }
            winner = fallback.get();
        }

        locationService.setAvailability(winner.driverId(), DriverAvailability.ON_TRIP);
        log.info("matched rider={} driver={} distanceKm={}", riderId, winner.driverId(), winner.distanceKm());
        return new MatchResult(riderId, pickup, candidates, winner);
    }

    public void releaseDriver(String driverId) {
        claimedDrivers.remove(driverId);
        locationService.setAvailability(driverId, DriverAvailability.AVAILABLE);
    }

    private boolean tryClaim(String driverId, String riderId) {
        String existing = claimedDrivers.putIfAbsent(driverId, riderId);
        return existing == null || existing.equals(riderId);
    }
}
