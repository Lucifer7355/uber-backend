package com.uber.backend.matching.model;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.NearbyDriver;
import java.util.List;

public record MatchResult(
        String riderId,
        GeoPoint pickup,
        List<NearbyDriver> candidates,
        NearbyDriver selected
) {
    public boolean matched() {
        return selected != null;
    }
}
