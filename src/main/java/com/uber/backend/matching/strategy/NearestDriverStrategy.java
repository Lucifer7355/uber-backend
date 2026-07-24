package com.uber.backend.matching.strategy;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.NearbyDriver;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NearestDriverStrategy implements MatchingStrategy {

    @Override
    public Optional<NearbyDriver> select(GeoPoint pickup, List<NearbyDriver> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().min(Comparator.comparingDouble(NearbyDriver::distanceKm));
    }
}
