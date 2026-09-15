# Project Saver — Comprehensive Source Code Architecture & Breakdown

**Document Version:** 1.0  
**Section:** 5.F (Comprehensive Source Code Explanation)

---

## 1. System Code Architecture & Data Flow

The software stack of **Project Saver** is divided into three primary modules:
1. **ESP32 Main Firmware (`firmware/esp32_main/`):** Written in C++ using Arduino framework. Manages Wi-Fi, WebSockets server, JSON decoding, TB6612FNG motor control, Servo positioning, Relay switching, and Piezo audio.
2. **ESP32-CAM Firmware (`firmware/esp32_cam/`):** Written in C++. Drives the OV2640 camera sensor and hosts an HTTP MJPEG stream server.
3. **Android Mobile App (`SaverRover/`):** Native Android UI built with Kotlin and Jetpack Compose, providing touch controls, servo sliders, MJPEG stream view, and WebSocket telemetry communication using OkHttp.

---

## 2. Firmware Breakdown (`esp32_main.ino`)

### A. Modular Design & Libraries Used
- **`WiFi.h` & `WebServer.h`:** Manages SoftAP mode (`Saver_Rover`, `rover1234`) and local HTTP diagnostic endpoint.
- **`WebSocketsServer.h`:** Runs an asynchronous WebSocket server on port 8080. WebSockets provide ultra-low latency (<20ms) bidirectional data transfer compared to traditional polling HTTP GET requests.
- **`ArduinoJson.h`:** Parses incoming JSON control packets from the Android app.
- **`ESP32Servo.h`:** Employs ESP32 hardware timers to produce precise 50Hz PWM signals for the Pan, Tilt, and TowerPro metal gear servos.

### B. Hardware Initialization & PWM Setup (`setupHardware()`)
```cpp
// ESP32 LEDC PWM Driver Configuration for Motors
ledcSetup(PWM_CHAN_MOTORS_A, 5000, 8); // Channel 0, 5 kHz, 8-bit (0-255)
ledcSetup(PWM_CHAN_MOTORS_B, 5000, 8); // Channel 1, 5 kHz, 8-bit (0-255)
ledcAttachPin(PIN_MOTOR_PWMA, PWM_CHAN_MOTORS_A);
ledcAttachPin(PIN_MOTOR_PWMB, PWM_CHAN_MOTORS_B);
```
- Configuring a 5 kHz PWM frequency ensures quiet, smooth motor operation without audible coil whine.

### C. Differential Steering Logic (`setMotorSpeeds()`)
- Maps signed speed values (-255 to 255) to TB6612FNG logic inputs:
  - **Forward:** `AIN1 = HIGH`, `AIN2 = LOW`, `PWMA = speed`
  - **Reverse:** `AIN1 = LOW`, `AIN2 = HIGH`, `PWMA = |speed|`
  - **Brake/Stop:** `AIN1 = LOW`, `AIN2 = LOW`, `PWMA = 0`

### D. Emergency Fail-Safe Mechanisms
- In `handleWebSocketEvent()`, if a client disconnects (`WStype_DISCONNECTED`), `setMotorSpeeds(0, 0)` is automatically executed. This prevents runaway rover behavior if Wi-Fi signal is lost.

---

## 3. ESP32-CAM Streaming Firmware (`esp32_cam.ino`)

- Configures OV2640 camera sensor via `esp_camera_init()`.
- Captures JPEG image frames into frame buffer (`camera_fb_t`).
- Transmits frames over HTTP GET endpoint `/stream` on port 81 using `multipart/x-mixed-replace;boundary=...` content headers.
- Achieves 25–30 FPS at VGA resolution (640x480) when PSRAM is enabled.

---

## 4. Android Mobile App Breakdown (`SaverRover/`)

### A. Architecture & Components
- **`MainActivity.kt`:** App entry point locking orientation to landscape, enabling edge-to-edge display, and initializing the `RoverScreen` composable.
- **`ui/RoverScreen.kt`:** Root Jetpack Compose screen assembling live video stream, telemetry status overlay, drive controls, gimbal sliders, and auxiliary switches.
- **`telemetry/TelemetryClient.kt`:** Asynchronous WebSocket manager built on OkHttp. Sends JSON control frames:
  - `sendMove(left, right)` -> `{ "type": "move", "left": 255, "right": 255 }`
  - `sendPanTilt(pan, tilt)` -> `{ "type": "pantilt", "pan": 90, "tilt": 90 }`
  - `sendSpotlight(state)` -> `{ "type": "spotlight", "state": true }`
  - `sendHorn(state)` -> `{ "type": "horn", "state": true }`
- **`camera/MjpegStreamReader.kt` & `MjpegSurfaceView.kt`:** Connects to `http://192.168.4.1:81/stream`, parses MJPEG boundary delimiters, and renders JPEG frames onto a Android `SurfaceView`.
- **`controls/` Components:**
  - `Joystick.kt`: Touch joystick & D-Pad direction controls with speed selection presets.
  - `GimbalControls.kt`: Interactive sliders for camera pan/tilt positioning.
  - `AuxiliaryControls.kt`: Switches for 60W Spotlight Relay and Horn trigger.

