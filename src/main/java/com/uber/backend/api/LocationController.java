package com.uber.backend.api;

import com.uber.backend.api.dto.LocationUpdateRequest;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.model.DriverLocation;
import com.uber.backend.location.model.NearbyDriver;
import com.uber.backend.location.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DriverLocation update(@Valid @RequestBody LocationUpdateRequest request) {
        return locationService.updateLocation(
                request.driverId(),
                new GeoPoint(request.lat(), request.lon()),
                DriverAvailability.valueOf(request.availability().toUpperCase()));
    }

    @GetMapping("/nearby")
    public List<NearbyDriver> nearby(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(defaultValue = "10") int limit) {
        return locationService.findNearbyDrivers(new GeoPoint(lat, lon), radiusKm, limit);
    }

    @GetMapping("/{driverId}")
    public DriverLocation get(@PathVariable String driverId) {
        return locationService.getDriver(driverId)
                .orElseThrow(() -> new com.uber.backend.common.exception.NotFoundException(
                        "driver not found: " + driverId));
    }

    @DeleteMapping("/{driverId}")
    public Map<String, String> offline(@PathVariable String driverId) {
        locationService.goOffline(driverId);
        return Map.of("driverId", driverId, "status", "OFFLINE");
    }
}
