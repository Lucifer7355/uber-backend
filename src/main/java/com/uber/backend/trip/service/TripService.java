package com.uber.backend.trip.service;

import com.uber.backend.common.exception.NotFoundException;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.common.id.IdGenerator;
import com.uber.backend.config.UberProperties;
import com.uber.backend.event.bus.EventPublisher;
import com.uber.backend.event.model.DomainEvent;
import com.uber.backend.matching.model.MatchResult;
import com.uber.backend.matching.service.DriverMatchingService;
import com.uber.backend.pricing.model.FareEstimate;
import com.uber.backend.pricing.service.PricingService;
import com.uber.backend.pricing.service.SurgePricingService;
import com.uber.backend.trip.model.Trip;
import com.uber.backend.trip.model.TripStatus;
import com.uber.backend.trip.repository.InMemoryTripRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TripService {

    private static final Logger log = LoggerFactory.getLogger(TripService.class);

    private final InMemoryTripRepository tripRepository;
    private final DriverMatchingService matchingService;
    private final PricingService pricingService;
    private final SurgePricingService surgePricingService;
    private final EventPublisher eventPublisher;
    private final UberProperties props;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public TripService(
            InMemoryTripRepository tripRepository,
            DriverMatchingService matchingService,
            PricingService pricingService,
            SurgePricingService surgePricingService,
            EventPublisher eventPublisher,
            UberProperties props,
            IdGenerator idGenerator,
            Clock clock) {
        this.tripRepository = tripRepository;
        this.matchingService = matchingService;
        this.pricingService = pricingService;
        this.surgePricingService = surgePricingService;
        this.eventPublisher = eventPublisher;
        this.props = props;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public Trip requestTrip(String riderId, GeoPoint pickup, GeoPoint dropoff) {
        Instant now = clock.instant();
        String tripId = idGenerator.nextId("trip");
        Trip trip = new Trip(tripId, riderId, pickup, dropoff, TripStatus.REQUESTED, now);
        tripRepository.save(trip);

        FareEstimate fare = pricingService.estimate(pickup, dropoff);
        MatchResult match = matchingService.match(riderId, pickup);

        if (!match.matched()) {
            trip.markNoDrivers(clock.instant());
            publishTripEvent(trip, "TRIP_NO_DRIVERS", Map.of("fare", fare.totalFare()));
            surgePricingService.releaseRideRequest(pickup);
            log.info("trip {} no drivers for rider {}", tripId, riderId);
            return trip;
        }

        trip.assignDriver(match.selected().driverId(), fare, clock.instant());
        publishTripEvent(trip, "TRIP_MATCHED", Map.of(
                "driverId", match.selected().driverId(),
                "distanceKm", match.selected().distanceKm(),
                "fare", fare.totalFare(),
                "surge", fare.surgeMultiplier()
        ));
        publishNotification(trip, "You have been matched with a rider", "DRIVER");
        publishNotification(trip, "Driver " + match.selected().driverId() + " is on the way", "RIDER");
        log.info("trip {} matched driver={} fare={}", tripId, match.selected().driverId(), fare.totalFare());
        return trip;
    }

    public Trip driverArriving(String tripId) {
        Trip trip = requireTrip(tripId);
        trip.markDriverArriving(clock.instant());
        publishTripEvent(trip, "TRIP_DRIVER_ARRIVING", Map.of());
        publishNotification(trip, "Your driver is arriving", "RIDER");
        return trip;
    }

    public Trip startTrip(String tripId) {
        Trip trip = requireTrip(tripId);
        trip.start(clock.instant());
        surgePricingService.releaseRideRequest(trip.pickup());
        publishTripEvent(trip, "TRIP_STARTED", Map.of());
        publishNotification(trip, "Trip started", "BOTH");
        return trip;
    }

    public Trip completeTrip(String tripId) {
        Trip trip = requireTrip(tripId);
        trip.complete(clock.instant());
        if (trip.driverId() != null) {
            matchingService.releaseDriver(trip.driverId());
        }
        publishTripEvent(trip, "TRIP_COMPLETED", Map.of(
                "fare", trip.fare() == null ? 0 : trip.fare().totalFare()
        ));
        publishNotification(trip, "Trip completed. Fare: " + (trip.fare() == null ? 0 : trip.fare().totalFare()), "BOTH");
        return trip;
    }

    public Trip cancelTrip(String tripId) {
        Trip trip = requireTrip(tripId);
        trip.cancel(clock.instant());
        if (trip.driverId() != null) {
            matchingService.releaseDriver(trip.driverId());
        }
        surgePricingService.releaseRideRequest(trip.pickup());
        publishTripEvent(trip, "TRIP_CANCELLED", Map.of());
        publishNotification(trip, "Trip cancelled", "BOTH");
        return trip;
    }

    public Trip getTrip(String tripId) {
        return requireTrip(tripId);
    }

    private Trip requireTrip(String tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new NotFoundException("trip not found: " + tripId));
    }

    private void publishTripEvent(Trip trip, String type, Map<String, Object> extra) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tripId", trip.tripId());
        payload.put("riderId", trip.riderId());
        payload.put("driverId", trip.driverId());
        payload.put("status", trip.status().name());
        payload.putAll(extra);
        DomainEvent event = new DomainEvent(
                idGenerator.nextId("evt"),
                type,
                trip.tripId(),
                clock.instant(),
                payload);
        eventPublisher.publish(props.kafka().topics().tripEvents(), trip.tripId(), event);
    }

    private void publishNotification(Trip trip, String message, String audience) {
        DomainEvent event = new DomainEvent(
                idGenerator.nextId("evt"),
                "NOTIFICATION",
                trip.tripId(),
                clock.instant(),
                Map.of(
                        "tripId", trip.tripId(),
                        "riderId", trip.riderId(),
                        "driverId", trip.driverId() == null ? "" : trip.driverId(),
                        "message", message,
                        "audience", audience
                ));
        eventPublisher.publish(props.kafka().topics().notifications(), trip.tripId(), event);
    }
}
