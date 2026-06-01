# 🎨 UI/UX Design Document
## RoadSense AI — Design System & Screen Specifications

---

## 1. Design Philosophy

> **"Invisible until it matters."**

RoadSense runs silently in the background. The UI should feel like a co-pilot, not a distraction. Every screen must be:
- **Glanceable** — core info readable in < 1 second
- **High contrast** — works in direct sunlight (outdoor use)
- **One-handed** — 90% of interactions reachable with thumb
- **Alert-forward** — danger warnings are unmissable

---

## 2. Design System

### 2.1 Color Palette

```
Background:      #0d0221   (Deep space — primary BG)
Surface:         #1a1035   (Card / modal BG)
Surface-Raised:  #241548   (Elevated card)
Border:          #2d1f5e   (Dividers, input borders)

Primary:         #7c3aed   (Purple — brand, CTAs)
Primary-Light:   #a78bfa   (Light purple — hover states)
Primary-Glow:    rgba(124, 58, 237, 0.25)

Accent:          #06b6d4   (Teal — active states, links)
Success:         #10b981   (Green — trip active, resolved)
Warning:         #f59e0b   (Amber — speed breaker)
Danger:          #ef4444   (Red — pothole, critical events)
Broken-Patch:    #f97316   (Orange — broken road patches)

Text-Primary:    #f8fafc
Text-Secondary:  #94a3b8
Text-Muted:      #475569
```

### 2.2 Typography

```
Font Family:     Inter (primary), Space Mono (data/code)

Scale:
  Display:       32px / 700 weight
  H1:            24px / 700
  H2:            20px / 600
  H3:            16px / 600
  Body:          14px / 400
  Caption:       12px / 400
  Label:         11px / 500 / UPPERCASE + letter-spacing

Line Height:     1.5× font size
```

### 2.3 Event Type Visual Language

| Event Type | Color | Icon | Marker Shape |
|------------|-------|------|-------------|
| Speed Breaker | `#f59e0b` (Amber) | ⚡ | Triangle |
| Pothole | `#ef4444` (Red) | 🕳 | Circle |
| Broken Patch | `#f97316` (Orange) | ⚠️ | Rectangle |
| Resolved | `#10b981` (Green) | ✓ | Crossed circle |

### 2.4 Severity Mapping

```
CRITICAL  →  #dc2626  (Deep Red)  — broken patch > 100m
HIGH      →  #ef4444  (Red)       — large pothole
MEDIUM    →  #f59e0b  (Amber)     — speed breaker / small pothole
LOW       →  #84cc16  (Lime)      — minor anomaly
```

### 2.5 Spacing System
```
4px base unit
xs: 4px  |  sm: 8px  |  md: 16px  |  lg: 24px  |  xl: 32px  |  2xl: 48px
Border radius: 8px (cards), 12px (modals), 999px (pills/badges)
```

---

## 3. Mobile App Screens

### Screen 1 — Onboarding (3 slides)

**Slide 1: Hook**
```
┌──────────────────────────┐
│                          │
│   [Animation: Road map   │
│    with event markers    │
│    appearing in real     │
│    time]                 │
│                          │
│  India's roads are       │
│  mapped by satellites.   │
│  But potholes aren't.    │
│                          │
│  Until now.              │
│                          │
│  ●○○  [Skip]             │
└──────────────────────────┘
```

**Slide 2: How it works**
```
┌──────────────────────────┐
│                          │
│   [3 icons in sequence:  │
│    📱→ 📡→ 🗺️]           │
│                          │
│  Just ride.              │
│  RoadSense detects.      │
│  The map updates.        │
│                          │
│  No tapping. No tagging. │
│  Fully automatic.        │
│                          │
│  ○●○  [Skip]             │
└──────────────────────────┘
```

**Slide 3: Setup**
```
┌──────────────────────────┐
│  One-time setup          │
│                          │
│  My vehicle is:          │
│  ┌──────┐┌──────┐┌─────┐│
│  │ 🏍️  ││ 🛺   ││ 🚗  ││
│  │ Bike ││ Auto ││ Car ││
│  └──────┘└──────┘└─────┘│
│                          │
│  My phone is usually:    │
│  ○ Mounted on vehicle    │
│  ○ In my pocket          │
│  ○ On dashboard          │
│                          │
│  [Get Started →]         │
└──────────────────────────┘
```

---

### Screen 2 — Home / Map View

```
┌──────────────────────────┐
│  RoadSense     [⚙️] [👤] │
├──────────────────────────┤
│                          │
│   [Full-screen dark map  │
│    — Mapbox dark theme]  │
│                          │
│   🔴 = Pothole           │
│   🟡 = Speed Breaker     │
│   🟠 = Broken Patch      │
│                          │
│   [User location pin]    │
│   (pulsing blue dot)     │
│                          │
│  ┌────────────────────┐  │
│  │ Filter: All │ 🔴 🟡 🟠│  │
│  └────────────────────┘  │
│                          │
├──────────────────────────┤
│ [🏁 Start Trip]          │
│ [━━━━━━━━━━━━━━━━━━━━━━] │
│ 📍 127 events near you   │
└──────────────────────────┘
```

**Map Interaction States:**
- **Tap marker** → bottom sheet slides up with event detail
- **Tap cluster** → map zooms into cluster
- **Long-press** → manually report an event (optional)

---

### Screen 3 — Active Trip Screen

```
┌──────────────────────────┐
│  🟢 SENSING ACTIVE       │
│  2.4 km covered          │
├──────────────────────────┤
│                          │
│   [Dark map with         │
│    live route trail]     │
│                          │
│   Live trail in purple   │
│   with event markers     │
│   appearing as detected  │
│                          │
├──────────────────────────┤
│ ┌────────┬──────┬──────┐ │
│ │ Speed  │Events│ Time │ │
│ │ 32km/h │  3   │ 4:12 │ │
│ └────────┴──────┴──────┘ │
│                          │
│ Last event:              │
│ ⚡ Speed Breaker — 200m ago│
│                          │
│    [⏹ END TRIP]          │
└──────────────────────────┘
```

**Live State Changes:**
- Purple trail follows user movement
- Event marker drops on map with haptic when detected
- Speed display updates every second from GPS

---

### Screen 4 — ALERT OVERLAY (Full-screen flash)

```
┌──────────────────────────┐
│                          │
│   ████████████████████   │
│   ██                 ██  │
│   ██    🕳️           ██  │
│   ██                 ██  │
│   ██   POTHOLE       ██  │
│   ██   AHEAD         ██  │
│   ██   80 meters     ██  │
│   ██                 ██  │
│   ████████████████████   │
│                          │
│  (Background: #ef4444    │
│   red pulse animation)   │
│                          │
│  Audio: "Pothole ahead,  │
│         80 meters"       │
│                          │
│  [Auto-dismisses in 3s]  │
└──────────────────────────┘
```

**Alert variants:**
- 🔴 RED — Pothole / Critical broken patch
- 🟡 AMBER — Speed breaker  
- 🟠 ORANGE — Broken road patch ahead

---

### Screen 5 — Event Detail Bottom Sheet

```
┌──────────────────────────┐
│   ─────                  │  ← drag handle
│   🕳️ Pothole             │
│   HIGH severity          │
│                          │
│   📍 NH-19, Durgapur     │
│   🕐 Reported 2h ago     │
│   👥 14 riders confirmed │
│   📊 Confidence: 94%     │
│                          │
│   Detected by:           │
│   🏍️ 8  🛺 4  🚗 2      │
│                          │
│   [Approach Alert: ON ●] │
│   [Report Fixed]         │
└──────────────────────────┘
```

---

### Screen 6 — Trip History

```
┌──────────────────────────┐
│  ← My Trips              │
├──────────────────────────┤
│  Today                   │
│ ┌────────────────────┐   │
│ │ 🏍️ 8:30 AM Trip   │   │
│ │ 12.4 km · 34 min  │   │
│ │ 3 events detected  │   │
│ │ 2🔴 1🟡            │   │
│ └────────────────────┘   │
│                          │
│  Yesterday               │
│ ┌────────────────────┐   │
│ │ 🏍️ 9:15 AM Trip   │   │
│ │ 8.2 km · 22 min   │   │
│ │ 1 event detected   │   │
│ │ 1🟠                │   │
│ └────────────────────┘   │
│                          │
│  You've helped map       │
│  127 road events! 🎉     │
└──────────────────────────┘
```

---

## 4. Web Dashboard Screens (Civic Authority)

### Screen 1 — Dashboard Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  RoadSense Civic  [Durgapur Municipal Corporation]  [Export ↓]  │
├─────────┬───────────────────────────────────────────────────────┤
│         │   ┌───────┐  ┌───────┐  ┌───────┐  ┌───────┐        │
│  [Nav]  │   │  847  │  │  312  │  │  94   │  │ 67.3  │        │
│         │   │Potholes│  │ Speed │  │Broken │  │  RQI  │        │
│  🗺 Map │   │       │  │Brkrs  │  │Patches│  │ Score │        │
│  📊 RQI │   └───────┘  └───────┘  └───────┘  └───────┘        │
│  ⚠️ Evt │                                                        │
│  📁 Rpt │   ┌─────────────────────────┐  ┌───────────────────┐ │
│  ⚙️ Cfg │   │                         │  │  Priority Queue   │ │
│         │   │   [Full Interactive Map] │  │                   │ │
│         │   │   Dark Mapbox theme      │  │  1. NH-19 Km 14  │ │
│         │   │   Heatmap layer active   │  │     CRITICAL 🔴  │ │
│         │   │                          │  │     Broken 420m  │ │
│         │   │                          │  │                   │ │
│         │   │                          │  │  2. City Rd B-12 │ │
│         │   └─────────────────────────┘  │     HIGH 🔴       │ │
│         │                                │     11 potholes   │ │
│         │   [Event Timeline Chart]        │                   │ │
│         │   (Recharts, last 30 days)      │  [View All →]     │ │
│         │                                └───────────────────┘ │
└─────────┴───────────────────────────────────────────────────────┘
```

---

### Screen 2 — Road Quality Index Map

```
┌─────────────────────────────────────────────────┐
│  Road Quality Index — Durgapur  [Date: Jan 2024]│
├─────────────────────────────────────────────────┤
│                                                  │
│  [Choropleth map — road segments colored by RQI] │
│                                                  │
│  🟢 80-100  Good                                 │
│  🟡 60-79   Fair                                 │
│  🟠 40-59   Poor                                 │
│  🔴  0-39   Critical                             │
│                                                  │
│  Tap road segment → RQI detail panel             │
│  ┌────────────────────────────────────┐          │
│  │ NH-19 · Segment 14               │          │
│  │ RQI Score: 23 / 100  🔴 CRITICAL  │          │
│  │ Events: 3 broken patches (>100m)  │          │
│  │ Pothole count: 18                 │          │
│  │ Last reported: 2 hours ago        │          │
│  │ Estimated repair cost: ₹ ---      │          │
│  │ [Add to Repair Queue]             │          │
│  └────────────────────────────────────┘          │
│                                                  │
│  [Export PDF Report] [Download CSV]              │
└─────────────────────────────────────────────────┘
```

---

## 5. Component Specifications

### 5.1 Event Marker (Map)
```
Unconfirmed (<3 trails):   Semi-transparent, dashed border
Confirmed (3-10 trails):   Solid, medium opacity  
High Confidence (>10):     Solid, full opacity, subtle glow
```

### 5.2 Stat Cards
```css
Background: #1a1035
Border: 1px solid #2d1f5e
Border-top: 3px solid [event-color]
Border-radius: 12px
Padding: 20px
Shadow: 0 4px 24px rgba(0,0,0,0.4)
```

### 5.3 Alert Banner (Web Dashboard)
```
Critical event within last 1 hour →
Red top banner: "🔴 NEW: Broken patch detected on NH-19 — 23 minutes ago"
Auto-dismisses after 10s unless pinned
```

### 5.4 Trip Active Pill (Mobile)
```
Position: top-center, below status bar
Style: pill shape, green pulse border
Content: "● SENSING  12.4km  14:23"
Tap → goes to active trip screen
```

---

## 6. Micro-Interactions & Animations

| Trigger | Animation |
|---------|-----------|
| Event detected during trip | Marker drops onto map with subtle bounce, haptic pulse |
| Alert fires | Screen edge glows red (0.5s), then alert overlay fades in |
| Trip ended | Confetti burst if > 3 events detected |
| Map marker tap | Bottom sheet slides up (spring animation, 300ms) |
| Trip start | Map zooms to user location, trail line begins drawing |
| New confirmed event on dashboard | Row slides in with green highlight fade |

---

## 7. Empty & Error States

**No events nearby:**
```
🗺️
No road events in your area yet.
You'll be the first to map it.
[Start a Trip →]
```

**Trip ended, no events:**
```
✅
Clean road detected!
0 events on your 8.2km trip.
Thanks for helping verify safe roads.
```

**Offline state:**
```
📡
You're offline.
Sensing still works — data syncs when you reconnect.
[Events buffered: 3]
```

---

## 8. Accessibility

- Minimum touch target: 44 × 44 px
- Color + shape always paired (never color alone for severity)
- Screen reader labels on all map markers
- Audio alerts configurable: ON / Vibrate-only / OFF
- High contrast mode: available in settings
- Font size: respects system font size setting

---

## 9. Design Tokens (Tailwind Config)

```js
// tailwind.config.js
module.exports = {
  theme: {
    extend: {
      colors: {
        bg:        '#0d0221',
        surface:   '#1a1035',
        elevated:  '#241548',
        border:    '#2d1f5e',
        primary:   '#7c3aed',
        'primary-light': '#a78bfa',
        accent:    '#06b6d4',
        success:   '#10b981',
        warning:   '#f59e0b',
        danger:    '#ef4444',
        patch:     '#f97316',
        'text-primary':   '#f8fafc',
        'text-secondary': '#94a3b8',
        'text-muted':     '#475569',
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        mono: ['Space Mono', 'monospace'],
      },
      borderRadius: {
        'card':  '8px',
        'modal': '12px',
        'pill':  '999px',
      }
    }
  }
}
```

---

*UIUX Version: 1.0 | Project: RoadSense AI | Stack Owner: Soumya CHK*
