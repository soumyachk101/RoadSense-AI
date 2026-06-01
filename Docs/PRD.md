# 📋 Product Requirements Document (PRD)
## RoadSense AI — Crowdsourced Road Intelligence Platform

> **Based on:** *"Crowdsourcing from the True Crowd: Device, Vehicle, Road-Surface and Driving Independent Road Profiling from Smartphone Sensors"* — Alam et al., Pervasive and Mobile Computing (2020)

---

## 1. Product Overview

### 1.1 Vision Statement
RoadSense AI is a smartphone-powered, crowdsourced road intelligence platform that detects and maps **potholes**, **speed-breakers**, and **broken road patches** in real time — independent of vehicle type, smartphone model, phone placement, or driving style. It turns everyday commuters into passive road quality sensors, feeding a living map of road conditions for drivers, civic authorities, and urban planners.

### 1.2 Problem Statement
India's roads kill more people from potholes and unmarked speed-breakers than from terrorism. Existing apps:
- Work only on cars (4-wheelers) — fail on bikes, autos (2/3-wheelers)
- Use static thresholds — break on rough roads
- Don't detect broken road patches as a separate category
- Require manual tagging — no passive, always-on sensing
- Accuracy drops to **<70% on rough Indian suburban roads**

**RoadSense solves all of the above.**

### 1.3 Target Users

| User Type | Primary Need | How RoadSense Helps |
|-----------|-------------|---------------------|
| **Daily Commuter** (bike/auto/car) | Safe navigation, avoid bad roads | Real-time alerts + route recommendations |
| **Civic Authority / Municipality** | Identify roads needing repair | Heatmaps, severity scores, export reports |
| **Logistics / Fleet Manager** | Reduce vehicle wear, plan routes | Fleet dashboard, road condition scores |
| **Urban Researcher / Planner** | Road quality data at scale | API access, data export, analytics |

---

## 2. Goals & Non-Goals

### 2.1 Goals (v1.0)
- [x] Passive real-time detection of 3 road events: **speed-breakers**, **potholes**, **broken patches**
- [x] Works across **2-wheeler, 3-wheeler, 4-wheeler** vehicles
- [x] Works regardless of **phone placement** (pocket, dashboard, handlebar mount)
- [x] Works across **6+ Android smartphone models**
- [x] Crowdsourced **geo-tagged event map** with confidence scoring
- [x] **Auto-orientation** and **auto-threshold** algorithms (no manual calibration)
- [x] Civic authority **dashboard** with heatmaps and repair priority scoring
- [x] Driver **alert system** (audio + haptic) when approaching known events
- [x] **Route recommendation** avoiding bad road stretches

### 2.2 Non-Goals (v1.0)
- ❌ iOS support (Android only, v1.0)
- ❌ Manhole / joint detection (v2.0)
- ❌ Real-time speed enforcement
- ❌ Paid civic data monetisation (v2.0)
- ❌ Offline-only mode (requires internet for crowdsourcing sync)

---

## 3. Core Features

### Feature 1 — Passive Road Sensing (Mobile App)
**Description:** The app runs in the background during any commute, collecting accelerometer + gyroscope + GPS data at 150 Hz. No user interaction required after onboarding.

**Acceptance Criteria:**
- App detects events without user manually pressing anything
- Battery drain must be < 5% per hour of sensing
- Data is stored locally and synced to server at trip end
- Works with phone in pocket, dashboard mount, or handlebar mount

---

### Feature 2 — Auto-Orientation Engine
**Description:** Uses Euler angle-based 3D rotation to normalize phone orientation regardless of how the phone is held or mounted. Translates device reference frame → vehicle reference frame.

**Acceptance Criteria:**
- Handles horizontal, vertical, and tilted phone positions
- Z-axis (vertical) is correctly isolated across all orientations
- Re-orientation runs continuously (every 500ms) during a trip
- Compensates for sudden orientation changes (user picks up phone)

---

### Feature 3 — Auto-Tune Dynamic Threshold
**Description:** Instead of fixed accelerometer thresholds (which fail on rough roads), RoadSense computes a dynamic threshold based on vehicle speed using an auto-regressive time series model.

**Formula:**
```
Tt = T0 + ((1/t) * Σ Vi - L) * S    if Σ Vi > B
Tt = T0                              otherwise
```

**Acceptance Criteria:**
- Threshold updates every second using GPS speed
- Initial thresholds pre-configured per vehicle type (2/3/4-wheeler)
- Speed-breaker threshold increases with speed; pothole threshold decreases
- Phase-I detection achieves >83% accuracy (before ML layer)

---

### Feature 4 — ML Classification Server (Decision Tree)
**Description:** Geo-tagged PoC candidates from Phase I are sent to the server where a J48 Decision Tree classifier removes false positives/negatives and classifies events into speed-breaker, pothole, or broken patch.

**Features used by classifier:**
| Feature | Description |
|---------|-------------|
| `Zt` | Z-axis acceleration at event detection time |
| `Z_next` | Local min/max just AFTER Zt within window Δ |
| `Z_prev` | Local min/max just BEFORE Zt within window Δ |
| `Tp` | Time gap between consecutive PoC events |
| `Sp` | Vehicle speed at detected PoC |

**Acceptance Criteria:**
- F1-Score ≥ 96% for speed-breakers (smooth road)
- F1-Score ≥ 90% for potholes (smooth road)
- F1-Score ≥ 92% for speed-breakers (rough road)
- F1-Score ≥ 90% for potholes (rough road)

---

### Feature 5 — k-Medoids Geo-Localization
**Description:** Combines PoC detections from multiple user trails using k-medoids clustering to precisely locate road events on a map, filtering out low-confidence lone detections.

**Acceptance Criteria:**
- Events confirmed only if detected by ≥ ⌈NT/3⌉ + 1 trails
- Silhouette analysis used to determine optimal k
- Cluster center = final geo-tagged event location
- Events visualized on interactive map

---

### Feature 6 — Live Road Event Map
**Description:** Interactive web + mobile map showing all confirmed road events with severity markers, event type icons, and confidence scores.

**Acceptance Criteria:**
- Map updates within 5 minutes of new crowdsourced data
- Filter by: event type, severity, date range, road type
- Tap on marker → see event details (type, count of reporters, estimated length for broken patches)
- Heatmap layer available

---

### Feature 7 — Driver Alert System
**Description:** Auditory + haptic alerts when the driver approaches a known road event within configurable distance (default 100m).

**Acceptance Criteria:**
- Alert fires when within 100m of a confirmed event (configurable)
- Audio alert: TTS voice "Pothole ahead in 80 meters"
- Haptic buzz pattern (different per event type)
- Alert only if confidence score > threshold
- No alert for events the driver has already passed

---

### Feature 8 — Civic Authority Dashboard (Web)
**Description:** Web dashboard for municipality/civic authority users to see road quality data, prioritise repair work, and export reports.

**Acceptance Criteria:**
- View all events by ward/zone/city level
- Filter by severity: Critical (broken patch >100m), High (pothole), Medium (speed-breaker)
- Export CSV/PDF repair priority report
- Road Quality Index (RQI) score per road segment
- Time-series view showing road degradation over weeks/months

---

## 4. User Stories

```
US-001: As a commuter on a bike, I want the app to silently detect road 
        events as I ride so I get alerts without looking at my phone.

US-002: As a civic engineer, I want to see which road stretches have 
        the most potholes so I can prioritise repair budgets.

US-003: As a fleet manager, I want route recommendations that avoid 
        broken road patches to reduce truck maintenance costs.

US-004: As an auto-rickshaw driver, I want audio warnings about 
        potholes ahead so I can slow down in time.

US-005: As a researcher, I want to download road quality data by 
        area and date range for urban mobility analysis.
```

---

## 5. Success Metrics

| Metric | Target (3 months post-launch) |
|--------|-------------------------------|
| Active sensing users | 500+ |
| Road events mapped | 10,000+ |
| Detection accuracy (speed-breaker) | ≥ 96% (smooth), ≥ 92% (rough) |
| Detection accuracy (pothole) | ≥ 92% (smooth), ≥ 90% (rough) |
| Civic authority signups | 3+ municipal bodies |
| Avg session length (background sensing) | ≥ 20 min/day |
| False alert rate | < 5% |

---

## 6. Out of Scope / Future Roadmap

### v2.0 Roadmap
- iOS support (Swift + CoreMotion)
- Manhole, joint, and pavement crack detection
- Sub-classify speed-breakers: multi-peak, sharp, low, effective/ineffective
- Road Quality Index API for third-party navigation apps
- ML model upgrade: LSTM / CNN on raw accelerometer sequences
- Integration with Google Maps / Apple Maps via SDK

---

## 7. Constraints & Assumptions

- Minimum Android version: **8.0 (Oreo)**
- Requires GPS + Accelerometer + Gyroscope hardware
- Minimum sensing speed: **> 5 km/h** (stationary detection excluded)
- Internet required for sync (offline detection buffered up to 50MB)
- Training data collected on Indian suburban road conditions (generalises with retraining)

---

*PRD Version: 1.0 | Project: RoadSense AI | Stack Owner: Soumya CHK*
