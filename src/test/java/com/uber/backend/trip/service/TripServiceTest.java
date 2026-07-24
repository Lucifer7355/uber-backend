package com.uber.backend.trip.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.common.id.IdGenerator;
import com.uber.backend.config.UberProperties;
import com.uber.backend.event.bus.InMemoryEventPublisher;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.repository.InMemoryDriverLocationRepository;
import com.uber.backend.location.service.LocationService;
import com.uber.backend.matching.service.DriverMatchingService;
import com.uber.backend.matching.strategy.NearestDriverStrategy;
import com.uber.backend.notification.service.NotificationService;
import com.uber.backend.notification.websocket.TripWebSocketHandler;
import com.uber.backend.pricing.service.PricingService;
import com.uber.backend.pricing.service.SurgePricingService;
import com.uber.backend.trip.model.Trip;
import com.uber.backend.trip.model.TripStatus;
import com.uber.backend.trip.repository.InMemoryTripRepository;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TripServiceTest {

    private TripService tripService;
    private LocationService locationService;
    private NotificationService notificationService;
    private InMemoryEventPublisher events;
    private UberProperties props;

    private static final GeoPoint PICKUP = new GeoPoint(12.9352, 77.6245);
    private static final GeoPoint DROPOFF = new GeoPoint(12.9716, 77.5946);

    @BeforeEach
    void setUp() {
        props = new UberProperties(
                new UberProperties.Matching(5, 10, 15),
                new UberProperties.Pricing(40, 12, 2, 60, "INR"),
                new UberProperties.Surge(6, 3, 2, 3.0),
                new UberProperties.Location("drivers:geo", 60),
                new UberProperties.Kafka(new UberProperties.Kafka.Topics(
                        "location.updates", "trip.events", "notifications"))
        );
        events = new InMemoryEventPublisher();
        InMemoryDriverLocationRepository repo = new InMemoryDriverLocationRepository(6);
        SurgePricingService surge = new SurgePricingService(props);
        locationService = new LocationService(
                repo, events, surge, props, Clock.systemUTC(), IdGenerator.sequential());
        DriverMatchingService matching = new DriverMatchingService(
                locationService, new NearestDriverStrategy(), surge, props);
        PricingService pricing = new PricingService(props, surge);
        tripService = new TripService(
                new InMemoryTripRepository(),
                matching,
                pricing,
                surge,
                events,
                props,
                IdGenerator.sequential(),
                Clock.systemUTC());
        notificationService = new NotificationService(
                new TripWebSocketHandler(new ObjectMapper()),
                IdGenerator.sequential(),
                Clock.systemUTC());
        events.subscribe(props.kafka().topics().notifications(), notificationService::handleNotificationEvent);
    }

    @Test
    void requestTrip_happyPath_completesAndNotifies() {
        locationService.updateLocation("D1", new GeoPoint(12.9360, 77.6250), DriverAvailability.AVAILABLE);

        Trip trip = tripService.requestTrip("R1", PICKUP, DROPOFF);
        assertEquals(TripStatus.MATCHED, trip.status());
        assertEquals("D1", trip.driverId());
        assertNotNull(trip.fare());

        tripService.driverArriving(trip.tripId());
        tripService.startTrip(trip.tripId());
        Trip done = tripService.completeTrip(trip.tripId());
        assertEquals(TripStatus.COMPLETED, done.status());
        assertTrue(notificationService.recent(10).size() >= 2);
        assertTrue(events.history().stream().anyMatch(e -> e.type().startsWith("TRIP_")));
    }

    @Test
    void requestTrip_noDrivers_setsNoDrivers() {
        Trip trip = tripService.requestTrip("R1", PICKUP, DROPOFF);
        assertEquals(TripStatus.NO_DRIVERS, trip.status());
    }
}
