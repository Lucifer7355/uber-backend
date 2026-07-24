package com.uber.backend.demo;

import com.uber.backend.common.geo.GeoHash;
import com.uber.backend.common.geo.GeoPoint;
import com.uber.backend.location.model.DriverAvailability;
import com.uber.backend.location.service.LocationService;
import com.uber.backend.notification.service.NotificationService;
import com.uber.backend.pricing.model.FareEstimate;
import com.uber.backend.pricing.model.SurgeSnapshot;
import com.uber.backend.pricing.service.PricingService;
import com.uber.backend.pricing.service.SurgePricingService;
import com.uber.backend.trip.model.Trip;
import com.uber.backend.trip.service.TripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Interview-grade walkthrough of the Uber backend flows.
 * Activate with: --spring.profiles.active=local,demo
 */
@Component
@Profile("demo")
public class RideFlowDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RideFlowDemo.class);

    // Koramangala, Bangalore
    private static final GeoPoint PICKUP = new GeoPoint(12.9352, 77.6245);
    private static final GeoPoint DROPOFF = new GeoPoint(12.9716, 77.5946);
    private static final GeoPoint DRIVER_NEAR = new GeoPoint(12.9360, 77.6250);
    private static final GeoPoint DRIVER_FAR = new GeoPoint(12.9800, 77.5800);

    private final LocationService locationService;
    private final TripService tripService;
    private final PricingService pricingService;
    private final SurgePricingService surgePricingService;
    private final NotificationService notificationService;

    public RideFlowDemo(
            LocationService locationService,
            TripService tripService,
            PricingService pricingService,
            SurgePricingService surgePricingService,
            NotificationService notificationService) {
        this.locationService = locationService;
        this.tripService = tripService;
        this.pricingService = pricingService;
        this.surgePricingService = surgePricingService;
        this.notificationService = notificationService;
    }

    @Override
    public void run(String... args) {
        banner("UBER BACKEND DEMO — distributed ride platform walkthrough");
        scenarioGeohash();
        scenarioLocationAndRedisGeoSemantics();
        scenarioPricingAndSurge();
        scenarioHappyPathTrip();
        scenarioNoDrivers();
        scenarioIllegalTransition();
        banner("DEMO COMPLETE — see README for Redis/Kafka/WebSocket production mode");
    }

    private void scenarioGeohash() {
        section("SCENARIO", "Geohashing for surge cells");
        String hash = GeoHash.encode(PICKUP, 6);
        GeoPoint center = GeoHash.decodeCenter(hash);
        action("Encode pickup " + PICKUP + " at precision 6");
        result("geohash=" + hash + " cellCenter=" + center);
        why("Precision-6 cells (~1km) bucket demand/supply for surge without scanning the whole city");
    }

    private void scenarioLocationAndRedisGeoSemantics() {
        section("SCENARIO", "Driver location updates (Redis GEO semantics)");
        action("Driver D1 pings near pickup as AVAILABLE");
        locationService.updateLocation("D1", DRIVER_NEAR, DriverAvailability.AVAILABLE);
        action("Driver D2 pings far away as AVAILABLE");
        locationService.updateLocation("D2", DRIVER_FAR, DriverAvailability.AVAILABLE);
        var nearby = locationService.findNearbyDrivers(PICKUP, 5.0, 10);
        result("nearby drivers=" + nearby);
        why("GEO radius query returns candidates sorted by distance — matching never scans all drivers");
    }

    private void scenarioPricingAndSurge() {
        section("SCENARIO", "Pricing + surge");
        FareEstimate base = pricingService.estimate(PICKUP, DROPOFF);
        action("Estimate fare with current surge");
        result("fare=" + base.totalFare() + " " + base.currency()
                + " distanceKm=" + base.distanceKm()
                + " surge=" + base.surgeMultiplier()
                + " cell=" + base.surgeGeohash());

        action("Simulate demand spike: 5 ride requests in same geohash cell");
        for (int i = 0; i < 5; i++) {
            surgePricingService.recordRideRequest(PICKUP);
        }
        SurgeSnapshot snap = surgePricingService.snapshotAt(PICKUP);
        FareEstimate surged = pricingService.estimate(PICKUP, DROPOFF);
        result("surgeSnapshot=" + snap + " fareAfterSurge=" + surged.totalFare());
        why("When demand >> supply in a geohash cell, multiplier rises (capped) — classic marketplace control loop");
        for (int i = 0; i < 5; i++) {
            surgePricingService.releaseRideRequest(PICKUP);
        }
    }

    private void scenarioHappyPathTrip() {
        section("SCENARIO", "Happy path: request → match → arrive → start → complete");
        action("Rider R1 requests trip Koramangala → MG Road");
        Trip trip = tripService.requestTrip("R1", PICKUP, DROPOFF);
        result("tripId=" + trip.tripId() + " status=" + trip.status() + " driver=" + trip.driverId()
                + " fare=" + (trip.fare() == null ? 0 : trip.fare().totalFare()));
        why("Matching claims nearest available driver atomically (putIfAbsent) to prevent double-booking");

        action("Driver marks arriving");
        trip = tripService.driverArriving(trip.tripId());
        result("status=" + trip.status());

        action("Trip starts");
        trip = tripService.startTrip(trip.tripId());
        result("status=" + trip.status());

        action("Trip completes — driver released back to AVAILABLE");
        trip = tripService.completeTrip(trip.tripId());
        result("status=" + trip.status());

        action("Notifications published via event bus (Kafka topic in prod)");
        result("recentNotifications=" + notificationService.recent(5));
        why("Trip lifecycle emits domain events; NotificationService pushes over WebSocket /ws/trips/{userId}");
    }

    private void scenarioNoDrivers() {
        section("SCENARIO", "Edge case: no drivers in radius");
        locationService.goOffline("D1");
        locationService.goOffline("D2");
        action("Rider R2 requests while all drivers offline");
        Trip trip = tripService.requestTrip("R2", PICKUP, DROPOFF);
        result("status=" + trip.status() + " (expected NO_DRIVERS)");
        why("Fail soft with explicit status — client can retry or expand search radius");
    }

    private void scenarioIllegalTransition() {
        section("SCENARIO", "State machine rejects illegal transitions");
        locationService.updateLocation("D3", DRIVER_NEAR, DriverAvailability.AVAILABLE);
        Trip trip = tripService.requestTrip("R3", PICKUP, DROPOFF);
        action("Attempt COMPLETE while status=" + trip.status() + " (skip start)");
        try {
            tripService.completeTrip(trip.tripId());
            result("UNEXPECTED: complete succeeded");
        } catch (Exception ex) {
            result("rejected: " + ex.getMessage());
            why("Trip state machine encodes legal edges only — prevents corrupted trip ledger");
        }
        tripService.cancelTrip(trip.tripId());
    }

    private static void banner(String msg) {
        log.info("");
        log.info("════════════════════════════════════════════════════════════");
        log.info(" {}", msg);
        log.info("════════════════════════════════════════════════════════════");
    }

    private static void section(String kind, String title) {
        log.info("");
        log.info("[{}] {}", kind, title);
    }

    private static void action(String msg) {
        log.info("  [ACTION] {}", msg);
    }

    private static void result(String msg) {
        log.info("  [RESULT] {}", msg);
    }

    private static void why(String msg) {
        log.info("  [WHY]    {}", msg);
    }
}
