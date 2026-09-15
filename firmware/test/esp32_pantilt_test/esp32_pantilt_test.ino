/*
 * ============================================================================
 * PROJECT SAVER — STANDALONE 2-AXIS PAN-TILT SERVO TEST SKETCH
 * Author: Sir Vince Zamora & Sir Heinrich Del Rosario
 * Target Board: ESP32-WROOM-32 DevKit / NodeMCU
 * File Path: firmware/test/esp32_pantilt_test/esp32_pantilt_test.ino
 *
 * Hardware Wiring:
 *   - Pan Servo (Horizontal):  Signal -> GPIO 18 (D18) | VCC -> 5V Rail | GND -> GND
 *   - Tilt Servo (Vertical):    Signal -> GPIO 19 (D19) | VCC -> 5V Rail | GND -> GND
 *
 * Controls:
 *   1. Android App: Move right joystick to command Pan & Tilt via WebSockets.
 *   2. Serial Monitor (115200 baud):
 *      - Type 'S' or 'sweep' : Run automatic 0° - 180° sweep test on both servos.
 *      - Type 'C' or 'center': Reset both servos to center (90°, 90°).
 *      - Type 'P90'          : Set Pan angle to 90°.
 *      - Type 'T45'          : Set Tilt angle to 45°.
 * ============================================================================
 */

#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ----------------------------------------------------------------------------
// WI-FI & PIN CONFIGURATION
// ----------------------------------------------------------------------------
#define USE_STATION_MODE  true  // true = Home Wi-Fi | false = AP Hotspot Mode (192.168.4.1)

const char* STATION_SSID = "HUAWEI-PBZM";
const char* STATION_PASS = "JfvyqXrK";

const char* AP_SSID      = "Saver_Rover";
const char* AP_PASS      = "rover1234";

#define PAN_SERVO_PIN   18  // GPIO 18 (D18) Horizontal Pan Servo Signal
#define TILT_SERVO_PIN  19  // GPIO 19 (D19) Vertical Tilt Servo Signal

#define SERVO_MIN_US    500   // 500us standard minimum pulse
#define SERVO_MAX_US    2400  // 2400us standard maximum pulse

// Objects & Global State
Servo servoPan;
Servo servoTilt;
WebSocketsServer webSocket = WebSocketsServer(8080);

float targetPanAngle   = 90;
float targetTiltAngle  = 0;
float currentPanAngle  = 90; // Default Pan 90° (Centered Left/Right)
float currentTiltAngle = 0;  // Default Tilt 0°

// Trim Offsets (Adjust these degrees if physical servo horn is slightly off-center!)
int panTrimOffset  = 0; // 0 = Default full 180° range
int tiltTrimOffset = 0;

// Speed & Inversion Settings
float maxServoStepDeg = 0.3f;        // 0.3 = Ultra Smooth & Slow (15°/sec)
bool invertPanDirection = true;   // Invert pan axis so swiping left turns camera left
bool invertTiltDirection = false; // False: 0° target outputs 0° signal (Standing position)
unsigned long lastServoTick = 0;

// ----------------------------------------------------------------------------
// FUNCTION PROTOTYPES
// ----------------------------------------------------------------------------
void setGimbalAngles(float pan, float tilt);
void updateSmoothServos();
void runAutoSweepTest();
void setupWiFi();
void processWebSocketCommand(uint8_t * payload, size_t length);
void checkSerialInput();

// ----------------------------------------------------------------------------
// GIMBAL POSITION CONTROLLER
// ----------------------------------------------------------------------------
void setGimbalAngles(float pan, float tilt) {
  targetPanAngle  = constrain(pan, 0.0f, 180.0f);
  targetTiltAngle = constrain(tilt, 10.0f, 170.0f);
  Serial.printf("📷 [PAN-TILT TARGET] Pan: %.1f° | Tilt: %.1f°\n", targetPanAngle, targetTiltAngle);
}

void updateSmoothServos() {
  if (millis() - lastServoTick >= 20) { // 50 Hz smooth update loop
    lastServoTick = millis();

    // Smooth Pan
    if (currentPanAngle < targetPanAngle) {
      currentPanAngle = min(currentPanAngle + maxServoStepDeg, targetPanAngle);
    } else if (currentPanAngle > targetPanAngle) {
      currentPanAngle = max(currentPanAngle - maxServoStepDeg, targetPanAngle);
    }
    int rawPanInt = (int)round(currentPanAngle);
    int effPan = invertPanDirection ? (180 - rawPanInt) : rawPanInt;
    servoPan.write(constrain(effPan + panTrimOffset, 0, 180));

    // Smooth Tilt
    if (currentTiltAngle < targetTiltAngle) {
      currentTiltAngle = min(currentTiltAngle + maxServoStepDeg, targetTiltAngle);
    } else if (currentTiltAngle > targetTiltAngle) {
      currentTiltAngle = max(currentTiltAngle - maxServoStepDeg, targetTiltAngle);
    }
    int rawTiltInt = (int)round(currentTiltAngle);
    int effTilt = invertTiltDirection ? (180 - rawTiltInt) : rawTiltInt;
    servoTilt.write(constrain(effTilt + tiltTrimOffset, 0, 180));
  }
}

// ----------------------------------------------------------------------------
// AUTO SWEEP DIAGNOSTIC TEST
// ----------------------------------------------------------------------------
void runAutoSweepTest() {
  Serial.println("\n🔄 >>> STARTING AUTOMATIC PAN & TILT SWEEP TEST <<<");
  
  // 1. Pan Sweep (0 -> 180)
  Serial.println(">> Sweeping Pan Servo (GPIO 18)...");
  for (int angle = 0; angle <= 180; angle += 15) {
    setGimbalAngles(angle, currentTiltAngle);
    delay(100);
  }

  // 2. Tilt Sweep (0 -> 180)
  Serial.println(">> Sweeping Tilt Servo (GPIO 19)...");
  for (int angle = 0; angle <= 180; angle += 15) {
    setGimbalAngles(currentPanAngle, angle);
    delay(100);
  }

  // Return to default position (Pan: 90°, Tilt: 0°)
  setGimbalAngles(90, 0);

  Serial.println("✅ >>> SWEEP TEST COMPLETE. RETURNED TO DEFAULT POSITION (Pan: 90°, Tilt: 0°) <<<\n");
}

// ----------------------------------------------------------------------------
// WEBSOCKET EVENT HANDLER
// ----------------------------------------------------------------------------
void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("[WEBSOCKET] ❌ Client #%u Disconnected\n", num);
      break;

    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("\n[WEBSOCKET] ✅ Client #%u Connected from %d.%d.%d.%d\n",
                      num, ip[0], ip[1], ip[2], ip[3]);

        StaticJsonDocument<128> doc;
        doc["status"] = "connected";
        doc["subsystem"] = "pantilt_test";
        String resp;
        serializeJson(doc, resp);
        webSocket.sendTXT(num, resp);
      }
      break;

    case WStype_TEXT:
      processWebSocketCommand(payload, length);
      break;

    default:
      break;
  }
}

void processWebSocketCommand(uint8_t * payload, size_t length) {
  StaticJsonDocument<256> doc;
  DeserializationError err = deserializeJson(doc, payload, length);
  if (err) {
    Serial.print("!! [JSON ERROR] ");
    Serial.println(err.c_str());
    return;
  }

  const char* cmdType = doc["type"] | "";
  if (strcmp(cmdType, "pantilt") == 0) {
    float pan = currentPanAngle;
    float tilt = currentTiltAngle;

    if (!doc["pan"].isNull()) {
      pan = doc["pan"].as<float>();
    }
    if (!doc["tilt"].isNull()) {
      tilt = doc["tilt"].as<float>();
    }
    setGimbalAngles(pan, tilt);
  }
}

// ----------------------------------------------------------------------------
// SERIAL MONITOR INTERACTIVE COMMAND PARSER
// ----------------------------------------------------------------------------
void checkSerialInput() {
  if (!Serial.available()) return;

  String input = Serial.readStringUntil('\n');
  input.trim();
  if (input.length() == 0) return;

  if (input.equalsIgnoreCase("S") || input.equalsIgnoreCase("SWEEP")) {
    runAutoSweepTest();
  } else if (input.equalsIgnoreCase("C") || input.equalsIgnoreCase("CENTER")) {
    setGimbalAngles(90, 90);
  } else if (input.startsWith("OFFSET") || input.startsWith("offset") || input.startsWith("TRIM") || input.startsWith("trim")) {
    int val = input.substring(input.indexOf(' ') + 1).toInt();
    panTrimOffset = val;
    Serial.printf("🎯 [PAN TRIM UPDATED] Pan Trim Offset set to: %d°\n", panTrimOffset);
    setGimbalAngles(currentPanAngle, currentTiltAngle);
  } else if (input.startsWith("P") || input.startsWith("p")) {
    int val = input.substring(1).toInt();
    setGimbalAngles(val, currentTiltAngle);
  } else if (input.startsWith("T") || input.startsWith("t")) {
    int val = input.substring(1).toInt();
    setGimbalAngles(currentPanAngle, val);
  } else {
    Serial.println("❓ [UNKNOWN COMMAND] Available Serial Commands:");
    Serial.println("   'S' or 'SWEEP'  -> Run 0°-180° auto sweep test");
    Serial.println("   'C' or 'CENTER' -> Reset Pan & Tilt to 90° center");
    Serial.println("   'P90'           -> Set Pan angle to 90 degrees");
    Serial.println("   'T45'           -> Set Tilt angle to 45 degrees");
    Serial.println("   'TRIM +5'       -> Adjust Pan trim offset by +5°");
    Serial.println("   'TRIM -5'       -> Adjust Pan trim offset by -5°");
  }
}

// ----------------------------------------------------------------------------
// SETUP
// ----------------------------------------------------------------------------
void setup() {
  // Disable ESP32 Brownout Detector to prevent power-dip resets during Wi-Fi & servo current spikes
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("  PROJECT SAVER — PAN & TILT SERVO TEST SKETCH");
  Serial.println("=======================================================");
  Serial.println("  Pan Pin : GPIO 18 (D18)");
  Serial.println("  Tilt Pin: GPIO 19 (D19)");
  Serial.println("=======================================================");

  // Allocate LEDC PWM Timers for ESP32Servo
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);

  servoPan.setPeriodHertz(50);
  servoTilt.setPeriodHertz(50);

  servoPan.attach(PAN_SERVO_PIN, SERVO_MIN_US, SERVO_MAX_US);
  servoTilt.attach(TILT_SERVO_PIN, SERVO_MIN_US, SERVO_MAX_US);

  // Set default center position (90°, 90°)
  setGimbalAngles(90, 90);

  // Wi-Fi Setup
  setupWiFi();

  // Start WebSocket Server
  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  Serial.println(">> [WEBSOCKET] Listening on Port 8080");

  // Run initial auto sweep test
  runAutoSweepTest();
}

// ----------------------------------------------------------------------------
// WI-FI SETUP
// ----------------------------------------------------------------------------
void setupWiFi() {
  if (USE_STATION_MODE) {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);
    WiFi.begin(STATION_SSID, STATION_PASS);
    Serial.printf(">> [WI-FI STA] Connecting to %s ...\n", STATION_SSID);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
      delay(500);
      Serial.print(".");
      attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\n>> [WI-FI STA] CONNECTED!");
      Serial.print("   IP Address: "); Serial.println(WiFi.localIP());
      Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.localIP()); Serial.println(":8080");
      return;
    }
    Serial.println("\n>> [WI-FI STA] Failed to connect to Home Wi-Fi. Falling back to AP Mode!");
  }

  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial.println(">> [WI-FI AP] Access Point Started!");
  Serial.print("   IP Address: "); Serial.println(WiFi.softAPIP());
  Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.softAPIP()); Serial.println(":8080");
}

// ----------------------------------------------------------------------------
// MAIN LOOP
// ----------------------------------------------------------------------------
void loop() {
  webSocket.loop();
  updateSmoothServos();
  checkSerialInput();
}
