package com.uber.backend.pricing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.config.UberProperties;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.pricing.model.SurgeSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SurgePricingServiceTest {

    private SurgePricingService surge;

    @BeforeEach
    void setUp() {
        UberProperties props = new UberProperties(
                new UberProperties.Matching(5, 10, 15),
                new UberProperties.Pricing(40, 12, 2, 60, "INR"),
                new UberProperties.Surge(6, 3, 2, 3.0),
                new UberProperties.Location("drivers:geo", 60),
                new UberProperties.Kafka(new UberProperties.Kafka.Topics("location.updates", "trip.events", "notifications"))
        );
        surge = new SurgePricingService(props);
    }

    @Test
    void multiplier_lowDemand_isOne() {
        GeoPoint pickup = new GeoPoint(12.9352, 77.6245);
        surge.recordRideRequest(pickup);
        assertEquals(1.0, surge.multiplierFor(pickup));
    }

    @Test
    void multiplier_highDemandLowSupply_increases() {
        GeoPoint pickup = new GeoPoint(12.9352, 77.6245);
        String cell = surge.cellFor(pickup);
        for (int i = 0; i < 6; i++) {
            surge.recordRideRequest(pickup);
        }
        surge.recordDriverPresence("d1", cell, DriverAvailability.AVAILABLE);

        SurgeSnapshot snap = surge.snapshot(cell);
        assertTrue(snap.multiplier() > 1.0, "expected surge, got " + snap);
        assertTrue(snap.multiplier() <= 3.0);
    }

    @Test
    void recordDriverPresence_idempotentForSameCell() {
        String cell = "tdr1v9";
        surge.recordDriverPresence("d1", cell, DriverAvailability.AVAILABLE);
        surge.recordDriverPresence("d1", cell, DriverAvailability.AVAILABLE);
        assertEquals(1, surge.snapshot(cell).supply());
    }
}
