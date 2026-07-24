package com.uber.backend.location.repository;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.DriverLocation;
import com.uber.backend.location.model.NearbyDriver;
import java.util.List;
import java.util.Optional;

/**
 * Spatial store for live driver positions.
 * Production: Redis GEO. Local/tests: in-memory ConcurrentHashMap + Haversine.
 */
public interface DriverLocationRepository {

    void upsert(DriverLocation location);

    Optional<DriverLocation> findByDriverId(String driverId);

    void remove(String driverId);

    List<NearbyDriver> findNearby(GeoPoint center, double radiusKm, int limit);

    long countAvailableInGeohash(String geohashPrefix);

    void updateAvailability(String driverId, DriverAvailability availability);
}
