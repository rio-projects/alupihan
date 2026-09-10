# Project Alupihan — 4WD Environmental Surveillance Rover

**Project Name:** Alupihan  
**Assigned Personnel:** Sir Vince Zamora and Sir Heinrich Del Rosario  
**Purpose:** Lightweight 4WD remote-controlled rover capable of environmental surveillance via live video streaming over a local Wi-Fi network.  
**Target Users:** Hobbyists, robotics students, and remote inspection teams.

---

## 1. System Specifications & Features

- **Microcontrollers:** Dual ESP32 Setup (ESP32 Dev Board for Main Logic/WebSockets + ESP32-CAM for MJPEG Video Streaming)
- **Locomotion:** 4WD Skid-Steer Chassis powered by 4x TT Motors driven by **TB6612FNG Dual MOSFET Motor Driver**
- **Camera Target System:** 2-Axis Pan-Tilt Bracket with 2x Micro Servos + 1x **TowerPro High Torque Metal Gear Servo**
- **Illumination & Alert System:** Relay-switched 60W Motorcycle Spotlight + Active Piezo Buzzer Horn
- **Power Management:** 3S 18650 Li-ion Battery Pack (11.1V–12.6V) with 3S BMS, Type-C step-up charger, and **LM2596s Buck Converter** (Regulated 5.0V Output)
- **Remote Control App:** Native Android Application (Kotlin + Jetpack Compose)

---

## 2. Directory & Repository Structure

```
alupihan/
├── firmware/
│   ├── esp32_main/           # Arduino IDE Sketch for Main ESP32 Logic & WebSockets
│   │   ├── esp32_main.ino
│   │   └── config.h
│   └── esp32_cam/            # Arduino IDE Sketch for ESP32-CAM MJPEG Streaming
│       └── esp32_cam.ino
├── AlupihanRover/            # Native Android Mobile Control Application (Kotlin + Jetpack Compose)
│   ├── app/                  # Application module containing Kotlin UI & Network sources
│   └── build.gradle.kts      # Gradle build configuration
└── docs/                     # Complete Dissemination Package (Section 5 Items A-K)
    ├── README.md             # Essential setup & installation instructions (This file)
    ├── USER_MANUAL.md        # Practical end-user guide & safety precautions
    ├── WIRING_DIAGRAM.md     # Wiring and schematic pinout specifications
    ├── ELECTRICAL_EXPLANATION.md # Comprehensive electrical rationale & power distribution
    ├── SOURCE_CODE_EXPLANATION.md # Deep-dive breakdown of firmware & Android app logic
    ├── RADD_RESEARCH.md      # Kinematics, battery runtime, & engineering calculations
    ├── SYSTEM_OVERVIEW.md    # Whole-system input/processing/output workflow
    └── TESTING_AND_VALIDATION.md # Unit, integration, and functional test suite
```

---

## 3. Installation & Setup Instructions

### A. ESP32 Main Firmware (Arduino IDE)
1. Open **Arduino IDE** (v2.0 or higher).
2. Go to **File -> Preferences** (or `Ctrl + ,`), and paste the following official Espressif board manager URL into **Additional Boards Manager URLs**:
   ```text
   https://espressif.github.io/arduino-esp32/package_esp32_index.json
   ```
   *(Legacy URL fallback: `https://dl.espressif.com/dl/package_esp32_index.json`)*
3. Go to **Tools -> Board -> Boards Manager...**, search for **esp32** by Espressif Systems, and click **Install**.
4. Install required libraries via **Tools -> Manage Libraries...**:
   - `ESP32Servo` (by Kevin Harrington)
   - `WebSockets` (by Markus Sattler)
   - `ArduinoJson` (v6.x by Benoit Blanchon)
5. Open `firmware/esp32_main/esp32_main.ino`.
6. Select Board: **ESP32 Dev Module**, select your COM/TTY Serial Port, and click **Upload** (→).

### B. ESP32-CAM Firmware (Arduino IDE)
1. Open `firmware/esp32_cam/esp32_cam.ino`.
2. Select Board: **AI Thinker ESP32-CAM**.
3. Set PSRAM: **Enabled** (if available) or Disabled.
4. Connect FTDI Programmer (TX->RX, RX->TX, GPIO 0 to GND during boot).
5. Click Upload and reset board.

### C. Mobile Application (AlupihanRover)
1. Open **Android Studio** (2024.2+ or Android Studio Ladybug recommended).
2. Select **Open** and navigate to the `AlupihanRover/` directory.
3. Allow Gradle to finish sync.
4. Connect an Android phone (with USB Debugging enabled) or start an Android Emulator.
5. Click **Run 'app'** or run `./gradlew assembleDebug` inside `AlupihanRover/`.

---

## 4. Basic Operation & Wi-Fi Connection

1. Power on the rover using the main switch on the 3S battery enclosure.
2. Wait for the startup chime from the Piezo Buzzer.
3. On your mobile phone, connect to the Wi-Fi Access Point:
   - **SSID:** `Alupihan_Rover`
   - **Password:** `rover1234`
4. Open the **Alupihan Rover App**.
5. Tap **CONNECT**. The live camera feed and controls will activate.

---

## 5. Troubleshooting Guide

| Issue | Potential Cause | Solution |
| :--- | :--- | :--- |
| **No Camera Feed** | ESP32-CAM not powered or wrong IP | Verify 5V power from LM2596s. Confirm stream URL (`http://192.168.4.1:81/stream`). |
| **Motors Not Turning** | TB6612FNG `STBY` pin LOW or 12V battery depleted | Check 3S battery voltage (>10.5V). Verify `STBY` pin (GPIO 33) is HIGH. |
| **ESP32 Reboots during Servo Movement** | High current draw on 5V rail | Ensure Servos receive power from LM2596s Buck 5V rail, NOT ESP32 3.3V pin. |
| **Spotlight Not Turning On** | Relay module wiring or reverse logic | Check Relay IN signal at GPIO 22. Confirm COM/NO connections to 12V rail. |
