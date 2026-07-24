package com.uber.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "uber")
public record UberProperties(
        Matching matching,
        Pricing pricing,
        Surge surge,
        Location location,
        Kafka kafka
) {
    public record Matching(double searchRadiusKm, int maxCandidates, int offerTimeoutSeconds) {
    }

    public record Pricing(double baseFare, double perKm, double perMinute, double minFare, String currency) {
    }

    public record Surge(int geohashPrecision, int demandThreshold, int supplyThreshold, double maxMultiplier) {
    }

    public record Location(String redisGeoKey, long staleAfterSeconds) {
    }

    public record Kafka(Topics topics) {
        public record Topics(String locationUpdates, String tripEvents, String notifications) {
        }
    }
}
