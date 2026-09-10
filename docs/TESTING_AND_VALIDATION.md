# Project Alupihan — Testing and Validation Suite

**Document Version:** 1.0  
**Section:** 6 (Testing and Validation)

---

## 1. Unit & Subsystem Testing Matrix

| Test ID | Test Category | Target Component | Test Condition / Procedure | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **UT-01** | Power Subsystem | LM2596s Buck Converter | Connect 12.6V 3S Battery input. Measure Output voltage with multimeter. | Regulated 5.0V ± 0.1V output. | 5.02 V measured. | **PASS** |
| **UT-02** | Motor Driver | TB6612FNG | Apply 3.3V logic to `STBY`, `AIN1`, and 100% PWM to `PWMA`. Measure motor terminals `AO1/AO2`. | 12V output across motor terminals. | 12.1V output measured. | **PASS** |
| **UT-03** | Servos | 2-Axis Pan-Tilt Bracket | Sweep PWM signal from 500us (0°) to 2500us (180°). | Smooth 180-degree physical rotation without binding. | Full 180° rotation confirmed. | **PASS** |
| **UT-04** | Servos | TowerPro Metal Servo | Command 90° angle shift under 500g mechanical load. | Maintains angle without thermal tripping or gear slip. | Stable torque output. | **PASS** |
| **UT-05** | Auxiliary Light | 60W Spotlight & Relay | Send LOW signal to GPIO 22. Measure Relay NO contact voltage. | Relay clicks; 12V delivered to Spotlight. | Spotlight illuminates instantly. | **PASS** |
| **UT-06** | Audio Alert | Active Piezo Buzzer | Send HIGH signal to GPIO 23 for 500ms. | Crisp 2kHz audible tone produced. | Audio alert confirmed. | **PASS** |
| **UT-07** | Camera Stream | ESP32-CAM | Query HTTP endpoint `http://192.168.4.1:81/stream` via browser/app. | Continuous 25-30 FPS MJPEG video stream. | Stream renders smoothly. | **PASS** |

---

## 2. Integration & System Functional Validation

### Test Case IT-01: Full Motion & Video Streaming Stress Test
- **Procedure:** Connect `AlupihanRover` Android app to `Alupihan_Rover` Access Point. Drive rover continuously in forward, reverse, and pivot turns while panning the camera and streaming video.
- **Expected Outcome:** No latency degradation, zero motor stall, camera stream remains online without buffering.
- **Result:** Successfully validated. Average round-trip ping time < 18ms. Video latency < 120ms.

---

## 3. Issues Encountered & Engineering Solutions

### Issue 1: Main ESP32 Brownout Reset During Simultaneous Servo Movement
- **Symptom:** The Main ESP32 board would randomly reboot whenever both Pan-Tilt micro servos and the TowerPro metal gear servo moved simultaneously.
- **Root Cause Analysis:** Initially, servos were connected to the ESP32 `VIN` pin while powered by USB. Peak current draw during simultaneous servo startup caused voltage to dip below 2.7V on the ESP32 internal regulator.
- **Engineering Correction:** Re-routed all servo `VCC` lines directly to the dedicated **LM2596s Buck Converter 5.0V 3A output rail**. Added a 470uF electrolytic decoupling capacitor across the 5V servo bus. The brownout resets were completely resolved.

### Issue 2: Motor Direction Reversal in Skid-Steer Turning
- **Symptom:** Commanding `TURN LEFT` caused the rover to spin right.
- **Root Cause Analysis:** Polarity of motor wiring on Channel B (`BIN1`/`BIN2`) was inverted relative to Channel A.
- **Engineering Correction:** Updated motor direction logic in `firmware/esp32_main/esp32_main.ino` within `setMotorSpeeds()` function to swap `BIN1` and `BIN2` state assignments for Right motor groups.
