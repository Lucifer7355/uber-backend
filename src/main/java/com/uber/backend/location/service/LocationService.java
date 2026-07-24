package com.uber.backend.location.service;

import com.uber.backend.common.geo.GeoHash;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.common.id.IdGenerator;
import com.uber.backend.config.UberProperties;
import com.uber.backend.event.bus.EventPublisher;
import com.uber.backend.event.model.DomainEvent;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.DriverLocation;
import com.uber.backend.location.model.NearbyDriver;
import com.uber.backend.location.repository.DriverLocationRepository;
import com.uber.backend.pricing.service.SurgePricingService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);

    private final DriverLocationRepository repository;
    private final EventPublisher eventPublisher;
    private final SurgePricingService surgePricingService;
    private final UberProperties props;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public LocationService(
            DriverLocationRepository repository,
            EventPublisher eventPublisher,
            SurgePricingService surgePricingService,
            UberProperties props,
            Clock clock,
            IdGenerator idGenerator) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.surgePricingService = surgePricingService;
        this.props = props;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    public DriverLocation updateLocation(String driverId, GeoPoint point, DriverAvailability availability) {
        Instant now = clock.instant();
        DriverLocation location = new DriverLocation(driverId, point, now, availability);
        repository.upsert(location);

        String geohash = GeoHash.encode(point, props.surge().geohashPrecision());
        surgePricingService.recordDriverPresence(driverId, geohash, availability);

        DomainEvent event = new DomainEvent(
                idGenerator.nextId("evt"),
                "LOCATION_UPDATED",
                driverId,
                now,
                Map.of(
                        "driverId", driverId,
                        "lat", point.latitude(),
                        "lon", point.longitude(),
                        "availability", availability.name(),
                        "geohash", geohash
                ));
        eventPublisher.publish(props.kafka().topics().locationUpdates(), driverId, event);
        log.info("location updated driver={} geohash={} availability={}", driverId, geohash, availability);
        return location;
    }

    public List<NearbyDriver> findNearbyDrivers(GeoPoint pickup, double radiusKm, int limit) {
        return repository.findNearby(pickup, radiusKm, limit);
    }

    public Optional<DriverLocation> getDriver(String driverId) {
        return repository.findByDriverId(driverId);
    }

    public void setAvailability(String driverId, DriverAvailability availability) {
        repository.updateAvailability(driverId, availability);
        repository.findByDriverId(driverId).ifPresent(loc -> {
            String geohash = GeoHash.encode(loc.point(), props.surge().geohashPrecision());
            surgePricingService.recordDriverPresence(driverId, geohash, availability);
        });
    }

    public void goOffline(String driverId) {
        repository.findByDriverId(driverId).ifPresent(loc -> {
            String geohash = GeoHash.encode(loc.point(), props.surge().geohashPrecision());
            surgePricingService.recordDriverPresence(driverId, geohash, DriverAvailability.OFFLINE);
        });
        repository.remove(driverId);
    }
}
