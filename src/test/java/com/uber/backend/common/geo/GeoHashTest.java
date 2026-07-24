package com.uber.backend.common.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeoHashTest {

    @Test
    void encode_bangalorePoint_stableHash() {
        GeoPoint point = new GeoPoint(12.9352, 77.6245);
        String hash = GeoHash.encode(point, 6);
        assertEquals(6, hash.length());
        GeoPoint center = GeoHash.decodeCenter(hash);
        double distance = Haversine.distanceKm(point, center);
        assertTrue(distance < 1.5, "center should be inside ~precision-6 cell, was " + distance);
    }

    @Test
    void encode_invalidPrecision_throws() {
        assertThrows(IllegalArgumentException.class, () -> GeoHash.encode(new GeoPoint(0, 0), 0));
    }
}
