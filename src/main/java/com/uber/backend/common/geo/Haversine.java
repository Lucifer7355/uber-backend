package com.uber.backend.common.geo;

/**
 * Haversine great-circle distance. Pure function — no I/O, easy to unit test.
 */
public final class Haversine {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {
    }

    public static double distanceKm(GeoPoint a, GeoPoint b) {
        ObjectsRequire(a, b);
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());

        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    public static int estimateDurationMinutes(double distanceKm, double avgSpeedKmh) {
        if (avgSpeedKmh <= 0) {
            throw new IllegalArgumentException("avgSpeedKmh must be > 0");
        }
        if (distanceKm < 0) {
            throw new IllegalArgumentException("distanceKm must be >= 0");
        }
        return Math.max(1, (int) Math.ceil((distanceKm / avgSpeedKmh) * 60.0));
    }

    private static void ObjectsRequire(GeoPoint a, GeoPoint b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("GeoPoints must not be null");
        }
    }
}
