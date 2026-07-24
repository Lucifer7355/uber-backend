package com.uber.backend.pricing.service;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.common.geo.Haversine;
import com.uber.backend.config.UberProperties;
import com.uber.backend.pricing.model.FareEstimate;
import org.springframework.stereotype.Service;

@Service
public class PricingService {

    private static final double CITY_AVG_SPEED_KMH = 25.0;

    private final UberProperties props;
    private final SurgePricingService surgePricingService;

    public PricingService(UberProperties props, SurgePricingService surgePricingService) {
        this.props = props;
        this.surgePricingService = surgePricingService;
    }

    public FareEstimate estimate(GeoPoint pickup, GeoPoint dropoff) {
        double distanceKm = Haversine.distanceKm(pickup, dropoff);
        int durationMinutes = Haversine.estimateDurationMinutes(distanceKm, CITY_AVG_SPEED_KMH);

        UberProperties.Pricing pricing = props.pricing();
        double base = pricing.baseFare();
        double distanceFare = distanceKm * pricing.perKm();
        double timeFare = durationMinutes * pricing.perMinute();
        double subtotal = base + distanceFare + timeFare;

        double surge = surgePricingService.multiplierFor(pickup);
        String cell = surgePricingService.cellFor(pickup);
        double total = Math.max(pricing.minFare(), subtotal * surge);
        total = Math.round(total * 100.0) / 100.0;

        return new FareEstimate(
                Math.round(distanceKm * 1000.0) / 1000.0,
                durationMinutes,
                base,
                Math.round(distanceFare * 100.0) / 100.0,
                Math.round(timeFare * 100.0) / 100.0,
                surge,
                total,
                pricing.currency(),
                cell
        );
    }
}
