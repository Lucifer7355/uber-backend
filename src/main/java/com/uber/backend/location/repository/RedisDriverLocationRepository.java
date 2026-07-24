package com.uber.backend.location.repository;

import com.uber.backend.common.geo.GeoHash;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.config.UberProperties;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.DriverLocation;
import com.uber.backend.location.model.NearbyDriver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis GEO-backed driver index.
 * GEOADD for upserts, GEORADIUS/GEOSEARCH for nearby matching.
 * Hash stores availability + updatedAt metadata keyed by driverId.
 */
public final class RedisDriverLocationRepository implements DriverLocationRepository {

    private static final String META_KEY_PREFIX = "drivers:meta:";

    private final StringRedisTemplate redis;
    private final String geoKey;
    private final int geohashPrecision;
    private final Map<String, DriverAvailability> localAvailabilityCache = new ConcurrentHashMap<>();

    public RedisDriverLocationRepository(StringRedisTemplate redis, UberProperties props) {
        this.redis = redis;
        this.geoKey = props.location().redisGeoKey();
        this.geohashPrecision = props.surge().geohashPrecision();
    }

    @Override
    public void upsert(DriverLocation location) {
        redis.opsForGeo().add(
                geoKey,
                new Point(location.point().longitude(), location.point().latitude()),
                location.driverId());

        String metaKey = META_KEY_PREFIX + location.driverId();
        redis.opsForHash().putAll(metaKey, Map.of(
                "availability", location.availability().name(),
                "updatedAt", location.updatedAt().toString(),
                "lat", String.valueOf(location.point().latitude()),
                "lon", String.valueOf(location.point().longitude())
        ));
        redis.expire(metaKey, 1, TimeUnit.HOURS);
        localAvailabilityCache.put(location.driverId(), location.availability());
    }

    @Override
    public Optional<DriverLocation> findByDriverId(String driverId) {
        Map<Object, Object> meta = redis.opsForHash().entries(META_KEY_PREFIX + driverId);
        if (meta == null || meta.isEmpty()) {
            return Optional.empty();
        }
        double lat = Double.parseDouble(String.valueOf(meta.get("lat")));
        double lon = Double.parseDouble(String.valueOf(meta.get("lon")));
        DriverAvailability availability = DriverAvailability.valueOf(String.valueOf(meta.get("availability")));
        Instant updatedAt = Instant.parse(String.valueOf(meta.get("updatedAt")));
        return Optional.of(new DriverLocation(driverId, new GeoPoint(lat, lon), updatedAt, availability));
    }

    @Override
    public void remove(String driverId) {
        redis.opsForGeo().remove(geoKey, driverId);
        redis.delete(META_KEY_PREFIX + driverId);
        localAvailabilityCache.remove(driverId);
    }

    @Override
    public List<NearbyDriver> findNearby(GeoPoint center, double radiusKm, int limit) {
        Circle circle = new Circle(
                new Point(center.longitude(), center.latitude()),
                new Distance(radiusKm, Metrics.KILOMETERS));

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(
                geoKey,
                circle,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(Math.max(1, limit * 3L)));

        if (results == null) {
            return List.of();
        }

        return results.getContent().stream()
                .map(this::toNearby)
                .filter(n -> isAvailable(n.driverId()))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public long countAvailableInGeohash(String geohashPrefix) {
        GeoPoint center = GeoHash.decodeCenter(geohashPrefix);
        // Approximate cell with ~1.5km radius for precision-6; scale loosely by prefix length.
        double radiusKm = Math.max(0.5, 20.0 / Math.pow(2, Math.max(0, geohashPrefix.length() - 3)));
        return findNearby(center, radiusKm, 500).stream()
                .filter(n -> GeoHash.encode(n.point(), geohashPrecision).startsWith(geohashPrefix))
                .count();
    }

    @Override
    public void updateAvailability(String driverId, DriverAvailability availability) {
        String metaKey = META_KEY_PREFIX + driverId;
        if (Boolean.FALSE.equals(redis.hasKey(metaKey))) {
            return;
        }
        redis.opsForHash().put(metaKey, "availability", availability.name());
        localAvailabilityCache.put(driverId, availability);
    }

    private NearbyDriver toNearby(GeoResult<RedisGeoCommands.GeoLocation<String>> result) {
        RedisGeoCommands.GeoLocation<String> content = result.getContent();
        Point p = content.getPoint();
        double distanceKm = result.getDistance().getValue();
        return new NearbyDriver(
                content.getName(),
                new GeoPoint(p.getY(), p.getX()),
                distanceKm);
    }

    private boolean isAvailable(String driverId) {
        DriverAvailability cached = localAvailabilityCache.get(driverId);
        if (cached != null) {
            return cached == DriverAvailability.AVAILABLE;
        }
        return findByDriverId(driverId).map(DriverLocation::isAvailable).orElse(false);
    }
}
