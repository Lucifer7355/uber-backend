package com.uber.backend.pricing.model;

public record FareEstimate(
        double distanceKm,
        int durationMinutes,
        double baseFare,
        double distanceFare,
        double timeFare,
        double surgeMultiplier,
        double totalFare,
        String currency,
        String surgeGeohash
) {
}
