# Project Alupihan — Electrical Architecture & Rationale

**Document Version:** 1.0  
**Section:** 5.D (Comprehensive Wiring & Schematic Explanation)

---

## 1. System Electrical Overview & Signal Flow

The electrical architecture of **Project Alupihan** is engineered for high performance, electrical noise isolation, and stable multi-actuator operation.

### Dual Electrical Rail Topology
Operating high-power inductive loads (4x TT Gear Motors), high-torque servos, and a 60W motorcycle spotlight alongside sensitive 3.3V digital microcontrollers requires strict power isolation to avoid voltage sag, electromagnetic interference (EMI), and unexpected microcontroller resets (brownout loops).

To eliminate these failure modes, the system divides power into **two isolated power buses**:

1. **12V Unregulated High-Current Bus (3S Li-ion Battery Pack ~11.1V–12.6V):**
   - Directly powers the **TB6612FNG `VM` motor power pin** to deliver maximum voltage and torque to the 4WD TT gear motors.
   - Supplies the high-current input rail for the **60W Spotlight** via the Relay switch contacts (`COM` to `NO`).

2. **5.0V Regulated Logic & Servo Bus (LM2596s Switching Buck Converter):**
   - The LM2596s step-down switching regulator drops the 12V battery voltage to a steady, ripple-filtered 5.0V output capable of supplying up to 3A continuous current.
   - Powers the **ESP32 Dev Board `5V/VIN` pin** (internal 3.3V linear regulator steps this down to 3.3V logic).
   - Powers the **ESP32-CAM `5V` input pin** to supply the camera sensor and high peak Wi-Fi radio transmissions.
   - Powers the **Servos `VCC` rail** (Pan, Tilt, and TowerPro servos). Servos draw brief current surges during movement; powering them from the LM2596s buck rail prevents these surges from causing ESP32 logic brownouts.
   - Powers the Optocoupler Relay coil and Active Piezo Alarm.

---

## 2. Component Selection Rationale

### A. TB6612FNG Dual H-Bridge Motor Driver vs. Legacy L298N
- **MOSFET vs. BJT Transistors:** The traditional L298N driver uses bipolar junction transistors (BJTs), which exhibit a high voltage drop across internal switches (~1.8V to 2.5V loss). This wastes battery energy as heat and reduces motor speed.
- The **TB6612FNG** uses low ON-resistance MOSFET switches with negligible voltage drop (~0.2V loss), achieving over 90% power efficiency, zero heatsink requirement, smaller physical footprint, and higher continuous current capacity (1.2A per channel, 3.2A peak).
- Integrated thermal shutdown and low-voltage detect protections safeguard the driver under heavy stalled motor conditions.

### B. Dual ESP32 Setup (ESP32 Dev Board + ESP32-CAM)
- **Task Separation & Hardware Parallelism:** Video encoding and live MJPEG HTTP streaming on ESP32 microcontrollers are computationally intensive and require near 100% CPU core utilization.
- Running video streaming, WebSockets server, 4WD motor PWM drivers, 3x Servo PWM generation, and relay toggles on a single ESP32 causes frame drops, motor stuttering, and command latency spikes.
- By assigning **ESP32-CAM exclusively to video streaming** and **Main ESP32 exclusively to actuation and command processing**, both tasks execute smoothly without thread starvation.

### C. Relay Module & 60W Motorcycle Spotlight Protection
- A 60W spotlight draws ~5A continuous current at 12V. ESP32 GPIO pins can only source/sink a maximum of 12mA to 40mA at 3.3V.
- An **optocoupled relay module** provides full galvanic isolation between the low-voltage 3.3V ESP32 GPIO pin and the high-power 12V spotlight circuit.
- The internal flyback diode on the relay module suppresses inductive voltage spikes caused by relay coil de-energization.

### D. Active Piezo Buzzer
- An active piezo buzzer includes an internal oscillating circuit. Applying a simple 3.3V HIGH signal from ESP32 GPIO 23 triggers a crisp 2kHz audio tone without requiring continuous CPU PWM generation, saving micro-controller timer resources.

---

## 3. Grounding & Noise Suppression

- **Common Ground Connection:** The GND pins of the ESP32 Main Board, ESP32-CAM, TB6612FNG, LM2596s Buck Converter, Relay Module, and 3S Battery BMS are tied together at a central star-ground point.
- This prevents ground loops and ensures all control signals (`AIN1`, `PWMA`, Servo PWM, Relay IN) share an accurate 0V voltage reference.
