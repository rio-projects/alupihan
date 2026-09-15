# Project Saver — Basic User Manual

**Document Version:** 1.0  
**Target Audience:** End-users, hobbyists, inspection operators.

---

## 1. Safety Precautions & Handling Rules

> [!WARNING]
> **High-Intensity Spotlight & Li-ion Battery Precautions**
> 1. **60W Spotlight Caution:** The auxiliary motorcycle spotlight emits intense white light. **Do not look directly into the beam** at close range to prevent visual injury. The spotlight housing can get warm during prolonged usage; ensure adequate airflow.
> 2. **18650 3S Battery Safety:**
>    - Charge the 18650 pack using the integrated Type-C charging port.
>    - Do not short-circuit battery terminals.
>    - Store the rover in a cool, dry place. If battery voltage drops below 9.6V (3.0V per cell), recharge immediately.

---

## 2. Pre-Flight Inspection Checklist

Before operating the rover:
- [ ] Inspect 4 wheels for loose debris or obstruction.
- [ ] Check pan-tilt camera bracket movement.
- [ ] Ensure 3S battery pack is fully charged (LED indicator green on charger).
- [ ] Verify LM2596s buck converter is outputting regulated 5.0V.
- [ ] Confirm antenna / ESP32-CAM module is securely mounted.

---

## 3. Step-by-Step Operating Guide

### Step 1: Powering On
Flip the main power switch located on the chassis side panel.
- The **Active Piezo Buzzer** will play a 2-tone boot chime.
- The status LED on the ESP32 Dev Board will light up solid blue/red.

### Step 2: Mobile App Connection
1. Turn on Wi-Fi on your mobile phone or tablet.
2. Select network `Saver_Rover` and enter password `rover1234`.
3. Launch the **Saver Mobile Control App**.
4. Press **CONNECT**.

### Step 3: Steering & Locomotion Controls
- **▲ FORWARD:** Drives all 4 wheels forward.
- **▼ REVERSE:** Drives all 4 wheels backward.
- **◀ LEFT:** Performs differential left turn (skid-steer).
- **RIGHT ▶:** Performs differential right turn.
- **STOP:** Immediately cuts power to TB6612FNG motor driver.
- **SPEED MODES:** Toggle between LOW (55%), MED (78%), and HIGH (100%) max output.

### Step 4: Camera Target & Servos
- Use the **TILT UP / DOWN** and **PAN LEFT / RIGHT** buttons to point the camera at areas of interest.
- Press **CENTER** to return camera to default forward angle (90° / 90°).
- Adjust **TOWERPRO METAL GEAR SERVO** buttons (0°, 90°, 180°) for auxiliary robotic arm operations.

### Step 5: Auxiliary Lighting & Alarm
- Tap **💡 60W SPOTLIGHT** to turn high-intensity light ON or OFF during low-light surveillance.
- Press and hold **🔊 ACTIVE BUZZER / HORN** for immediate audio alert.

---

## 4. Maintenance & Storage

- Clean camera lens gently with a microfiber cloth.
- Check motor wire solder joints periodically.
- Turn off main power switch when rover is idle.
