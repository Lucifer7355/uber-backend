package com.uber.backend.location.model;

import com.uber.backend.common.geo.GeoPoint;

public record NearbyDriver(String driverId, GeoPoint point, double distanceKm) {
}
