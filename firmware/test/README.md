# Project Alupihan — Component Testing Suite

This folder contains isolated, standalone unit test sketches for individual hardware components before full system integration.

---

## Test #1: ESP32-CAM Standalone Stream (`esp32_cam_test/`)

### Objective
Verify that the **ESP32-CAM** module initializes its camera sensor, creates a Wi-Fi Access Point, and serves a live MJPEG video stream.

### Location
[`firmware/test/esp32_cam_test/esp32_cam_test.ino`](file:///home/vincezamora/alupihan/firmware/test/esp32_cam_test/esp32_cam_test.ino)

### Instructions (USB Type-C Plug & Play)

1. **Hardware Connection:**
   - Plug the ESP32-CAM directly into your computer using a **USB Type-C cable**.

2. **Arduino IDE Settings:**
   - Open `esp32_cam_test.ino` in Arduino IDE.
   - Board: **AI Thinker ESP32-CAM** (or **ESP32 Wrover Module**)
   - CPU Frequency: **240MHz**
   - Flash Frequency: **80MHz**
   - PSRAM: **Enabled**
   - Port: Select your USB Serial COM port.
   - Click **Upload**.

3. **Execution & Verification:**
   - Open the **Serial Monitor** at **115200 Baud**.
   - On your phone or laptop, connect to the Wi-Fi AP:
     - **SSID:** `ESP32_CAM_TEST`
     - **Password:** `12345678`
   - Open a browser and navigate to `http://192.168.4.1`.
   - **Pass Criteria:** Live camera video stream renders cleanly.

---

## Test #2: ESP32 AP & WebSockets Communication Test (`esp32_ap_test/`)

### Objective
Verify that the **Main ESP32** initializes its Access Point (`Alupihan_Rover`), starts the WebSocket server on Port 8080, accepts connections from the `AlupihanRover` app / ESP32-CAM, and prints received commands directly to the Arduino Serial Monitor in real time.

### Location
[`firmware/test/esp32_ap_test/esp32_ap_test.ino`](file:///home/vincezamora/alupihan/firmware/test/esp32_ap_test/esp32_ap_test.ino)

### Instructions
1. Open `esp32_ap_test.ino` in **Arduino IDE**.
2. Select Board: **ESP32 Dev Module**, select Port, and click **Upload**.
3. Open **Serial Monitor** at **115200 Baud**.
4. Connect your phone's Wi-Fi to **`Alupihan_Rover`** (Password: `rover1234`).
5. Open the **AlupihanRover** app (or emulator) on your phone.
6. **Pass Criteria:** 
   - Serial Monitor prints `[WEBSOCKET] Client Connected`.
   - Dragging joystick, pressing Spotlight, Horn, or Pan-Tilt buttons prints formatted JSON command logs in real time.

