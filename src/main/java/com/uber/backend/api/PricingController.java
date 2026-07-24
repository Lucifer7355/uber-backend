package com.uber.backend.api;

import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.pricing.model.FareEstimate;
import com.uber.backend.pricing.model.SurgeSnapshot;
import com.uber.backend.pricing.service.PricingService;
import com.uber.backend.pricing.service.SurgePricingService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
public class PricingController {

    private final PricingService pricingService;
    private final SurgePricingService surgePricingService;

    public PricingController(PricingService pricingService, SurgePricingService surgePricingService) {
        this.pricingService = pricingService;
        this.surgePricingService = surgePricingService;
    }

    @GetMapping("/estimate")
    public FareEstimate estimate(
            @RequestParam double pickupLat,
            @RequestParam double pickupLon,
            @RequestParam double dropoffLat,
            @RequestParam double dropoffLon) {
        return pricingService.estimate(
                new GeoPoint(pickupLat, pickupLon),
                new GeoPoint(dropoffLat, dropoffLon));
    }

    @GetMapping("/surge")
    public SurgeSnapshot surge(@RequestParam double lat, @RequestParam double lon) {
        return surgePricingService.snapshotAt(new GeoPoint(lat, lon));
    }

    @GetMapping("/surge/all")
    public Map<String, SurgeSnapshot> allSurge() {
        return surgePricingService.allSnapshots();
    }
}
