package com.uber.backend.trip.repository;

import com.uber.backend.trip.model.Trip;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryTripRepository {

    private final ConcurrentMap<String, Trip> trips = new ConcurrentHashMap<>();

    public Trip save(Trip trip) {
        trips.put(trip.tripId(), trip);
        return trip;
    }

    public Optional<Trip> findById(String tripId) {
        return Optional.ofNullable(trips.get(tripId));
    }

    public Collection<Trip> findAll() {
        return trips.values();
    }

    public void clear() {
        trips.clear();
    }
}
