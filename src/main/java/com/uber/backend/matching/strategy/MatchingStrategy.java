package com.uber.backend.matching.strategy;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.NearbyDriver;
import java.util.List;
import java.util.Optional;

/**
 * Strategy for picking a driver from GEO candidates.
 * Default: nearest available. Extensible for rating, ETA, batching.
 */
public interface MatchingStrategy {

    Optional<NearbyDriver> select(GeoPoint pickup, List<NearbyDriver> candidates);
}
