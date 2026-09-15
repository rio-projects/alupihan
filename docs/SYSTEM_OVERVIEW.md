# Project Saver — General Project Overview & Whole-System Explanation

**Document Version:** 1.0  
**Section:** 5.H & 5.I (General Project Overview & Comprehensive Whole-System Flow)

---

## 1. High-Level Project Overview (Section 5.H)

Project **Saver** is a lightweight, 4WD remote-controlled surveillance rover designed for localized environmental inspection, obstacle reconnaissance, and real-time visual streaming.

### Key Objectives Solved
1. **Low-Latency Surveillance:** Provides live video feedback to remote operators via local Wi-Fi, enabling inspection of confined or hazard-prone areas without physical presence.
2. **Efficient Actuation:** Utilizes the high-efficiency **TB6612FNG MOSFET motor driver** for smooth 4WD skid-steer maneuvering.
3. **Multi-Axis Target Tracking:** Features a 2-axis Pan-Tilt bracket holding the camera module alongside a **TowerPro Metal Gear Servo** for auxiliary payload deployment.
4. **Night Operations Capability:** Equipped with a relay-switched **60W High-Intensity Motorcycle Spotlight** for dark environment surveillance.

---

## 2. Comprehensive Whole-System Architecture & Flow (Section 5.I)

The operation of **Project Saver** is based on two parallel, real-time data loops:
1. **Control & Command Pipeline (Input -> Processing -> Output)**
2. **Vision & Video Stream Pipeline (Sensor -> Processing -> Output)**

```
+-----------------------------------------------------------------------------------+
|                            PARALLEL SYSTEM DATA FLOW                              |
+-----------------------------------------------------------------------------------+

[LOOP 1: CONTROL & COMMAND PIPELINE]

   INPUT                           PROCESSING                         OUTPUT
+-----------------------+       +-------------------+       +-----------------------+
| Android App UI        |       | Main ESP32        |       | 1. TB6612FNG Driver   |
| (SaverRover)       | ----> | Controller        | ----> |    (4x TT Motors)     |
|  - Touch D-Pad / Move | (WS)  |  - Decodes JSON   |       | 2. Pan-Tilt Servos    |
|  - Pan-Tilt Sliders   |       |  - Generates PWM  |       | 3. TowerPro Servo     |
|  - Spotlight Switch   |       |  - Toggles GPIOs  |       | 4. 60W Spotlight Relay|
|  - Horn Button        |       |                   |       | 5. Active Piezo Horn  |
+-----------------------+       +-------------------+       +-----------------------+

-------------------------------------------------------------------------------------

[LOOP 2: REAL-TIME VISION & VIDEO PIPELINE]

   INPUT                           PROCESSING                         OUTPUT
+-----------------------+       +-------------------+       +-----------------------+
| OV2640 Camera Sensor  | ----> | ESP32-CAM Board   | ----> | Android Mobile App    |
| (Physical Light Input)| (DVP) |  - JPEG Encoder   | (HTTP)| Control Screen        |
|                       |       |  - Stream Server  | Stream| (Live 30 FPS Stream)  |
+-----------------------+       +-------------------+       +-----------------------+
```

---

### Pipeline 1 Detailed Breakdown (Control Loop)
1. **INPUT (User Action):** The operator interacts with the Android app (`SaverRover`) interface on their mobile device (e.g., pressing `FORWARD` or toggling `SPOTLIGHT ON`).
2. **TRANSMISSION:** The app serializes the action into a lightweight JSON packet (e.g. `{"type":"move","left":255,"right":255}`) and sends it over a TCP WebSocket connection (`ws://192.168.4.1:8080`).
3. **PROCESSING (Main ESP32):** The Main ESP32 receives the packet, deserializes the JSON via `ArduinoJson`, and determines target actuator speeds or pin states.
4. **OUTPUT (Actuation):**
   - **Locomotion:** ESP32 updates hardware `ledc` PWM duty cycles on pins 14 (`PWMA`) and 25 (`PWMB`) and directional pins (`AIN1`, `AIN2`, `BIN1`, `BIN2`), driving the TB6612FNG output channels.
   - **Servos:** ESP32 adjusts 50Hz PWM pulse widths (500us–2500us) on GPIOs 18, 19, and 21.
   - **Spotlight:** ESP32 pulls GPIO 22 LOW, triggering the optocoupled relay to close the 12V circuit.
   - **Horn:** ESP32 outputs a HIGH signal to GPIO 23, sounding the Piezo Buzzer.

---

### Pipeline 2 Detailed Breakdown (Vision Loop)
1. **INPUT (Optics):** Environmental light enters the OV2640 lens on the ESP32-CAM module.
2. **PROCESSING (ESP32-CAM):** The ESP32-CAM onboard hardware JPEG compressor encodes raw image pixels into compressed JPEG frames at 640x480 resolution.
3. **OUTPUT (Video Stream):** The HTTP stream server packages frames into an MJPEG stream over HTTP port 81 (`http://192.168.4.1:81/stream`). The `SaverRover` app's MJPEG stream reader renders the stream in real time.
