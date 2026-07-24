package com.uber.backend.trip.model;

import com.uber.backend.common.exception.ValidationException;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.pricing.model.FareEstimate;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class Trip {

    private final String tripId;
    private final String riderId;
    private String driverId;
    private final GeoPoint pickup;
    private final GeoPoint dropoff;
    private TripStatus status;
    private FareEstimate fare;
    private final Instant createdAt;
    private Instant updatedAt;

    public Trip(
            String tripId,
            String riderId,
            GeoPoint pickup,
            GeoPoint dropoff,
            TripStatus status,
            Instant createdAt) {
        this.tripId = Objects.requireNonNull(tripId);
        this.riderId = Objects.requireNonNull(riderId);
        this.pickup = Objects.requireNonNull(pickup);
        this.dropoff = Objects.requireNonNull(dropoff);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    public String tripId() {
        return tripId;
    }

    public String riderId() {
        return riderId;
    }

    public String driverId() {
        return driverId;
    }

    public GeoPoint pickup() {
        return pickup;
    }

    public GeoPoint dropoff() {
        return dropoff;
    }

    public TripStatus status() {
        return status;
    }

    public FareEstimate fare() {
        return fare;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void assignDriver(String driverId, FareEstimate fare, Instant at) {
        requireTransition(TripStatus.MATCHED);
        this.driverId = driverId;
        this.fare = fare;
        this.status = TripStatus.MATCHED;
        this.updatedAt = at;
    }

    public void markNoDrivers(Instant at) {
        requireTransition(TripStatus.NO_DRIVERS);
        this.status = TripStatus.NO_DRIVERS;
        this.updatedAt = at;
    }

    public void markDriverArriving(Instant at) {
        requireTransition(TripStatus.DRIVER_ARRIVING);
        this.status = TripStatus.DRIVER_ARRIVING;
        this.updatedAt = at;
    }

    public void start(Instant at) {
        requireTransition(TripStatus.IN_PROGRESS);
        this.status = TripStatus.IN_PROGRESS;
        this.updatedAt = at;
    }

    public void complete(Instant at) {
        requireTransition(TripStatus.COMPLETED);
        this.status = TripStatus.COMPLETED;
        this.updatedAt = at;
    }

    public void cancel(Instant at) {
        requireTransition(TripStatus.CANCELLED);
        this.status = TripStatus.CANCELLED;
        this.updatedAt = at;
    }

    private void requireTransition(TripStatus target) {
        if (!allowedTransitions(status).contains(target)) {
            throw new ValidationException(
                    "illegal trip transition " + status + " -> " + target + " for " + tripId);
        }
    }

    private static Set<TripStatus> allowedTransitions(TripStatus from) {
        return switch (from) {
            case REQUESTED -> EnumSet.of(TripStatus.MATCHED, TripStatus.NO_DRIVERS, TripStatus.CANCELLED);
            case MATCHED -> EnumSet.of(TripStatus.DRIVER_ARRIVING, TripStatus.CANCELLED);
            case DRIVER_ARRIVING -> EnumSet.of(TripStatus.IN_PROGRESS, TripStatus.CANCELLED);
            case IN_PROGRESS -> EnumSet.of(TripStatus.COMPLETED, TripStatus.CANCELLED);
            case COMPLETED, CANCELLED, NO_DRIVERS -> EnumSet.noneOf(TripStatus.class);
        };
    }
}
