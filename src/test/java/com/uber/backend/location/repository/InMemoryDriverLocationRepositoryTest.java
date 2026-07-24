package com.uber.backend.location.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.DriverLocation;
import com.uber.backend.location.model.NearbyDriver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryDriverLocationRepositoryTest {

    private InMemoryDriverLocationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDriverLocationRepository(6);
    }

    @Test
    void findNearby_returnsSortedByDistance_andFiltersUnavailable() {
        GeoPoint center = new GeoPoint(12.9352, 77.6245);
        repository.upsert(new DriverLocation("near", new GeoPoint(12.9360, 77.6250), Instant.now(), DriverAvailability.AVAILABLE));
        repository.upsert(new DriverLocation("far", new GeoPoint(12.9500, 77.6400), Instant.now(), DriverAvailability.AVAILABLE));
        repository.upsert(new DriverLocation("busy", new GeoPoint(12.9355, 77.6248), Instant.now(), DriverAvailability.ON_TRIP));

        List<NearbyDriver> nearby = repository.findNearby(center, 5.0, 10);

        assertEquals(2, nearby.size());
        assertEquals("near", nearby.get(0).driverId());
        assertTrue(nearby.get(0).distanceKm() <= nearby.get(1).distanceKm());
    }

    @Test
    void updateAvailability_removesFromNearby() {
        GeoPoint center = new GeoPoint(12.9352, 77.6245);
        repository.upsert(new DriverLocation("d1", new GeoPoint(12.9360, 77.6250), Instant.now(), DriverAvailability.AVAILABLE));
        repository.updateAvailability("d1", DriverAvailability.OFFLINE);

        assertTrue(repository.findNearby(center, 5.0, 10).isEmpty());
    }
}
