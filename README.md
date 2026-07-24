# Uber Backend ★★★★★

Ride-hailing **backend** (no frontend) demonstrating distributed systems, realtime, caching, concurrency, and system design.

| Capability | Implementation |
|---|---|
| Driver Matching | GEO radius + nearest strategy + atomic claim |
| Geo Hashing | Custom base32 encoder for surge cells |
| Redis GEO | `GEOADD` / radius query (prod profile) |
| Kafka | Location, trip, notification topics |
| Location Updates | Drivers ping → spatial index + events |
| Trip Service | State machine lifecycle |
| Pricing Service | Distance + time + min fare |
| Surge Pricing | Demand/supply per geohash cell |
| Notification | Kafka/in-memory bus → WebSocket push |
| WebSockets | `/ws/trips/{userId}` realtime fan-out |

---

## Architecture

```
                    ┌─────────────┐
   Driver pings ──► │ Location    │──► Redis GEO (or in-memory)
                    │ Service     │──► Kafka: location.updates
                    └──────┬──────┘
                           │ nearby candidates
                    ┌──────▼──────┐
   Rider request ─► │ Matching    │──► claim driver (CAS)
                    │ + Trip svc  │──► Trip state machine
                    └──────┬──────┘
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
     Pricing/Surge    Kafka trip.events   notifications
     (geohash cells)                      │
                                          ▼
                                   WebSocket push
```

**Modular monolith** with clear service boundaries — each package can become a microservice later.

---

## Quick start (no Docker)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local,demo
```

Runs with in-memory GEO + in-memory event bus and prints an interview-style walkthrough.

```bash
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Server: `http://localhost:8080`

---

## Production-style infra (Redis + Kafka)

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=default
```

(Use a profile that is **not** `local` so Redis/Kafka beans activate.)

```bash
# PowerShell
$env:SPRING_PROFILES_ACTIVE=""
mvn spring-boot:run
```

Or explicitly:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=
```

---

## API cheat sheet

### 1. Driver location update

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

### 2. Nearby drivers (GEO)

```http
GET /api/v1/locations/nearby?lat=12.9352&lon=77.6245&radiusKm=5&limit=10
```

### 3. Fare estimate + surge

```http
GET /api/v1/pricing/estimate?pickupLat=12.9352&pickupLon=77.6245&dropoffLat=12.9716&dropoffLon=77.5946
GET /api/v1/pricing/surge?lat=12.9352&lon=77.6245
```

### 4. Request trip → lifecycle

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

### 5. WebSocket

Connect: `ws://localhost:8080/ws/trips/R1`  
Receives JSON notifications when trip events fire.

---

## Package map

```
com.uber.backend
├── api/                 REST controllers
├── common/geo           GeoPoint, GeoHash, Haversine
├── location/            Redis GEO + location stream
├── matching/            Strategy + concurrent claim
├── pricing/             Fare + surge
├── trip/                State machine + repository
├── notification/        Kafka consumer + WebSocket
├── event/               EventPublisher (Kafka | in-memory)
└── demo/                RideFlowDemo (profile=demo)
```

---

## Design highlights (interview talking points)

1. **Redis GEO** — O(log N) nearby driver queries instead of scanning every driver.
2. **Geohash surge cells** — marketplace control loop without global locks.
3. **Kafka** — decouples location ingestion, trip ledger, and notifications.
4. **Atomic driver claim** — `ConcurrentHashMap.putIfAbsent` prevents double-booking under concurrency.
5. **Trip state machine** — illegal transitions fail fast (`REQUESTED ↛ COMPLETED`).
6. **WebSockets** — push trip updates without polling.
7. **Ports & adapters** — `DriverLocationRepository` / `EventPublisher` swap Redis+Kafka ↔ in-memory for tests.

---

## Resume one-liner

> Built an Uber-like ride backend in Java 21 / Spring Boot with Redis GEO driver matching, geohash surge pricing, Kafka event pipelines, trip state machines, and WebSocket notifications — covering distributed systems, realtime, caching, and concurrency.
