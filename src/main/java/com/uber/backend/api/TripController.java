package com.uber.backend.api;

import com.uber.backend.api.dto.TripRequest;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.trip.model.Trip;
import com.uber.backend.trip.service.TripService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Trip request(@Valid @RequestBody TripRequest request) {
        return tripService.requestTrip(
                request.riderId(),
                new GeoPoint(request.pickupLat(), request.pickupLon()),
                new GeoPoint(request.dropoffLat(), request.dropoffLon()));
    }

    @GetMapping("/{tripId}")
    public Trip get(@PathVariable String tripId) {
        return tripService.getTrip(tripId);
    }

    @PostMapping("/{tripId}/arriving")
    public Trip arriving(@PathVariable String tripId) {
        return tripService.driverArriving(tripId);
    }

    @PostMapping("/{tripId}/start")
    public Trip start(@PathVariable String tripId) {
        return tripService.startTrip(tripId);
    }

    @PostMapping("/{tripId}/complete")
    public Trip complete(@PathVariable String tripId) {
        return tripService.completeTrip(tripId);
    }

    @PostMapping("/{tripId}/cancel")
    public Trip cancel(@PathVariable String tripId) {
        return tripService.cancelTrip(tripId);
    }

    @GetMapping("/{tripId}/status")
    public Map<String, Object> status(@PathVariable String tripId) {
        Trip trip = tripService.getTrip(tripId);
        return Map.of(
                "tripId", trip.tripId(),
                "status", trip.status().name(),
                "driverId", trip.driverId() == null ? "" : trip.driverId(),
                "fare", trip.fare() == null ? 0 : trip.fare().totalFare());
    }
}
