package com.uber.backend.location.model;

import com.uber.backend.common.geo.GeoPoint;
import java.time.Instant;
import java.util.Objects;

public final class DriverLocation {

    private final String driverId;
    private final GeoPoint point;
    private final Instant updatedAt;
    private final DriverAvailability availability;

    public DriverLocation(String driverId, GeoPoint point, Instant updatedAt, DriverAvailability availability) {
        if (driverId == null || driverId.isBlank()) {
            throw new IllegalArgumentException("driverId must not be blank");
        }
        this.driverId = driverId;
        this.point = Objects.requireNonNull(point, "point");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.availability = Objects.requireNonNull(availability, "availability");
    }

    public String driverId() {
        return driverId;
    }

    public GeoPoint point() {
        return point;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public DriverAvailability availability() {
        return availability;
    }

    public boolean isAvailable() {
        return availability == DriverAvailability.AVAILABLE;
    }

    public DriverLocation withAvailability(DriverAvailability next) {
        return new DriverLocation(driverId, point, updatedAt, next);
    }

    public DriverLocation withPoint(GeoPoint next, Instant at) {
        return new DriverLocation(driverId, next, at, availability);
    }
}
