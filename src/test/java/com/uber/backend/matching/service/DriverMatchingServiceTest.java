package com.uber.backend.matching.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.common.id.IdGenerator;
import com.uber.backend.config.UberProperties;
import com.uber.backend.event.bus.InMemoryEventPublisher;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.repository.InMemoryDriverLocationRepository;
import com.uber.backend.location.service.LocationService;
import com.uber.backend.matching.model.MatchResult;
import com.uber.backend.matching.strategy.NearestDriverStrategy;
import com.uber.backend.pricing.service.SurgePricingService;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriverMatchingServiceTest {

    private LocationService locationService;
    private DriverMatchingService matchingService;
    private static final GeoPoint PICKUP = new GeoPoint(12.9352, 77.6245);

    @BeforeEach
    void setUp() {
        UberProperties props = props();
        InMemoryDriverLocationRepository repo = new InMemoryDriverLocationRepository(6);
        InMemoryEventPublisher events = new InMemoryEventPublisher();
        SurgePricingService surge = new SurgePricingService(props);
        locationService = new LocationService(repo, events, surge, props, Clock.systemUTC(), IdGenerator.sequential());
        matchingService = new DriverMatchingService(locationService, new NearestDriverStrategy(), surge, props);
    }

    @Test
    void match_selectsNearestDriver() {
        locationService.updateLocation("far", new GeoPoint(12.9500, 77.6400), DriverAvailability.AVAILABLE);
        locationService.updateLocation("near", new GeoPoint(12.9360, 77.6250), DriverAvailability.AVAILABLE);

        MatchResult result = matchingService.match("rider-1", PICKUP);

        assertTrue(result.matched());
        assertEquals("near", result.selected().driverId());
    }

    @Test
    void match_noDrivers_returnsUnmatched() {
        MatchResult result = matchingService.match("rider-1", PICKUP);
        assertNull(result.selected());
    }

    @Test
    void match_concurrentClaims_onlyOneWins() throws Exception {
        locationService.updateLocation("only", new GeoPoint(12.9360, 77.6250), DriverAvailability.AVAILABLE);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger wins = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final String rider = "rider-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    MatchResult r = matchingService.match(rider, PICKUP);
                    if (r.matched()) {
                        wins.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // claim races may leave some unmatched — that is expected
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, wins.get(), "exactly one rider should claim the single driver");
        assertNotNull(locationService.getDriver("only").orElseThrow());
    }

    private static UberProperties props() {
        return new UberProperties(
                new UberProperties.Matching(5, 10, 15),
                new UberProperties.Pricing(40, 12, 2, 60, "INR"),
                new UberProperties.Surge(6, 3, 2, 3.0),
                new UberProperties.Location("drivers:geo", 60),
                new UberProperties.Kafka(new UberProperties.Kafka.Topics("location.updates", "trip.events", "notifications"))
        );
    }
}
