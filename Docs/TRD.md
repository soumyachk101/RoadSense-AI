# ⚙️ Technical Requirements Document (TRD)
## RoadSense AI — System Architecture & Engineering Spec

---

## 1. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      CLIENT LAYER                               │
│   ┌──────────────────┐          ┌──────────────────────────┐   │
│   │  React Native    │          │  Next.js 14 Web App      │   │
│   │  Mobile App      │          │  (Civic Dashboard +      │   │
│   │  (Android)       │          │   Public Map)            │   │
│   └────────┬─────────┘          └────────────┬─────────────┘   │
└────────────│────────────────────────────────│─────────────────┘
             │ HTTPS/REST                      │ HTTPS/REST
             ▼                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY (FastAPI)                      │
│  /api/v1/trips    /api/v1/events    /api/v1/map   /api/v1/auth  │
└───────────┬─────────────────────────────────────────────────────┘
            │
     ┌──────┴────────────────────────────────┐
     ▼                                       ▼
┌──────────────┐                    ┌────────────────────┐
│  Trip        │                    │  ML Pipeline       │
│  Ingestion   │──── Celery Task ──►│  (Classification   │
│  Service     │                    │   + Clustering)    │
└──────┬───────┘                    └────────┬───────────┘
       │                                     │
       ▼                                     ▼
┌──────────────────────────────────────────────────────────┐
│                    DATA LAYER                            │
│  PostgreSQL (PostGIS)  │  Redis  │  MinIO (sensor logs) │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Tech Stack

### 2.1 Mobile App (Sensor Collection)
| Component | Technology |
|-----------|-----------|
| Framework | React Native (Expo) |
| Sensors | `expo-sensors` (Accelerometer, Gyroscope, Magnetometer) |
| Location | `expo-location` (GPS, 1 Hz update rate) |
| Background | `expo-task-manager` + `expo-background-fetch` |
| Local Storage | SQLite via `expo-sqlite` |
| Maps | `react-native-maps` |
| Audio Alerts | `expo-av` (TTS + custom audio) |
| Haptics | `expo-haptics` |
| State | Zustand |
| API Client | Axios + React Query |

### 2.2 Backend (FastAPI)
| Component | Technology |
|-----------|-----------|
| Framework | FastAPI (Python 3.11+) |
| ASGI Server | Uvicorn + Gunicorn |
| Task Queue | Celery + Redis broker |
| Auth | JWT (python-jose) + bcrypt |
| ORM | SQLAlchemy 2.0 + Alembic |
| Validation | Pydantic v2 |
| Geo Operations | GeoPandas + Shapely |
| ML | scikit-learn (J48 = DecisionTreeClassifier) |
| Clustering | scikit-learn KMedoids (sklearn_extra) |
| File Storage | MinIO (S3-compatible) |

### 2.3 Web Dashboard (Next.js)
| Component | Technology |
|-----------|-----------|
| Framework | Next.js 14 (App Router) |
| Language | TypeScript |
| UI Components | shadcn/ui + Tailwind CSS |
| Maps | Mapbox GL JS / Leaflet.js |
| Charts | Recharts |
| State | Zustand + React Query |
| Auth | NextAuth.js |

### 2.4 Infrastructure
| Component | Technology |
|-----------|-----------|
| Database | PostgreSQL 16 + PostGIS extension |
| Cache / Broker | Redis 7 |
| Object Storage | MinIO |
| Containerization | Docker + Docker Compose |
| Reverse Proxy | Nginx |
| Deployment | Railway / Render (MVP) → AWS EC2 (prod) |

---

## 3. Database Schema

### 3.1 Core Tables (PostgreSQL + PostGIS)

```sql
-- Users (commuters + civic authority)
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR(15) UNIQUE NOT NULL,
    name        VARCHAR(100),
    role        VARCHAR(20) DEFAULT 'commuter', -- commuter | civic | admin
    vehicle_type VARCHAR(20),                    -- two_wheeler | three_wheeler | four_wheeler
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Trips (one sensing session = one trip)
CREATE TABLE trips (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),
    vehicle_type    VARCHAR(20) NOT NULL,
    phone_placement VARCHAR(20) NOT NULL,       -- pocket | mounter | dashboard
    phone_model     VARCHAR(100),
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ,
    distance_km     FLOAT,
    road_type       VARCHAR(20),                -- smooth | rough
    status          VARCHAR(20) DEFAULT 'pending', -- pending | processed | failed
    raw_data_path   TEXT,                       -- MinIO path to sensor log
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Raw PoC Candidates (Phase I output from phone)
CREATE TABLE poc_candidates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id         UUID REFERENCES trips(id),
    detected_at     TIMESTAMPTZ NOT NULL,
    location        GEOGRAPHY(POINT, 4326) NOT NULL,
    z_value         FLOAT NOT NULL,            -- Zt: Z-axis acceleration
    z_next          FLOAT,                     -- Z_next
    z_prev          FLOAT,                     -- Z_prev
    tp              FLOAT,                     -- Time to next PoC
    speed_kmh       FLOAT NOT NULL,
    threshold_used  FLOAT NOT NULL,
    raw_event_type  VARCHAR(20),               -- initial guess: speed_breaker | pothole
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Classified Events (Phase II output from server ML)
CREATE TABLE road_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id         UUID REFERENCES trips(id),
    poc_candidate_id UUID REFERENCES poc_candidates(id),
    event_type      VARCHAR(20) NOT NULL,      -- speed_breaker | pothole | broken_patch
    location        GEOGRAPHY(POINT, 4326) NOT NULL,
    severity        VARCHAR(20) DEFAULT 'medium', -- low | medium | high | critical
    estimated_length_m FLOAT,                 -- for broken patches
    detected_at     TIMESTAMPTZ NOT NULL,
    speed_kmh       FLOAT,
    vehicle_type    VARCHAR(20),
    confidence_score FLOAT DEFAULT 0.0,       -- 0.0 - 1.0
    is_confirmed    BOOLEAN DEFAULT FALSE,     -- confirmed after clustering
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Confirmed Events (Phase III: clustered from multiple trails)
CREATE TABLE confirmed_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type      VARCHAR(20) NOT NULL,
    location        GEOGRAPHY(POINT, 4326) NOT NULL,
    severity        VARCHAR(20) NOT NULL,
    estimated_length_m FLOAT,
    trail_count     INT NOT NULL,              -- how many trips detected this
    confidence_score FLOAT NOT NULL,
    first_seen      TIMESTAMPTZ NOT NULL,
    last_seen       TIMESTAMPTZ NOT NULL,
    is_active       BOOLEAN DEFAULT TRUE,
    resolved_at     TIMESTAMPTZ,               -- if civic authority marks fixed
    cluster_radius_m FLOAT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Event Reports (many raw events → one confirmed event)
CREATE TABLE event_cluster_members (
    confirmed_event_id UUID REFERENCES confirmed_events(id),
    road_event_id      UUID REFERENCES road_events(id),
    PRIMARY KEY (confirmed_event_id, road_event_id)
);

-- Sensor Readings (raw 150Hz data — stored in MinIO, metadata here)
CREATE TABLE sensor_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id         UUID REFERENCES trips(id),
    sample_rate_hz  INT DEFAULT 150,
    duration_sec    FLOAT,
    total_samples   BIGINT,
    file_path       TEXT NOT NULL,             -- MinIO path
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Road Quality Index (pre-computed per road segment)
CREATE TABLE road_quality_index (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    road_segment    GEOGRAPHY(LINESTRING, 4326),
    rqi_score       FLOAT NOT NULL,            -- 0 (terrible) - 100 (perfect)
    event_density   FLOAT,                     -- events per km
    dominant_event  VARCHAR(20),
    computed_at     TIMESTAMPTZ DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_confirmed_events_location ON confirmed_events USING GIST(location);
CREATE INDEX idx_road_events_location ON road_events USING GIST(location);
CREATE INDEX idx_poc_candidates_trip ON poc_candidates(trip_id);
CREATE INDEX idx_road_events_trip ON road_events(trip_id);
CREATE INDEX idx_trips_user ON trips(user_id);
CREATE INDEX idx_confirmed_events_active ON confirmed_events(is_active);
```

---

## 4. API Specification

### 4.1 Auth Routes
```
POST   /api/v1/auth/register          Register with phone number
POST   /api/v1/auth/login             Phone + OTP login
POST   /api/v1/auth/refresh           Refresh JWT token
```

### 4.2 Trip Routes
```
POST   /api/v1/trips/start            Start a new sensing trip
POST   /api/v1/trips/{id}/end         End trip + upload sensor data
GET    /api/v1/trips/history          User's trip history
GET    /api/v1/trips/{id}             Trip detail + events detected
```

### 4.3 PoC Candidate Upload (Phone → Server)
```
POST   /api/v1/trips/{id}/poc         Upload batch of PoC candidates
                                      Body: { candidates: PoCandidate[] }
```

**PoCandidate schema:**
```json
{
  "detected_at": "2024-01-15T10:30:00Z",
  "lat": 23.548,
  "lng": 87.302,
  "z_value": 18.4,
  "z_next": -12.1,
  "z_prev": 3.2,
  "tp": 1.8,
  "speed_kmh": 28.5,
  "threshold_used": 14.2,
  "raw_event_type": "speed_breaker"
}
```

### 4.4 Map / Events Routes
```
GET    /api/v1/events                 All confirmed events (bbox filter)
       ?bbox=lat1,lng1,lat2,lng2
       &type=pothole|speed_breaker|broken_patch
       &severity=low|medium|high|critical
       &since=ISO8601

GET    /api/v1/events/{id}            Single event detail
GET    /api/v1/events/heatmap         Heatmap data (aggregated)
GET    /api/v1/events/nearby          Events near a point
       ?lat=23.5&lng=87.3&radius_m=200
```

### 4.5 Civic Dashboard Routes (Role: civic | admin)
```
GET    /api/v1/civic/summary          Dashboard summary stats
GET    /api/v1/civic/rqi              Road quality index by area
GET    /api/v1/civic/priority-list    Repair priority ranked list
POST   /api/v1/civic/events/{id}/resolve   Mark event as resolved
GET    /api/v1/civic/export           Export CSV/PDF report
```

---

## 5. ML Pipeline Specification

### 5.1 Phase I — Auto-Orientation (On-Device)

```python
# Euler angle-based reorientation
# Translates device reference frame → vehicle reference frame

def auto_orient(ax, ay, az):
    theta = math.atan2(ay, az)           # pitch angle
    beta  = math.atan2(-ax, math.sqrt(ay**2 + az**2))  # roll angle
    
    ax_v = ax*math.cos(beta) + ay*math.sin(beta)*math.sin(theta) + az*math.cos(theta)*math.sin(beta)
    ay_v = ay*math.cos(theta) - az*math.sin(theta)
    az_v = -ax*math.sin(beta) + ay*math.cos(beta)*math.sin(theta) + az*math.cos(beta)*math.cos(theta)
    
    return ax_v, ay_v, az_v

# Apply low-pass filter to extract Z-axis vertical component
def low_pass_filter(signal, alpha=0.8):
    filtered = [signal[0]]
    for i in range(1, len(signal)):
        filtered.append(alpha * filtered[-1] + (1 - alpha) * signal[i])
    return filtered
```

### 5.2 Phase I — Auto-Tune Threshold (On-Device)

```python
# Initial thresholds per vehicle type (in g-force)
INITIAL_THRESHOLDS = {
    "speed_breaker": {
        "two_wheeler":   {"mounter": 1.8, "pocket": 1.57},
        "three_wheeler": {"mounter": 1.47, "pocket": None},
        "four_wheeler":  {"mounter": 1.08, "pocket": None},
    },
    "pothole": {
        "two_wheeler":   {"mounter": 0.714, "pocket": 0.612},
        "three_wheeler": {"mounter": 0.612, "pocket": None},
        "four_wheeler":  {"mounter": 0.41,  "pocket": None},
    }
}

def compute_dynamic_threshold(T0, speeds, t, L, S, B):
    """
    T0: initial threshold
    speeds: list of speed values up to time t
    L: lower adaptation limit
    S: scaling factor
    B: base point for switching to dynamic mode
    """
    avg_speed = sum(speeds) / len(speeds)
    if avg_speed > B:
        Tt = T0 + (avg_speed - L) * S
    else:
        Tt = T0
    return Tt
```

### 5.3 Phase II — Decision Tree Classifier (Server)

```python
from sklearn.tree import DecisionTreeClassifier
from sklearn.model_selection import cross_val_score
import numpy as np

# Feature vector: [Zt, Z_next, Z_prev, Tp, Sp]
# Label: 0=anomaly, 1=speed_breaker, 2=pothole, 3=broken_patch

def train_classifier(X_train, y_train):
    clf = DecisionTreeClassifier(
        criterion='entropy',       # J48 equivalent in sklearn
        max_depth=None,
        min_samples_split=2,
        class_weight='balanced'
    )
    scores = cross_val_score(clf, X_train, y_train, cv=10, scoring='f1_macro')
    clf.fit(X_train, y_train)
    return clf, scores

def classify_poc(clf, z_value, z_next, z_prev, tp, speed_kmh):
    features = np.array([[z_value, z_next, z_prev, tp, speed_kmh]])
    prediction = clf.predict(features)[0]
    confidence = max(clf.predict_proba(features)[0])
    labels = {0: 'anomaly', 1: 'speed_breaker', 2: 'pothole', 3: 'broken_patch'}
    return labels[prediction], float(confidence)

def detect_broken_patch(events, time_window_sec=3.0, min_speed_kmh=5.0):
    """
    Broken patch = consecutive potholes + speed-breakers
    spaced very close together (Tp <= 3 seconds at low speed)
    """
    broken_groups = []
    i = 0
    while i < len(events):
        group = [events[i]]
        while i + 1 < len(events):
            tp = (events[i+1]['detected_at'] - events[i]['detected_at']).total_seconds()
            if tp <= time_window_sec:
                group.append(events[i+1])
                i += 1
            else:
                break
        if len(group) >= 3:
            broken_groups.append(group)
        i += 1
    return broken_groups
```

### 5.4 Phase III — k-Medoids Clustering (Server)

```python
from sklearn_extra.cluster import KMedoids
from sklearn.metrics import silhouette_score
import numpy as np

def find_optimal_k(coords, k_range=(2, 10)):
    """Silhouette analysis to find best k"""
    best_k = 2
    best_score = -1
    for k in range(k_range[0], min(k_range[1], len(coords))):
        km = KMedoids(n_clusters=k, metric='euclidean', random_state=42)
        labels = km.fit_predict(coords)
        score = silhouette_score(coords, labels)
        if score > best_score:
            best_score = score
            best_k = k
    return best_k

def cluster_events(raw_events, total_trails):
    """
    Cluster geo-tagged events from multiple trails.
    Discard clusters with fewer than ceil(NT/3) + 1 members.
    """
    import math
    min_members = math.ceil(total_trails / 3) + 1
    
    coords = np.array([[e['lat'], e['lng']] for e in raw_events])
    
    if len(coords) < 2:
        return []
    
    optimal_k = find_optimal_k(coords)
    km = KMedoids(n_clusters=optimal_k, metric='euclidean', random_state=42)
    labels = km.fit_predict(coords)
    
    confirmed = []
    for cluster_id in range(optimal_k):
        members = [raw_events[i] for i in range(len(raw_events)) if labels[i] == cluster_id]
        if len(members) >= min_members:
            center = km.cluster_centers_[cluster_id]
            confirmed.append({
                'lat': center[0],
                'lng': center[1],
                'trail_count': len(members),
                'confidence': len(members) / total_trails,
                'members': members
            })
    return confirmed
```

---

## 6. Celery Task Pipeline

```python
# tasks/pipeline.py

@celery_app.task(bind=True, max_retries=3)
def process_trip(self, trip_id: str):
    """
    Triggered when a trip ends and PoC candidates are uploaded.
    Steps:
    1. Load PoC candidates from DB
    2. Run Decision Tree classifier (Phase II)
    3. Save classified road_events
    4. Trigger clustering task
    """
    try:
        candidates = db.query(PocCandidate).filter_by(trip_id=trip_id).all()
        clf = load_model('decision_tree_v1.pkl')
        
        for cand in candidates:
            event_type, confidence = classify_poc(
                clf, cand.z_value, cand.z_next, cand.z_prev,
                cand.tp, cand.speed_kmh
            )
            if event_type != 'anomaly':
                save_road_event(trip_id, cand, event_type, confidence)
        
        trigger_clustering.delay(area_bbox=get_trip_bbox(trip_id))
    except Exception as exc:
        raise self.retry(exc=exc, countdown=60)


@celery_app.task
def trigger_clustering(area_bbox):
    """
    Re-cluster events in a geographic area after new trip processed.
    Runs every time new events are added in an area.
    """
    raw_events = db.query(RoadEvent).filter(
        RoadEvent.location.ST_Within(area_bbox),
        RoadEvent.is_confirmed == False
    ).all()
    
    total_trails = count_trails_in_area(area_bbox)
    confirmed = cluster_events(raw_events, total_trails)
    
    for event in confirmed:
        upsert_confirmed_event(event)


@celery_app.task
def compute_rqi(road_segment_id):
    """Compute Road Quality Index score for a segment"""
    pass  # Implementation in v1.1
```

---

## 7. Sensor Data Collection Spec (Mobile)

### 7.1 Sampling Configuration
```json
{
  "accelerometer_hz": 150,
  "gyroscope_hz": 150,
  "gps_interval_ms": 1000,
  "magnetometer_hz": 50,
  "batch_upload_on_trip_end": true,
  "local_buffer_max_mb": 50,
  "min_speed_kmh_for_detection": 5
}
```

### 7.2 Vehicle Type Auto-Detection
- User selects once during onboarding
- Option: Auto-detect from phone vibration signature (v2.0)

### 7.3 Phone Placement Options
- **Mounter** (handlebar / windshield / dashboard)
- **Pocket** (shirt / pant pocket)
- App prompts user to select placement at trip start

---

## 8. Performance Requirements

| Metric | Requirement |
|--------|-------------|
| API response time (p95) | < 200ms |
| Trip processing latency | < 5 minutes |
| Map update latency | < 5 minutes post-trip |
| Concurrent users supported | 500 (MVP) |
| Sensor data upload size (avg trip) | < 10MB |
| Mobile app RAM usage | < 150MB |
| Mobile battery drain | < 5%/hour |
| DB storage per 1000 trips | ~2GB |

---

## 9. Security

- JWT tokens with 24h expiry (refresh token: 30 days)
- Phone OTP-based auth (no passwords)
- Rate limiting: 100 req/min per IP (FastAPI middleware)
- Sensor data encrypted in transit (HTTPS/TLS 1.3)
- PostGIS queries parameterized (SQLAlchemy ORM, no raw SQL)
- Civic dashboard: IP allowlisting + 2FA (v1.1)
- GDPR-compliant: user can delete all their trip data

---

## 10. Project Structure

```
roadsense/
├── mobile/                     # React Native (Expo)
│   ├── app/
│   │   ├── (tabs)/
│   │   │   ├── index.tsx       # Map screen
│   │   │   ├── trip.tsx        # Active trip screen
│   │   │   └── history.tsx     # Trip history
│   │   ├── onboarding/
│   │   └── _layout.tsx
│   ├── components/
│   │   ├── SensorEngine.tsx    # Core sensor loop
│   │   ├── AutoOrient.ts       # Euler angle reorientation
│   │   ├── ThresholdEngine.ts  # Dynamic threshold
│   │   ├── AlertSystem.tsx     # Audio + haptic alerts
│   │   └── EventMarker.tsx
│   ├── stores/
│   │   ├── tripStore.ts
│   │   └── settingsStore.ts
│   └── services/
│       ├── api.ts
│       └── sensorService.ts
│
├── backend/                    # FastAPI
│   ├── app/
│   │   ├── main.py
│   │   ├── api/
│   │   │   ├── auth.py
│   │   │   ├── trips.py
│   │   │   ├── events.py
│   │   │   └── civic.py
│   │   ├── models/             # SQLAlchemy models
│   │   ├── schemas/            # Pydantic schemas
│   │   ├── services/
│   │   │   ├── classifier.py   # Decision Tree ML
│   │   │   ├── clustering.py   # k-Medoids
│   │   │   └── geo.py          # PostGIS helpers
│   │   └── tasks/
│   │       └── pipeline.py     # Celery tasks
│   ├── ml/
│   │   ├── train.py            # Model training script
│   │   ├── models/             # Saved .pkl models
│   │   └── data/               # Training datasets
│   ├── alembic/                # DB migrations
│   └── tests/
│
├── web/                        # Next.js 14 Dashboard
│   ├── app/
│   │   ├── dashboard/          # Civic dashboard
│   │   ├── map/                # Public road map
│   │   └── api/                # Next.js API routes (auth only)
│   ├── components/
│   │   ├── RoadMap.tsx
│   │   ├── HeatmapLayer.tsx
│   │   ├── EventTable.tsx
│   │   └── RQIChart.tsx
│   └── lib/
│
├── docker-compose.yml
├── nginx.conf
└── README.md
```

---

*TRD Version: 1.0 | Project: RoadSense AI | Stack Owner: Soumya CHK*
