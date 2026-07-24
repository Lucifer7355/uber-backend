package com.uber.backend.trip.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.uber.backend.common.exception.ValidationException;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.pricing.model.FareEstimate;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TripTest {

    @Test
    void happyPathTransitions() {
        Trip trip = new Trip("t1", "r1", new GeoPoint(12.93, 77.62), new GeoPoint(12.97, 77.59),
                TripStatus.REQUESTED, Instant.now());
        FareEstimate fare = new FareEstimate(5, 12, 40, 60, 24, 1.0, 124, "INR", "abc");
        trip.assignDriver("d1", fare, Instant.now());
        trip.markDriverArriving(Instant.now());
        trip.start(Instant.now());
        trip.complete(Instant.now());
        assertEquals(TripStatus.COMPLETED, trip.status());
    }

    @Test
    void completeFromRequested_throws() {
        Trip trip = new Trip("t1", "r1", new GeoPoint(12.93, 77.62), new GeoPoint(12.97, 77.59),
                TripStatus.REQUESTED, Instant.now());
        assertThrows(ValidationException.class, () -> trip.complete(Instant.now()));
    }
}
