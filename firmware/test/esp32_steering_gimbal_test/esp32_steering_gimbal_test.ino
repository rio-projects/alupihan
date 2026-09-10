/*
 * ============================================================================
 * Project Alupihan — Subsystem Test: 4WD Differential Wheel Drive (Skid-Steer)
 * Target Board: Main ESP32 (NodeMCU / ESP32 WROOM-32)
 *
 * Hardware Pin Mapping:
 *   - Camera Pan Servo (Optional): GPIO 18
 *   - Camera Tilt Servo (Optional): GPIO 19
 *   - Spotlight Relay:             GPIO 26
 *
 * Motor Driver (TB6612FNG / L298N):
 *   - Left Motors Speed (PWMA):   GPIO 14
 *   - Left Motors Dir 1 (AIN1):   GPIO 32
 *   - Left Motors Dir 2 (AIN2):   GPIO 27
 *   - Right Motors Speed (PWMB):  GPIO 25
 *   - Right Motors Dir 1 (BIN1):  GPIO 12
 *   - Right Motors Dir 2 (BIN2):  GPIO 13
 *   - Standby Enable (STBY):      GPIO 33 (HIGH = Active)
 *
 * Drive Logic (Skid-Steer / Tank Steering):
 *   - STOP (Default State): Left = 0, Right = 0
 *   - FORWARD:  Left > 0 (Forward), Right > 0 (Forward)
 *   - BACKWARD: Left < 0 (Reverse), Right < 0 (Reverse)
 *   - TURN RIGHT: Left > 0 (Forward), Right < 0 (Reverse)
 *   - TURN LEFT:  Left < 0 (Reverse), Right > 0 (Forward)
 * ============================================================================
 */

#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ----------------------------------------------------------------------------
// 1. WI-FI & NETWORK CONFIGURATION
// ----------------------------------------------------------------------------
#define USE_STATION_MODE true  // true = Home Wi-Fi | false = AP Mode (192.168.4.1)
#define USE_STATIC_IP   true  // true = Fixed Static IP for Home Network

const char* STATION_SSID = "HUAWEI-PBZM";
const char* STATION_PASS = "JfvyqXrK";

// Static IP for Home Network (Always 192.168.18.88)
IPAddress local_IP(192, 168, 18, 88);
IPAddress gateway(192, 168, 18, 1);
IPAddress subnet(255, 255, 255, 0);

const char* AP_SSID = "Alupihan_Rover";
const char* AP_PASS = "rover1234";

// Auxiliary Pins
#define PAN_SERVO_PIN       18  // Camera Pan Servo (GPIO 18 / D18)
#define TILT_SERVO_PIN      19  // Camera Tilt Servo (GPIO 19 / D19)
#define STEERING_SERVO_PIN  16  // TowerPro Chassis Steering Servo (GPIO 16 / D16)
#define TOWERPRO_SERVO_PIN  5   // Auxiliary Payload Servo (GPIO 5 / D5)
#define SPOTLIGHT_RELAY_PIN 2   // Spotlight Relay Pin (GPIO 2 / D2)



// Motor Driver Pins (Matches Physical Wiring Schematic)
#define PIN_PWMA 14 // Left Speed PWM (GPIO 14)
#define PIN_AIN1 26 // Left Dir 1 (GPIO 26 - Connected to AIN1)
#define PIN_AIN2 27 // Left Dir 2 (GPIO 27 - Connected to AIN2)

#define PIN_PWMB 25 // Right Speed PWM (GPIO 25)
#define PIN_BIN1 12 // Right Dir 1 (GPIO 12 - Connected to BIN1)
#define PIN_BIN2 13 // Right Dir 2 (GPIO 13 - Connected to BIN2)

#define PIN_STBY 33 // Motor Driver Standby Enable (GPIO 33)

// PWM Properties
#define PWM_FREQ 5000
#define PWM_RES  8

// ----------------------------------------------------------------------------
// STEERING SERVO CALIBRATION & TRIM CONFIGURATION
// ----------------------------------------------------------------------------
int STEERING_CENTER_ANGLE = 90;  // Default center angle (90°)
int STEERING_RANGE        = 40;  // Steering deflection range (+/- 40°)

Servo panServo;
Servo tiltServo;
Servo steeringServo;

WebSocketsServer webSocket = WebSocketsServer(8080);

int currentPan = 90;
int currentTilt = 90;
int currentSteeringAngle = 90;

// ----------------------------------------------------------------------------
// 2. HARDWARE CONTROL HELPERS (WHEELS & STEERING SERVO)
// ----------------------------------------------------------------------------
void setPanTilt(int pan, int tilt) {
  currentPan = constrain(pan, 0, 180);
  currentTilt = constrain(tilt, 0, 180);
  panServo.write(currentPan);
  tiltServo.write(currentTilt);
}

void setSteeringAngle(int angle) {
  currentSteeringAngle = constrain(angle, 35, 145);
  steeringServo.write(currentSteeringAngle);
}

void setMotorDrive(int leftVal, int rightVal) {
  // Constrain speed values strictly between -255 and 255
  leftVal = constrain(leftVal, -255, 255);
  rightVal = constrain(rightVal, -255, 255);

  // 1. Left Wheel Group Direction Logic
  if (leftVal > 0) {
    digitalWrite(PIN_AIN1, HIGH);
    digitalWrite(PIN_AIN2, LOW);
  } else if (leftVal < 0) {
    digitalWrite(PIN_AIN1, LOW);
    digitalWrite(PIN_AIN2, HIGH);
  } else {
    digitalWrite(PIN_AIN1, LOW);
    digitalWrite(PIN_AIN2, LOW); // Active brake / coast (Stop)
  }

  // 2. Right Wheel Group Direction Logic
  if (rightVal > 0) {
    digitalWrite(PIN_BIN1, HIGH);
    digitalWrite(PIN_BIN2, LOW);
  } else if (rightVal < 0) {
    digitalWrite(PIN_BIN1, LOW);
    digitalWrite(PIN_BIN2, HIGH);
  } else {
    digitalWrite(PIN_BIN1, LOW);
    digitalWrite(PIN_BIN2, LOW); // Active brake / coast (Stop)
  }

  // 3. Write Speed PWM to Motors
  int absLeft = abs(leftVal);
  int absRight = abs(rightVal);

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(PIN_PWMA, absLeft);
  ledcWrite(PIN_PWMB, absRight);
#else
  ledcWrite(0, absLeft);
  ledcWrite(1, absRight);
#endif

  // 4. Calculate Steering Servo Angle for GPIO 5 (Calibrated Center)
  // Steering difference: (Left - Right) ranges from -510 to +510
  int steeringDiff = leftVal - rightVal;
  int minAngle = STEERING_CENTER_ANGLE - STEERING_RANGE;
  int maxAngle = STEERING_CENTER_ANGLE + STEERING_RANGE;
  int targetAngle = map(steeringDiff, -510, 510, minAngle, maxAngle);
  setSteeringAngle(targetAngle);

  // 5. Debug Direction Output
  if (leftVal == 0 && rightVal == 0) {
    Serial.printf("🛑 [MOTORS] STOPPED | Steering Servo (G5): %d° (Calibrated Center)\n", STEERING_CENTER_ANGLE);
  } else if (leftVal > 0 && rightVal > 0) {
    Serial.printf("⬆️ [MOTORS] FORWARD (L: %d | R: %d) | Steering Servo (G5): %d°\n", leftVal, rightVal, STEERING_CENTER_ANGLE);
  } else if (leftVal < 0 && rightVal < 0) {
    Serial.printf("⬇️ [MOTORS] BACKWARD (L: %d | R: %d) | Steering Servo (G5): %d°\n", leftVal, rightVal, STEERING_CENTER_ANGLE);
  } else if (leftVal > 0 && rightVal < 0) {
    Serial.printf("↪️ [MOTORS] TURN RIGHT (L: +%d | R: %d) | Steering Servo (G5): %d°\n", leftVal, rightVal, targetAngle);
  } else if (leftVal < 0 && rightVal > 0) {
    Serial.printf("↩️ [MOTORS] TURN LEFT (L: %d | R: +%d) | Steering Servo (G5): %d°\n", leftVal, rightVal, targetAngle);
  } else {
    Serial.printf("⚙️ [MOTORS] Pivot Drive (L: %d | R: %d) | Steering Servo (G5): %d°\n", leftVal, rightVal, targetAngle);
  }
}

// ----------------------------------------------------------------------------
// 3. WEBSOCKET COMMAND DISPATCHER
// ----------------------------------------------------------------------------
void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("[WEBSOCKET] ❌ Client #%u Disconnected. Immediate Motor Halt!\n", num);
      setMotorDrive(0, 0); // Safety Emergency Stop on disconnect
      break;

    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("\n[WEBSOCKET] ✅ Client #%u Connected from %d.%d.%d.%d\n",
                      num, ip[0], ip[1], ip[2], ip[3]);
        
        // Ensure default state remains STOPPED when new client connects
        setMotorDrive(0, 0);

        StaticJsonDocument<128> doc;
        doc["status"] = "connected";
        doc["mode"] = "wheels_skid_steer";
        String resp;
        serializeJson(doc, resp);
        webSocket.sendTXT(num, resp);
      }
      break;

    case WStype_TEXT:
      {
        StaticJsonDocument<256> doc;
        DeserializationError err = deserializeJson(doc, payload, length);
        if (err) {
          Serial.print("!! [JSON ERROR] ");
          Serial.println(err.c_str());
          return;
        }

        const char* commandType = doc["type"] | "";

        // 1. Wheel Locomotion Command (From Phone App Left Joystick)
        if (strcmp(commandType, "move") == 0) {
          int leftVal = doc["left"] | 0;
          int rightVal = doc["right"] | 0;

          // Pure 4WD Wheel Differential Control (No Servo Steering)
          setMotorDrive(leftVal, rightVal);
        }
        // 2. Camera Pan-Tilt Gimbal Command (From Phone App Right Joystick)
        else if (strcmp(commandType, "pantilt") == 0) {
          int pan = doc["pan"] | 90;
          int tilt = doc["tilt"] | 90;
          setPanTilt(pan, tilt);
        }
        // 3. Spotlight / Relay Toggle Command (Active LOW)
        else if (strcmp(commandType, "spotlight") == 0 || strcmp(commandType, "relay") == 0) {
          bool state = doc["state"] | true;
          digitalWrite(SPOTLIGHT_RELAY_PIN, state ? LOW : HIGH);
          Serial.printf("💡 [RELAY] State: %s (GPIO 2 / D2)\n", state ? "ON" : "OFF");
        }
      }
      break;

    default:
      break;
  }
}

// ----------------------------------------------------------------------------
// LIVE SERIAL MONITOR CALIBRATION HELPER
// ----------------------------------------------------------------------------
void checkSerialCalibration() {
  static String numBuffer = "";

  while (Serial.available() > 0) {
    char c = Serial.read();

    // 1. Nudge Right (+5 Degrees)
    if (c == '+' || c == '=' || c == 'd' || c == 'D') {
      numBuffer = "";
      STEERING_CENTER_ANGLE = constrain(STEERING_CENTER_ANGLE + 5, 45, 135);
      setSteeringAngle(STEERING_CENTER_ANGLE);
      Serial.printf("🎯 [CALIBRATION] Nudge Right (+5°) -> New Center: %d°\n", STEERING_CENTER_ANGLE);
    } 
    // 2. Nudge Left (-5 Degrees)
    else if (c == '-' || c == '_' || c == 'a' || c == 'A') {
      numBuffer = "";
      STEERING_CENTER_ANGLE = constrain(STEERING_CENTER_ANGLE - 5, 45, 135);
      setSteeringAngle(STEERING_CENTER_ANGLE);
      Serial.printf("🎯 [CALIBRATION] Nudge Left (-5°) -> New Center: %d°\n", STEERING_CENTER_ANGLE);
    }
    // 3. Accumulate Digits for Direct Angle Input (e.g., typing '75' + Enter)
    else if (isDigit(c)) {
      numBuffer += c;
    }
    else if (c == '\n' || c == '\r') {
      if (numBuffer.length() > 0) {
        int newAngle = numBuffer.toInt();
        if (newAngle >= 45 && newAngle <= 135) {
          STEERING_CENTER_ANGLE = newAngle;
          setSteeringAngle(STEERING_CENTER_ANGLE);
          Serial.printf("🎯 [CALIBRATION] Set Exact Steering Center Angle to: %d°\n", STEERING_CENTER_ANGLE);
        } else {
          Serial.println("⚠️ [CALIBRATION] Please enter an angle between 45 and 135 degrees.");
        }
        numBuffer = "";
      }
    }
  }
}

// ----------------------------------------------------------------------------
// 4. SETUP & INITIALIZATION
// ----------------------------------------------------------------------------
void setup() {
  // Disable ESP32 Brownout Detector to prevent resets caused by servo current surges
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("  PROJECT ALUPIHAN — 4WD & STEERING CALIBRATION SKETCH");
  Serial.println("=======================================================");

  // 1. Keep Motor Driver Disabled during Pin Setup to prevent any boot glitch
  pinMode(PIN_STBY, OUTPUT);
  digitalWrite(PIN_STBY, LOW); // Disable TB6612FNG Driver initially

  pinMode(PIN_AIN1, OUTPUT);
  pinMode(PIN_AIN2, OUTPUT);
  pinMode(PIN_BIN1, OUTPUT);
  pinMode(PIN_BIN2, OUTPUT);

  digitalWrite(PIN_AIN1, LOW);
  digitalWrite(PIN_AIN2, LOW);
  digitalWrite(PIN_BIN1, LOW);
  digitalWrite(PIN_BIN2, LOW);

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PIN_PWMA, PWM_FREQ, PWM_RES);
  ledcAttach(PIN_PWMB, PWM_FREQ, PWM_RES);
  ledcWrite(PIN_PWMA, 0);
  ledcWrite(PIN_PWMB, 0);
#else
  ledcSetup(0, PWM_FREQ, PWM_RES);
  ledcAttachPin(PIN_PWMA, 0);
  ledcSetup(1, PWM_FREQ, PWM_RES);
  ledcAttachPin(PIN_PWMB, 1);
  ledcWrite(0, 0);
  ledcWrite(1, 0);
#endif

  // Enable TB6612FNG Driver now that pins are safely grounded
  digitalWrite(PIN_STBY, HIGH);
  setMotorDrive(0, 0);
  Serial.println("🛑 [MOTORS] Initialized to STOPPED (Default State)");

  // 2. Setup Spotlight Relay (Active LOW - Primed HIGH before OUTPUT to eliminate boot click)
  digitalWrite(SPOTLIGHT_RELAY_PIN, HIGH);
  pinMode(SPOTLIGHT_RELAY_PIN, OUTPUT);
  digitalWrite(SPOTLIGHT_RELAY_PIN, HIGH);

  // 3. Setup Servos (Pan, Tilt, & Chassis Steering Servo on GPIO 5)
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  panServo.setPeriodHertz(50);
  tiltServo.setPeriodHertz(50);
  steeringServo.setPeriodHertz(50);

  panServo.attach(PAN_SERVO_PIN, 500, 2400);
  tiltServo.attach(TILT_SERVO_PIN, 500, 2400);
  steeringServo.attach(STEERING_SERVO_PIN, 544, 2400);

  setPanTilt(90, 90);

  // --- GENTLE STEERING INITIALIZATION (NO ABRUPT SWEEP TO PREVENT BROWNOUT) ---
  Serial.println("⚙️ [STARTUP] Softly Positioning Steering Servo to Center...");
  setSteeringAngle(STEERING_CENTER_ANGLE); // Hold directly at calibrated center
  delay(100);

  Serial.println("\n-------------------------------------------------------");
  Serial.printf("🎯 STEERING SERVO CALIBRATED CENTER: %d° (GPIO 5)\n", STEERING_CENTER_ANGLE);
  Serial.println("   LIVE SERIAL MONITOR CALIBRATION ACTIVE:");
  Serial.println("   - Type '+' and press Enter to nudge 1° Right");
  Serial.println("   - Type '-' and press Enter to nudge 1° Left");
  Serial.println("   - Type a number (e.g. 82) to set exact center angle");
  Serial.println("-------------------------------------------------------\n");

  // 4. Setup Wi-Fi
  if (USE_STATION_MODE) {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);

    if (USE_STATIC_IP) {
      if (!WiFi.config(local_IP, gateway, subnet)) {
        Serial.println(">> [WI-FI STA] Static IP configuration failed!");
      }
    }

    WiFi.begin(STATION_SSID, STATION_PASS);
    Serial.printf(">> [WI-FI STA] Connecting to %s ...\n", STATION_SSID);
    while (WiFi.status() != WL_CONNECTED) {
      delay(500);
      Serial.print(".");
    }
    Serial.println("\n>> [WI-FI STA] CONNECTED!");
    Serial.print("   IP Address: "); Serial.println(WiFi.localIP());
    Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.localIP()); Serial.println(":8080");
  } else {
    WiFi.mode(WIFI_AP);
    WiFi.softAP(AP_SSID, AP_PASS);
    Serial.println(">> [WI-FI AP] Access Point Started!");
    Serial.print("   IP Address: "); Serial.println(WiFi.softAPIP());
    Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.softAPIP()); Serial.println(":8080");
  }

  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  Serial.println(">> [WEBSOCKET] Server running on Port 8080");
  Serial.println("=======================================================\n");
}

// ----------------------------------------------------------------------------
// 5. MAIN LOOP
// ----------------------------------------------------------------------------
void loop() {
  webSocket.loop();
  checkSerialCalibration(); // Live trim adjustments via Serial Monitor
}


