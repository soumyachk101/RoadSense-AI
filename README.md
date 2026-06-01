# RoadSense AI (Kotlin Version)

RoadSense AI is an intelligent road condition monitoring platform that uses smartphone sensors and ML to detect potholes, speed breakers, and road anomalies in real-time.

This project has been fully migrated to a Kotlin-centric stack.

## Architecture

- **[backend-kotlin](./backend-kotlin)**: Ktor-based backend service.
- **[ml-kotlin](./ml-kotlin)**: Ktor-based ML classification and clustering service.
- **[mobile-kotlin](./mobile-kotlin)**: Native Android app built with Jetpack Compose.

## Getting Started

### Prerequisites
- JDK 17+
- Android Studio (Iguana or newer)
- PostgreSQL

### Running the Services

1. **Backend**:
   ```bash
   cd backend-kotlin
   ./gradlew run
   ```

2. **ML Service**:
   ```bash
   cd ml-kotlin
   ./gradlew run
   ```

3. **Mobile App**:
   Open `mobile-kotlin` in Android Studio and run on an Android device.

## License
MIT
