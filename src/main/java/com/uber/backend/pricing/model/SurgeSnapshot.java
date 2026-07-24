package com.uber.backend.pricing.model;

public record SurgeSnapshot(
        String geohash,
        int demand,
        int supply,
        double multiplier
) {
}
