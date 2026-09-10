# Project Alupihan — Wiring & Schematic Specifications

This document defines the complete electrical connections, pin assignments, and power distribution topology used in the functional prototype of **Project Alupihan**.

---

## 1. System Electrical Architecture & Power Rails

```
                      +-----------------------------+
                      | 3S 18650 Li-ion Battery Pack |
                      |   (11.1V Nom / 12.6V Max)   |
                      +--------------+--------------+
                                     |
             +-----------------------+-----------------------+
             |                                               |
  (12V High-Current Rail)                        (12V High-Current Rail)
             |                                               |
             v                                               v
+------------------------+                     +---------------------------+
| TB6612FNG Motor Driver |                     | 60W Spotlight Relay (COM) |
|   (VM Motor Power Pin) |                     +-------------+-------------+
+------------------------+                                   |
                                                  (Switched 12V Output)
                                                             |
                                                             v
                                                   +-------------------+
                                                   | 60W Motorcycle    |
                                                   | Spotlight         |
                                                   +-------------------+
             |
             +-----------------------+
                                     |
                                     v
                        +--------------------------+
                        | LM2596s Buck Converter   |
                        |   (Step-Down to 5.0V)    |
                        +------------+-------------+
                                     |
                           (5.0V Regulated Rail)
                                     |
    +-------------------+------------+------------+--------------------+
    |                   |                         |                    |
    v                   v                         v                    v
+-------+      +-----------------+       +-----------------+  +-----------------+
| ESP32 |      | ESP32-CAM (5V)  |       | Servos VCC (x3) |  | Relay VCC &     |
| 5V/VIN|      |                 |       | (Pan, Tilt,     |  | Piezo Buzzer    |
+-------+      +-----------------+       | TowerPro)       |  +-----------------+
                                         +-----------------+
```

---

## 2. Complete Microcontroller Pinout Matrix

### A. ESP32 Main Development Board

| ESP32 Pin | Connected Component | Signal / Parameter | Logic Level |
| :--- | :--- | :--- | :--- |
| **GPIO 26** | TB6612FNG `AIN1` | Left Motors Direction 1 | 3.3V Digital Output |
| **GPIO 27** | TB6612FNG `AIN2` | Left Motors Direction 2 | 3.3V Digital Output |
| **GPIO 14** | TB6612FNG `PWMA` | Left Motors Speed PWM (LEDC Ch 0) | 3.3V PWM Output (5kHz) |
| **GPIO 12** | TB6612FNG `BIN1` | Right Motors Direction 1 | 3.3V Digital Output |
| **GPIO 13** | TB6612FNG `BIN2` | Right Motors Direction 2 | 3.3V Digital Output |
| **GPIO 25** | TB6612FNG `PWMB` | Right Motors Speed PWM (LEDC Ch 1) | 3.3V PWM Output (5kHz) |
| **GPIO 33** | TB6612FNG `STBY` | Motor Driver Enable | 3.3V Digital Output (HIGH) |
| **GPIO 18** | Pan Servo | Pan Axis Signal (0 - 180°) | 3.3V PWM Output (50Hz) |
| **GPIO 19** | Tilt Servo | Tilt Axis Signal (0 - 180°) | 3.3V PWM Output (50Hz) |
| **GPIO 21** | TowerPro Metal Servo | Heavy Duty Actuator | 3.3V PWM Output (50Hz) |
| **GPIO 22** | Relay Module | 60W Spotlight Signal | 3.3V Digital Output (Active LOW) |
| **GPIO 23** | Active Piezo Buzzer | Horn / Alarm Output | 3.3V Digital Output |
| **5V / VIN**| LM2596s Buck Output | Main Board Power Input | 5.0V DC Supply |
| **GND** | System Common Ground | Electrical Reference Ground | 0V Ground |

---

### B. ESP32-CAM Video Streamer

| ESP32-CAM Pin | Connected Component | Purpose |
| :--- | :--- | :--- |
| **5V** | LM2596s Buck Output | Camera Logic & Wi-Fi Power |
| **GND** | System Common Ground | Electrical Reference Ground |
| **GPIO 32 - 22** | OV2640 Sensor Pins | Parallel Video Interface |

---

### C. TB6612FNG Motor Driver Terminal Connections

| Driver Pin | Wiring Connection | Voltage Level |
| :--- | :--- | :--- |
| **VM** | 3S 18650 Battery Rail (+) | 11.1V - 12.6V DC |
| **VCC** | LM2596s Buck Output (+) | 5.0V DC |
| **GND** | Common System Ground | 0V Ground |
| **AO1 / AO2**| Left Motors Group (2x TT Motors in Parallel) | Motor Output A |
| **BO1 / BO2**| Right Motors Group (2x TT Motors in Parallel) | Motor Output B |
| **STBY** | ESP32 GPIO 33 | 3.3V Control |

---

### D. Power Subsystem Terminals

| Module | Input Wiring | Output Wiring |
| :--- | :--- | :--- |
| **3S 18650 Pack** | Type-C Step-Up Charger | 11.1V–12.6V B+ / B- to 3S BMS |
| **LM2596s Buck** | 11.1V–12.6V from BMS Out | Regulated 5.0V DC to Logic Bus |
| **Relay Module** | 5V Logic VCC, GND, GPIO 22 IN | Switched 12V COM -> NO to Spotlight (+) |
