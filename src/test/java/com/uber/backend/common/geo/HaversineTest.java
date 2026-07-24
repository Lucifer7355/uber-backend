package com.uber.backend.common.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HaversineTest {

    @Test
    void distanceKm_samePoint_isZero() {
        GeoPoint p = new GeoPoint(12.93, 77.62);
        assertEquals(0.0, Haversine.distanceKm(p, p), 0.0001);
    }

    @Test
    void distanceKm_knownShortHop_reasonable() {
        GeoPoint a = new GeoPoint(12.9352, 77.6245);
        GeoPoint b = new GeoPoint(12.9716, 77.5946);
        double km = Haversine.distanceKm(a, b);
        assertTrue(km > 3 && km < 8, "expected ~5km, got " + km);
    }

    @Test
    void estimateDuration_invalidSpeed_throws() {
        assertThrows(IllegalArgumentException.class, () -> Haversine.estimateDurationMinutes(5, 0));
    }
}
