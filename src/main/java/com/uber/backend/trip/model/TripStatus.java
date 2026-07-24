package com.uber.backend.trip.model;

public enum TripStatus {
    REQUESTED,
    MATCHED,
    DRIVER_ARRIVING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    NO_DRIVERS
}
