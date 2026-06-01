# 🤖 AI Instructions for Claude Code
## RoadSense AI — Developer Context & Build Guide

> This file is the primary context document for Claude Code.
> Read this FIRST before touching any file in this project.

---

## 1. Project Identity

**Name:** RoadSense AI
**Type:** Crowdsourced Road Intelligence Platform
**Scientific Basis:** RoadSurP system (Alam et al., Pervasive and Mobile Computing, 2020)
**Developer:** Soumya CHK (`chksoumya.in` · `github.com/soumyachk101`)
**Goal:** Detect potholes, speed-breakers, and broken road patches from smartphone sensors using crowdsourced data — independent of vehicle type, phone model, or placement.

---

## 2. Tech Stack (Do Not Deviate Without Asking)

```
Mobile:   React Native (Expo SDK 51+) — Android only for v1.0
Backend:  FastAPI (Python 3.11+) + Celery + Redis
Database: PostgreSQL 16 + PostGIS extension
Web:      Next.js 14 (App Router) + TypeScript + Tailwind CSS + shadcn/ui
ML:       scikit-learn (DecisionTreeClassifier, J48 equivalent)
Clustering: sklearn_extra (KMedoids)
Storage:  MinIO (S3-compatible object storage)
Queue:    Redis (Celery broker)
Maps:     Mapbox GL JS (web) + react-native-maps (mobile)
ORM:      SQLAlchemy 2.0 + Alembic
Auth:     JWT (python-jose) — phone OTP based
Deploy:   Docker + Docker Compose → Railway (MVP)
```

---

## 3. Repository Structure

```
roadsense/
├── mobile/          → React Native (Expo) app — sensor collection + map + alerts
├── backend/         → FastAPI server — REST API + Celery ML pipeline
│   ├── app/
│   │   ├── main.py
│   │   ├── api/     → route handlers (auth, trips, events, civic)
│   │   ├── models/  → SQLAlchemy ORM models
│   │   ├── schemas/ → Pydantic v2 request/response schemas
│   │   ├── services/→ business logic (classifier, clustering, geo)
│   │   └── tasks/   → Celery async tasks (pipeline.py)
│   └── ml/          → training scripts + saved .pkl models
├── web/             → Next.js 14 civic dashboard + public map
└── docker-compose.yml
```

---

## 4. Core Algorithms — Source of Truth

These algorithms come directly from the research paper. **Implement exactly as specified.**

### 4.1 Auto-Orientation (Euler Angles)

The phone's device reference frame must be translated to the vehicle reference frame using Euler angles. This removes dependency on how the phone is held or mounted.

```python
# File: backend/app/services/orientation.py
import math

def auto_orient(ax: float, ay: float, az: float) -> tuple[float, float, float]:
    """
    Translates device accelerometer axes to vehicle reference frame.
    Z-axis of output = vertical movement (road surface detection axis).
    
    Args:
        ax, ay, az: Raw accelerometer values (device frame)
    Returns:
        ax_v, ay_v, az_v: Reoriented values (vehicle frame)
    """
    theta = math.atan2(ay, az)                             # pitch
    beta  = math.atan2(-ax, math.sqrt(ay**2 + az**2))     # roll
    
    ax_v = ax*math.cos(beta) + ay*math.sin(beta)*math.sin(theta) + az*math.cos(theta)*math.sin(beta)
    ay_v = ay*math.cos(theta) - az*math.sin(theta)
    az_v = -ax*math.sin(beta) + ay*math.cos(beta)*math.sin(theta) + az*math.cos(beta)*math.cos(theta)
    
    return ax_v, ay_v, az_v

def low_pass_filter(signal: list[float], alpha: float = 0.8) -> list[float]:
    """Extract vertical component from Z-axis noise."""
    filtered = [signal[0]]
    for i in range(1, len(signal)):
        filtered.append(alpha * filtered[-1] + (1 - alpha) * signal[i])
    return filtered
```

**IMPORTANT:** Run auto_orient on every accelerometer sample before doing anything else. The Z-axis of the output is the ONLY axis used for road event detection.

---

### 4.2 Dynamic Threshold (Auto-Tune)

Do NOT use static thresholds. The threshold must adapt to vehicle speed in real time.

```python
# File: backend/app/services/threshold.py

# Initial thresholds (in g-force) — derived from empirical experiments
# Source: Table 2 of the paper
INITIAL_THRESHOLDS = {
    "speed_breaker": {
        "two_wheeler":   {"mounter": 1.8,  "pocket": 1.57},
        "three_wheeler": {"mounter": 1.47, "pocket": None},   # pocket not applicable
        "four_wheeler":  {"mounter": 1.08, "pocket": None},
    },
    "pothole": {
        "two_wheeler":   {"mounter": 0.714, "pocket": 0.612},
        "three_wheeler": {"mounter": 0.612, "pocket": None},
        "four_wheeler":  {"mounter": 0.41,  "pocket": None},
    }
}

# Tuning constants (can be adjusted per city/road type)
THRESHOLD_CONFIG = {
    "B": 20.0,   # Base point: min speed for dynamic mode (km/h)
    "L": 20.0,   # Lower adaptation limit (km/h)
    "S": 0.3,    # Scaling factor: threshold increase per km/h above L
}

def compute_threshold(
    T0: float,
    speed_history: list[float],
    cfg: dict = THRESHOLD_CONFIG
) -> float:
    """
    Computes dynamic threshold Tt based on vehicle speed history.
    
    For speed-breakers: threshold INCREASES with speed
    For potholes:       threshold DECREASES with speed (use negative S)
    """
    if not speed_history:
        return T0
    avg_speed = sum(speed_history) / len(speed_history)
    if avg_speed > cfg["B"]:
        return T0 + (avg_speed - cfg["L"]) * cfg["S"]
    return T0
```

**When does a PoC fire?**
- If `abs(z_oriented)` exceeds `Tt` for speed-breaker
- If `abs(z_oriented)` drops below `-Tt` for pothole (negative Z = downward)
- Record: `{timestamp, lat, lng, z_value, speed_kmh, threshold_used}`

---

### 4.3 Decision Tree Classifier (J48 equivalent)

```python
# File: backend/app/services/classifier.py
import pickle
import numpy as np
from sklearn.tree import DecisionTreeClassifier
from sklearn.model_selection import cross_val_score
from pathlib import Path

MODEL_PATH = Path("ml/models/decision_tree_v1.pkl")

EVENT_LABELS = {
    0: "anomaly",
    1: "speed_breaker",
    2: "pothole",
    3: "broken_patch"
}

def load_model() -> DecisionTreeClassifier:
    with open(MODEL_PATH, "rb") as f:
        return pickle.load(f)

def train_and_save(X: np.ndarray, y: np.ndarray) -> dict:
    """
    Train J48 (DecisionTreeClassifier with entropy criterion).
    Uses 10-fold cross-validation as per the paper.
    
    Feature vector: [z_value, z_next, z_prev, tp, speed_kmh]
    Labels: 0=anomaly, 1=speed_breaker, 2=pothole, 3=broken_patch
    """
    clf = DecisionTreeClassifier(
        criterion="entropy",       # J48 uses information gain (entropy)
        class_weight="balanced",   # handle class imbalance
        random_state=42
    )
    cv_scores = cross_val_score(clf, X, y, cv=10, scoring="f1_macro")
    clf.fit(X, y)
    
    with open(MODEL_PATH, "wb") as f:
        pickle.dump(clf, f)
    
    return {"cv_f1_mean": cv_scores.mean(), "cv_f1_std": cv_scores.std()}

def classify_poc(
    z_value: float,
    z_next: float | None,
    z_prev: float | None,
    tp: float,
    speed_kmh: float
) -> tuple[str, float]:
    """
    Classify a single PoC candidate.
    Returns (event_type, confidence_score)
    """
    clf = load_model()
    features = np.array([[
        z_value,
        z_next if z_next is not None else 0.0,
        z_prev if z_prev is not None else 0.0,
        tp,
        speed_kmh
    ]])
    pred = clf.predict(features)[0]
    conf = float(max(clf.predict_proba(features)[0]))
    return EVENT_LABELS[pred], conf

def detect_broken_patches(events: list[dict], time_window_sec: float = 3.0) -> list[list[dict]]:
    """
    Groups consecutive speed-breakers/potholes into broken patches.
    A broken patch = 3+ events within time_window_sec of each other.
    
    Source: Section 5.2.1 of the paper
    """
    if not events:
        return []
    
    events = sorted(events, key=lambda e: e["detected_at"])
    groups = []
    i = 0
    while i < len(events):
        group = [events[i]]
        while i + 1 < len(events):
            dt = (events[i+1]["detected_at"] - events[i]["detected_at"]).total_seconds()
            if dt <= time_window_sec:
                group.append(events[i+1])
                i += 1
            else:
                break
        if len(group) >= 3:
            groups.append(group)
        i += 1
    return groups
```

---

### 4.4 k-Medoids Clustering (Geo-Localization)

```python
# File: backend/app/services/clustering.py
import math
import numpy as np
from sklearn_extra.cluster import KMedoids
from sklearn.metrics import silhouette_score

def find_optimal_k(coords: np.ndarray, k_max: int = 10) -> int:
    """Silhouette analysis to find optimal number of clusters."""
    if len(coords) < 4:
        return max(1, len(coords) - 1)
    
    best_k, best_score = 2, -1
    for k in range(2, min(k_max + 1, len(coords))):
        km = KMedoids(n_clusters=k, metric="euclidean", random_state=42)
        labels = km.fit_predict(coords)
        score = silhouette_score(coords, labels)
        if score > best_score:
            best_score, best_k = score, k
    return best_k

def cluster_and_confirm(
    raw_events: list[dict],
    total_trails: int
) -> list[dict]:
    """
    Cluster geo-tagged road events from multiple trails.
    A confirmed event requires detection by >= ceil(NT/3) + 1 trails.
    
    Source: Section 5.2.2 of the paper
    
    Args:
        raw_events: list of {lat, lng, event_type, trip_id, detected_at, ...}
        total_trails: NT — total number of trails in the crowdsourced data
    
    Returns:
        list of confirmed events with cluster center as location
    """
    if len(raw_events) < 2:
        return []
    
    min_members = math.ceil(total_trails / 3) + 1
    coords = np.array([[e["lat"], e["lng"]] for e in raw_events])
    
    optimal_k = find_optimal_k(coords)
    km = KMedoids(n_clusters=optimal_k, metric="euclidean", random_state=42)
    labels = km.fit_predict(coords)
    
    confirmed = []
    for cluster_id in range(optimal_k):
        members = [raw_events[i] for i, l in enumerate(labels) if l == cluster_id]
        
        if len(members) < min_members:
            continue  # Not enough trail agreement → discard
        
        center = km.cluster_centers_[cluster_id]
        event_types = [m["event_type"] for m in members]
        dominant_type = max(set(event_types), key=event_types.count)
        
        confirmed.append({
            "lat": float(center[0]),
            "lng": float(center[1]),
            "event_type": dominant_type,
            "trail_count": len(members),
            "confidence_score": len(members) / total_trails,
            "first_seen": min(m["detected_at"] for m in members),
            "last_seen":  max(m["detected_at"] for m in members),
            "members": members,
        })
    
    return confirmed
```

---

## 5. API Route Conventions

```
Base URL:        /api/v1
Auth header:     Authorization: Bearer <jwt_token>
Content-Type:    application/json
Error format:    { "detail": "message", "code": "ERROR_CODE" }
Pagination:      ?page=1&limit=20
Geo filter:      ?bbox=lat1,lng1,lat2,lng2 (SW corner, NE corner)
```

### Standard Response Wrapper
```python
# schemas/base.py
from pydantic import BaseModel
from typing import Generic, TypeVar

T = TypeVar("T")

class ApiResponse(BaseModel, Generic[T]):
    success: bool = True
    data: T
    message: str | None = None
```

---

## 6. Celery Task Execution Order

When a user ends a trip:
```
1. POST /api/v1/trips/{id}/end  →  trip marked complete
2. POST /api/v1/trips/{id}/poc  →  PoC candidates uploaded (batch)
3. Celery: process_trip.delay(trip_id)
     ├── Load PoC candidates
     ├── Run classify_poc() on each
     ├── Save classified road_events
     ├── Run detect_broken_patches()
     └── Celery: trigger_clustering.delay(area_bbox)
           ├── Load all unconfirmed events in area
           ├── Count total trails in area
           ├── Run cluster_and_confirm()
           └── Upsert confirmed_events table
```

---

## 7. Mobile — Sensor Engine Pattern

```typescript
// mobile/services/SensorEngine.ts
// This runs during an active trip. NEVER block the main thread.

const SAMPLE_RATE_HZ = 150;
const SAMPLE_INTERVAL_MS = Math.floor(1000 / SAMPLE_RATE_HZ);

class SensorEngine {
  private tripId: string;
  private vehicleType: 'two_wheeler' | 'three_wheeler' | 'four_wheeler';
  private placement: 'mounter' | 'pocket' | 'dashboard';
  
  // Buffer: flush every 500 samples (~3.3s at 150Hz)
  private pocBuffer: PocCandidate[] = [];
  
  async start() {
    // 1. Subscribe to accelerometer + gyroscope at 150Hz
    // 2. Subscribe to GPS at 1Hz
    // 3. On each sample: auto-orient → threshold → if event → add to pocBuffer
    // 4. Flush pocBuffer to server every 500ms
  }
  
  private processSample(ax: number, ay: number, az: number, speedKmh: number) {
    // Auto-orient (implement Euler angle logic in TypeScript)
    const [ax_v, ay_v, az_v] = autoOrient(ax, ay, az);
    
    // Apply low-pass filter to Z
    const z_filtered = this.lowPassFilter(az_v);
    
    // Compute dynamic threshold
    const threshold = this.computeThreshold(speedKmh);
    
    // Detect event
    if (Math.abs(z_filtered) > threshold) {
      this.recordPoC(z_filtered, speedKmh, threshold);
    }
  }
}
```

---

## 8. Environment Variables

```bash
# backend/.env
DATABASE_URL=postgresql+asyncpg://user:pass@localhost:5432/roadsense
REDIS_URL=redis://localhost:6379/0
MINIO_ENDPOINT=localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=roadsense-sensor-data
JWT_SECRET_KEY=<generate-256-bit-secret>
JWT_ALGORITHM=HS256
JWT_EXPIRE_HOURS=24
CELERY_BROKER_URL=redis://localhost:6379/0
CELERY_RESULT_BACKEND=redis://localhost:6379/1
ML_MODEL_PATH=ml/models/decision_tree_v1.pkl

# web/.env.local
NEXT_PUBLIC_MAPBOX_TOKEN=<mapbox-public-token>
NEXTAUTH_SECRET=<generate-secret>
NEXT_PUBLIC_API_URL=http://localhost:8000/api/v1
```

---

## 9. Code Conventions

### Python (Backend)
- All route handlers are `async def`
- DB sessions via `Depends(get_db)` — use SQLAlchemy async session
- Pydantic v2 for all request/response schemas
- Services layer handles business logic — route handlers stay thin
- Celery tasks are idempotent and retriable (max_retries=3)
- Always use `ST_DWithin` for geo radius queries (PostGIS), not manual distance calc
- Type annotations everywhere

### TypeScript (Mobile + Web)
- Strict mode enabled (`"strict": true` in tsconfig)
- No `any` types — use proper interfaces
- Zustand stores are typed with `create<StoreType>()`
- React Query for all API calls (no raw fetch in components)
- Expo modules only — no bare React Native modules

### General
- Every function > 5 lines needs a docstring
- ML model version is tracked in filename: `decision_tree_v1.pkl`
- No magic numbers — define as constants with comments linking to paper
- Git commit style: `feat:`, `fix:`, `ml:`, `docs:`, `refactor:`

---

## 10. Common Pitfalls — Do NOT Do These

```
❌ Don't use static thresholds for event detection
   → Always use compute_threshold() with live speed

❌ Don't detect events when speed < 5 km/h
   → Stationary noise will generate massive false positives

❌ Don't skip the auto-orientation step
   → Raw accelerometer Z-axis is useless without reorientation

❌ Don't confirm an event from a single trail
   → Must pass cluster_and_confirm() with min_members check

❌ Don't store raw 150Hz sensor data in PostgreSQL
   → Use MinIO for sensor logs, only metadata in DB

❌ Don't run clustering synchronously in API handlers
   → Always dispatch as Celery task

❌ Don't use KMeans instead of KMedoids
   → Paper explicitly uses k-medoids (robust to outliers in GPS coords)

❌ Don't skip 10-fold cross-validation during training
   → Required for model validation as per paper methodology

❌ Don't use the same road data for training and testing
   → Paper uses separate roads for train vs test (see Table 1)
```

---

## 11. Testing Checkpoints

Before declaring a module complete, verify:

### Orientation Engine
- [ ] Phone horizontal → Z-axis correctly captures vertical movement
- [ ] Phone vertical (mounted) → Z-axis still captures vertical movement
- [ ] Phone tilted 45° → Z-axis still works correctly
- [ ] Sudden phone rotation → system recovers within 500ms

### Threshold Engine
- [ ] At 10 km/h → threshold = T0 (static mode)
- [ ] At 30 km/h → threshold > T0 (dynamic mode)
- [ ] Speed-breaker threshold increases with speed ✓
- [ ] Pothole threshold decreases with speed ✓

### ML Classifier
- [ ] F1-score ≥ 0.93 for speed-breaker (smooth road, 10-fold CV)
- [ ] F1-score ≥ 0.91 for pothole (smooth road, 10-fold CV)
- [ ] No false positives when vehicle is stationary
- [ ] Broken patch detection: ≥ 3 consecutive events within 3s

### Clustering
- [ ] Event from 1 trail → NOT confirmed
- [ ] Event from ≥ ceil(NT/3)+1 trails → confirmed
- [ ] Cluster center is closer to actual event than any individual detection
- [ ] Silhouette score computed before choosing k

### API
- [ ] Trip start/end flow works end-to-end
- [ ] PoC batch upload < 200ms response
- [ ] Map events API supports bbox filtering with PostGIS
- [ ] Civic routes return 403 for non-civic users

---

## 12. MVP Build Order

Build in this exact order to maintain a working system at each step:

```
Phase 1 — Foundation
  [1] Docker Compose setup (PostgreSQL+PostGIS, Redis, MinIO)
  [2] FastAPI skeleton + health check endpoint
  [3] Database migrations (Alembic) — all tables
  [4] Auth system (register, OTP login, JWT)

Phase 2 — Core ML Pipeline
  [5] Auto-orientation service (Python)
  [6] Dynamic threshold service (Python)
  [7] Decision tree training script (ml/train.py)
  [8] Classifier service + tests

Phase 3 — Backend API
  [9] Trip CRUD endpoints
  [10] PoC batch upload endpoint
  [11] Celery pipeline (process_trip → classify → cluster)
  [12] Events map API (bbox query with PostGIS)
  [13] Civic dashboard API

Phase 4 — Mobile App
  [14] Expo project setup + navigation
  [15] Onboarding + auth flow
  [16] Sensor engine (auto-orient + threshold) — test with real device
  [17] Trip recording + PoC upload
  [18] Map screen with event markers
  [19] Alert system (audio + haptic)

Phase 5 — Web Dashboard
  [20] Next.js setup + shadcn/ui + Mapbox
  [21] Public road event map
  [22] Civic dashboard (auth-gated)
  [23] RQI map + repair priority list
  [24] Export (CSV + PDF)
```

---

*AI Instructions Version: 1.0 | Project: RoadSense AI*
*Tell Claude Code: "Read AI_INSTRUCTIONS.md first, then ask me which phase to build."*
