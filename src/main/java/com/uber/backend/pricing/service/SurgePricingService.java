package com.uber.backend.pricing.service;

import com.uber.backend.common.geo.GeoHash;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.config.UberProperties;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.pricing.model.SurgeSnapshot;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * Geohash-cell surge: multiplier rises when demand >> supply in a cell.
 * Driver membership is tracked by driverId so location updates stay idempotent.
 */
@Service
public class SurgePricingService {

    private final UberProperties props;
    private final ConcurrentHashMap<String, AtomicInteger> demandByCell = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> driverCell = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> supplyByCell = new ConcurrentHashMap<>();

    public SurgePricingService(UberProperties props) {
        this.props = props;
    }

    public String cellFor(GeoPoint point) {
        return GeoHash.encode(point, props.surge().geohashPrecision());
    }

    public void recordRideRequest(GeoPoint pickup) {
        String cell = cellFor(pickup);
        demandByCell.computeIfAbsent(cell, c -> new AtomicInteger()).incrementAndGet();
    }

    public void releaseRideRequest(GeoPoint pickup) {
        String cell = cellFor(pickup);
        AtomicInteger demand = demandByCell.get(cell);
        if (demand != null) {
            demand.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    public void recordDriverPresence(String driverId, String geohash, DriverAvailability availability) {
        if (driverId == null || driverId.isBlank() || geohash == null || geohash.isBlank()) {
            return;
        }
        String previousCell = driverCell.get(driverId);
        if (previousCell != null && !previousCell.equals(geohash)) {
            decrementSupply(previousCell);
            driverCell.remove(driverId);
        }

        if (availability == DriverAvailability.AVAILABLE) {
            if (!geohash.equals(driverCell.get(driverId))) {
                driverCell.put(driverId, geohash);
                supplyByCell.computeIfAbsent(geohash, c -> new AtomicInteger()).incrementAndGet();
            }
        } else {
            if (driverCell.remove(driverId) != null) {
                decrementSupply(geohash.equals(previousCell) || previousCell == null ? geohash : previousCell);
            }
        }
    }

    public double multiplierFor(GeoPoint pickup) {
        return snapshot(cellFor(pickup)).multiplier();
    }

    public SurgeSnapshot snapshot(String geohash) {
        int demand = demandByCell.getOrDefault(geohash, new AtomicInteger(0)).get();
        int supply = supplyByCell.getOrDefault(geohash, new AtomicInteger(0)).get();
        return new SurgeSnapshot(geohash, demand, supply, computeMultiplier(demand, supply));
    }

    public SurgeSnapshot snapshotAt(GeoPoint point) {
        return snapshot(cellFor(point));
    }

    double computeMultiplier(int demand, int supply) {
        UberProperties.Surge surge = props.surge();
        if (demand < surge.demandThreshold()) {
            return 1.0;
        }
        double effectiveSupply = Math.max(1, supply);
        if (supply >= surge.supplyThreshold() && demand <= supply) {
            return 1.0;
        }
        double ratio = demand / effectiveSupply;
        double raw = 1.0 + (ratio - 1.0) * 0.5;
        return Math.min(surge.maxMultiplier(), Math.round(raw * 10.0) / 10.0);
    }

    public Map<String, SurgeSnapshot> allSnapshots() {
        ConcurrentHashMap<String, SurgeSnapshot> out = new ConcurrentHashMap<>();
        demandByCell.keySet().forEach(cell -> out.put(cell, snapshot(cell)));
        supplyByCell.keySet().forEach(cell -> out.putIfAbsent(cell, snapshot(cell)));
        return Map.copyOf(out);
    }

    public void reset() {
        demandByCell.clear();
        supplyByCell.clear();
        driverCell.clear();
    }

    private void decrementSupply(String cell) {
        AtomicInteger supply = supplyByCell.get(cell);
        if (supply != null) {
            supply.updateAndGet(v -> Math.max(0, v - 1));
        }
    }
}
