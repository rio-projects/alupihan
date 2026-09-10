# Project Alupihan — Engineering Research & Design Calculations

**Document Version:** 1.0  
**Section:** 5.G (RADD Research / Study Documentation)

---

## 1. Skid-Steer Locomotion Kinematics

Rover **Alupihan** uses a **4WD Skid-Steer Differential Drive** mechanism. Unlike Ackerman steering (found in standard automobiles with front-wheel turning knuckles), skid-steer rovers rotate by driving left and right wheel sets at different relative speeds and directions.

### Mathematical Kinematics Model
Let:
- $v_L$ = Linear velocity of Left wheels
- $v_R$ = Linear velocity of Right wheels
- $W$ = Track width of the rover (distance between left and right wheel centers = 0.18 m)
- $R$ = Wheel radius (0.033 m)
- $v$ = Rover linear velocity along forward axis
- $\omega$ = Rover angular velocity (rotation rate around center of mass)

The forward kinematics equations are:
$$v = \frac{v_R + v_L}{2}$$
$$\omega = \frac{v_R - v_L}{W}$$

### Motion Modes & Control Signals

| Desired Motion | Left Motors Speed ($v_L$) | Right Motors Speed ($v_R$) | Angular Velocity ($\omega$) |
| :--- | :--- | :--- | :--- |
| **Straight Forward** | $+V_{max}$ | $+V_{max}$ | $0$ (Pure translation) |
| **Straight Reverse** | $-V_{max}$ | $-V_{max}$ | $0$ (Pure translation) |
| **Pivot Turn Left** | $-V_{max}$ | $+V_{max}$ | $+\frac{2 V_{max}}{W}$ (Fast rotation on point) |
| **Pivot Turn Right** | $+V_{max}$ | $-V_{max}$ | $-\frac{2 V_{max}}{W}$ (Fast rotation on point) |
| **Smooth Left Curve**| $+0.5 V_{max}$ | $+V_{max}$ | $+\frac{0.5 V_{max}}{W}$ |

---

## 2. Power Subsystem Calculations & Battery Runtime

### A. Electrical Load Analysis

| Component | Nominal Operating Voltage | Max Current Draw | Average Operating Current |
| :--- | :--- | :--- | :--- |
| **4x TT Motors (Loaded)** | 12.0V (TB6612FNG VM) | 1200 mA (stall) | 400 mA |
| **ESP32 Dev Board** | 5.0V (Buck Output) | 240 mA (Wi-Fi Tx) | 120 mA |
| **ESP32-CAM Module** | 5.0V (Buck Output) | 310 mA (Stream + Flash)| 180 mA |
| **2x Pan-Tilt Micro Servos**| 5.0V (Buck Output) | 500 mA (moving) | 100 mA |
| **TowerPro Metal Servo** | 5.0V (Buck Output) | 1200 mA (peak load)| 250 mA |
| **60W Motorcycle Spotlight**| 12.0V (Relay Switched) | 4800 mA | 4800 mA (When ON) |
| **Active Piezo & Relay Coil**| 5.0V (Buck Output) | 80 mA | 15 mA |
| **Total (Spotlight OFF)** | — | **~3.53 A Peak** | **~1.065 A Avg** |
| **Total (Spotlight ON)** | — | **~8.33 A Peak** | **~5.865 A Avg** |

---

### B. 3S 18650 Li-ion Battery Runtime Calculation

The power pack consists of **3x 18650 Li-ion cells in series (3S1P configuration)**:
- Nominal Voltage ($V_{nom}$): $3.7\text{V} \times 3 = 11.1\text{V}$
- Fully Charged Voltage ($V_{max}$): $4.2\text{V} \times 3 = 12.6\text{V}$
- Single Cell Capacity: $2600\text{ mAh} = 2.6\text{ Ah}$
- Total Pack Energy Capacity ($E_{pack}$):
$$E_{pack} = 11.1\text{ V} \times 2.6\text{ Ah} = 28.86\text{ Watt-hours (Wh)}$$

#### Mode 1: Standard Surveillance (Motors + Servos + Camera + WebSockets, Spotlight OFF)
- Average Current Draw from 12V rail ($I_{avg1}$): $\approx 1.065\text{ A}$
- Estimated Runtime ($T_1$):
$$T_1 = \frac{2.6\text{ Ah}}{1.065\text{ A}} \approx 2.44\text{ hours } (\approx 146\text{ minutes})$$

#### Mode 2: Full Night Surveillance (All Systems + 60W Spotlight Active ON)
- Average Current Draw from 12V rail ($I_{avg2}$): $\approx 5.865\text{ A}$
- Estimated Runtime ($T_2$):
$$T_2 = \frac{2.6\text{ Ah}}{5.865\text{ A}} \approx 0.44\text{ hours } (\approx 26.6\text{ minutes})$$

---

## 3. LM2596s Buck Converter Thermal Dissipation Calculation

The LM2596s step-down converter powers the 5V logic and servo bus.
- Max Input Voltage ($V_{in}$): 12.6 V
- Output Voltage ($V_{out}$): 5.0 V
- Peak 5V Current Draw ($I_{out}$): 1.5 A
- Conversion Efficiency ($\eta$): ~82% at $12\text{V} \rightarrow 5\text{V}$

Power lost as heat ($P_{loss}$):
$$P_{out} = V_{out} \times I_{out} = 5.0\text{ V} \times 1.5\text{ A} = 7.5\text{ W}$$
$$P_{in} = \frac{P_{out}}{\eta} = \frac{7.5\text{ W}}{0.82} = 9.15\text{ W}$$
$$P_{loss} = P_{in} - P_{out} = 9.15\text{ W} - 7.5\text{ W} = 1.65\text{ W}$$

With a heat dissipation of 1.65 W, the LM2596s onboard heatsink operates comfortably below its maximum junction temperature limit ($T_J < 125^\circ\text{C}$), ensuring long-term hardware stability without forced fan cooling.
