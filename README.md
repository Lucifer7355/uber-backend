# Uber Backend

Backend for a ride-hailing system (Java 21, Spring Boot). No frontend.

Things this covers:
- driver matching with geo queries
- geohashing for surge zones
- Redis GEO for live driver locations
- Kafka for location / trip / notification events
- trip lifecycle + pricing + surge
- WebSocket push for trip updates

## How it fits together

```
Driver location pings
        │
        ▼
 Location Service ──► Redis GEO (or in-memory on local)
        │             Kafka: location.updates
        ▼
 Matching + Trip ──► claim nearest driver, trip state machine
        │
        ├── Pricing / Surge (geohash cells)
        ├── Kafka: trip.events
        └── notifications ──► WebSocket (/ws/trips/{userId})
```

`local` profile uses in-memory stores so you can run without Docker. Drop `local` and bring up `docker compose` when you want real Redis + Kafka.

## Run

```bash
# demo walkthrough (no Docker)
mvn spring-boot:run -Dspring-boot.run.profiles=local,demo

# API only
mvn spring-boot:run

# tests
mvn test
```

App: http://localhost:8080

### Redis + Kafka

```bash
docker compose up -d
```

Then start the app **without** the `local` profile:

```bash
# PowerShell
$env:SPRING_PROFILES_ACTIVE=""
mvn spring-boot:run
```

## APIs

**Update driver location**
```http
POST /api/v1/locations
Content-Type: application/json

{
  "driverId": "D1",
  "lat": 12.9360,
  "lon": 77.6250,
  "availability": "AVAILABLE"
}
```

**Nearby drivers**
```http
GET /api/v1/locations/nearby?lat=12.9352&lon=77.6245&radiusKm=5&limit=10
```

**Fare / surge**
```http
GET /api/v1/pricing/estimate?pickupLat=12.9352&pickupLon=77.6245&dropoffLat=12.9716&dropoffLon=77.5946
GET /api/v1/pricing/surge?lat=12.9352&lon=77.6245
```

**Trip**
```http
POST /api/v1/trips
{
  "riderId": "R1",
  "pickupLat": 12.9352,
  "pickupLon": 77.6245,
  "dropoffLat": 12.9716,
  "dropoffLon": 77.5946
}

POST /api/v1/trips/{tripId}/arriving
POST /api/v1/trips/{tripId}/start
POST /api/v1/trips/{tripId}/complete
POST /api/v1/trips/{tripId}/cancel
```

**WebSocket:** `ws://localhost:8080/ws/trips/R1`

## Package layout

```
com.uber.backend
├── api/
├── common/geo          GeoPoint, GeoHash, Haversine
├── location/           Redis GEO + location updates
├── matching/           nearest-driver matching + claim
├── pricing/            fare + surge
├── trip/               state machine
├── notification/       Kafka/in-memory → WebSocket
├── event/              EventPublisher (Kafka or in-memory)
└── demo/               RideFlowDemo (profile=demo)
```

## Notes

- Nearby search uses Redis GEO in prod; Haversine over a map locally.
- Surge is per geohash cell based on demand vs supply.
- Driver claim uses `putIfAbsent` so two riders don't get the same driver.
- Trip status changes are validated (you can't jump `MATCHED` → `COMPLETED`).
