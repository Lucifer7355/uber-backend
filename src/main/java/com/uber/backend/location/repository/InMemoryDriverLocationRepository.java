package com.uber.backend.location.repository;

import com.uber.backend.common.geo.GeoHash;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.common.geo.Haversine;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.DriverLocation;
import com.uber.backend.location.model.NearbyDriver;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe in-memory GEO store for local profile and unit tests.
 * Mirrors Redis GEO semantics: upsert by member id, radius query, remove.
 */
public final class InMemoryDriverLocationRepository implements DriverLocationRepository {

    private final ConcurrentMap<String, DriverLocation> store = new ConcurrentHashMap<>();
    private final int geohashPrecision;

    public InMemoryDriverLocationRepository(int geohashPrecision) {
        this.geohashPrecision = geohashPrecision;
    }

    @Override
    public void upsert(DriverLocation location) {
        store.put(location.driverId(), location);
    }

    @Override
    public Optional<DriverLocation> findByDriverId(String driverId) {
        return Optional.ofNullable(store.get(driverId));
    }

    @Override
    public void remove(String driverId) {
        store.remove(driverId);
    }

    @Override
    public List<NearbyDriver> findNearby(GeoPoint center, double radiusKm, int limit) {
        return store.values().stream()
                .filter(DriverLocation::isAvailable)
                .map(loc -> new NearbyDriver(
                        loc.driverId(),
                        loc.point(),
                        Haversine.distanceKm(center, loc.point())))
                .filter(n -> n.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(NearbyDriver::distanceKm))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public long countAvailableInGeohash(String geohashPrefix) {
        return store.values().stream()
                .filter(DriverLocation::isAvailable)
                .filter(loc -> GeoHash.encode(loc.point(), geohashPrecision).startsWith(geohashPrefix))
                .count();
    }

    @Override
    public void updateAvailability(String driverId, DriverAvailability availability) {
        store.computeIfPresent(driverId, (id, loc) -> loc.withAvailability(availability));
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
