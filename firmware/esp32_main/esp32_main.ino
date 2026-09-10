/*
 * ============================================================================
 * PROJECT ALUPIHAN — MAIN ESP32 CONTROLLER FIRMWARE
 * Author: Sir Vince Zamora & Sir Heinrich Del Rosario
 * Hardware Target: ESP32-WROOM-32 Development Board
 * 
 * Dependencies:
 *   - ESP32Servo (by Kevin Harrington)
 *   - WebSockets (by Markus Sattler)
 *   - ArduinoJson (by Benoit Blanchon)
 *   - WiFi & WebServer (Built-in ESP32 core)
 * ============================================================================
 */

#include <WiFi.h>
#include <WebServer.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include "driver/gpio.h"
#include "config.h"

// System Global Objects
WebServer server(HTTP_SERVER_PORT);
WebSocketsServer webSocket(WEBSOCKET_PORT);

// Static Network IP Configuration
IPAddress local_IP(192, 168, 18, 88);
IPAddress gateway(192, 168, 18, 1);
IPAddress subnet(255, 255, 255, 0);

// Servo Objects
Servo servoPan;
Servo servoTilt;
Servo servoSteering;

// State Variables
bool spotlightState = false;
bool buzzerState = false;
uint8_t currentRelayActiveState = RELAY_ACTIVE_STATE;
float targetPanAngle = PAN_DEFAULT_ANGLE;
float targetTiltAngle = TILT_DEFAULT_ANGLE;
float currentPanAngle = PAN_DEFAULT_ANGLE;
float currentTiltAngle = TILT_DEFAULT_ANGLE;
int steeringCenterAngle = STEERING_CENTER_DEFAULT;
int currentSteeringAngle = STEERING_CENTER_DEFAULT;
unsigned long lastTelemetryTime = 0;
unsigned long lastServoTick = 0;

// Function Prototypes
void setupHardware();
void setupWiFi();
void setupWebServer();
void setupWebSockets();
void handleWebSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length);
void processCommand(uint8_t clientNum, String message);
void setMotorSpeeds(int speedLeft, int speedRight);
void setSteeringAngle(int angle);
void setGimbalAngles(float pan, float tilt);
void setRelayState(bool state);
void updateSmoothServos();
void checkSerialCalibration();
void playToneSequence(int type);
void sendTelemetry();

void setup() {
  // Disable ESP32 Brownout Detector to prevent unexpected resets during load spikes
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=======================================================");
  Serial.println("   PROJECT ALUPIHAN — MAIN ESP32 CONTROLLER SYSTEM");
  Serial.println("=======================================================");

  setupHardware();
  setupWiFi();
  setupWebServer();
  setupWebSockets();

  playToneSequence(1); // Boot chime
  Serial.println(">> [ALUPIHAN] Initialization Complete. System Ready.");
  Serial.println("=======================================================\n");
}

void loop() {
  server.handleClient();
  webSocket.loop();
  updateSmoothServos();     // Smooth 50Hz camera interpolation
  checkSerialCalibration();  // Live Serial Monitor trim adjustments

  // Send periodic telemetry packet every 1 second
  if (millis() - lastTelemetryTime >= 1000) {
    sendTelemetry();
    lastTelemetryTime = millis();
  }
}

// ----------------------------------------------------------------------------
// HARDWARE INITIALIZATION
// ----------------------------------------------------------------------------
void setupHardware() {
  // 1. Reserve LEDC PWM Timers for Servos FIRST (Timers 0, 1, 2 at 50 Hz)
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);

  servoPan.setPeriodHertz(50);
  servoTilt.setPeriodHertz(50);
  servoSteering.setPeriodHertz(50);

  servoPan.attach(PIN_SERVO_PAN, SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
  servoTilt.attach(PIN_SERVO_TILT, SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
  servoSteering.attach(PIN_SERVO_STEERING, 544, SERVO_MAX_PULSE_US);

  // Set default starting positions with initial inversion and trim
  int initPan  = INVERT_PAN_DIRECTION  ? (180 - (int)round(currentPanAngle))  : (int)round(currentPanAngle);
  int initTilt = INVERT_TILT_DIRECTION ? (180 - (int)round(currentTiltAngle)) : (int)round(currentTiltAngle);
  servoPan.write(constrain(initPan + PAN_TRIM_OFFSET, 0, 180));
  servoTilt.write(constrain(initTilt + TILT_TRIM_OFFSET, 0, 180));
  setSteeringAngle(steeringCenterAngle);

  // 2. Motor Driver Direction & Standby Pin Setup
  pinMode(PIN_MOTOR_STBY, OUTPUT);
  digitalWrite(PIN_MOTOR_STBY, LOW);

  pinMode(PIN_MOTOR_AIN1, OUTPUT);
  pinMode(PIN_MOTOR_AIN2, OUTPUT);
  pinMode(PIN_MOTOR_BIN1, OUTPUT);
  pinMode(PIN_MOTOR_BIN2, OUTPUT);

  digitalWrite(PIN_MOTOR_AIN1, LOW);
  digitalWrite(PIN_MOTOR_AIN2, LOW);
  digitalWrite(PIN_MOTOR_BIN1, LOW);
  digitalWrite(PIN_MOTOR_BIN2, LOW);

  // 3. TB6612FNG Motor Speed PWM configuration (Channels 6 & 7 on Timer 3 at 5 kHz)
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcAttach(PIN_MOTOR_PWMA, PWM_FREQ_MOTORS, PWM_RES_MOTORS);
  ledcAttach(PIN_MOTOR_PWMB, PWM_FREQ_MOTORS, PWM_RES_MOTORS);
  ledcWrite(PIN_MOTOR_PWMA, 0);
  ledcWrite(PIN_MOTOR_PWMB, 0);
#else
  ledcSetup(PWM_CHAN_MOTORS_A, PWM_FREQ_MOTORS, PWM_RES_MOTORS);
  ledcAttachPin(PIN_MOTOR_PWMA, PWM_CHAN_MOTORS_A);
  ledcSetup(PWM_CHAN_MOTORS_B, PWM_FREQ_MOTORS, PWM_RES_MOTORS);
  ledcAttachPin(PIN_MOTOR_PWMB, PWM_CHAN_MOTORS_B);
  ledcWrite(PWM_CHAN_MOTORS_A, 0);
  ledcWrite(PWM_CHAN_MOTORS_B, 0);
#endif

  // Enable Motor Driver (STBY HIGH)
  digitalWrite(PIN_MOTOR_STBY, HIGH);
  setMotorSpeeds(0, 0);

  // 4. Relay Configuration (GPIO 2 / D2 - High-Z High Impedance OFF state on boot)
  setRelayState(false);

  // 5. Active Piezo Buzzer Configuration
  pinMode(PIN_PIEZO_BUZZER, OUTPUT);
  digitalWrite(PIN_PIEZO_BUZZER, LOW);
}

// ----------------------------------------------------------------------------
// WI-FI NETWORK INITIALIZATION
// ----------------------------------------------------------------------------
void setupWiFi() {
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

    Serial.println("\n>> [WI-FI STA] Could not connect to Home Wi-Fi. Falling back to AP Mode!");
  }

  // AP Mode Fallback
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASS);
  Serial.println(">> [WI-FI AP] Access Point Started!");
  Serial.print("   SSID: "); Serial.println(AP_SSID);
  Serial.print("   IP Address: "); Serial.println(WiFi.softAPIP());
  Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.softAPIP()); Serial.println(":8080");
}

// ----------------------------------------------------------------------------
// WEB SERVER & WEBSOCKETS SETUP
// ----------------------------------------------------------------------------
void setupWebServer() {
  server.on("/", []() {
    server.send(200, "text/json", "{\"status\":\"online\",\"system\":\"Alupihan Main ESP32 Controller\"}");
  });
  server.begin();
  Serial.println(">> [HTTP] Server Started on Port " + String(HTTP_SERVER_PORT));
}

void setupWebSockets() {
  webSocket.begin();
  webSocket.onEvent(handleWebSocketEvent);
  Serial.println(">> [WEBSOCKET] Command Server Started on Port " + String(WEBSOCKET_PORT));
}

void handleWebSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("[WEBSOCKET] Client #%u Disconnected. Emergency Halt!\n", num);
      setMotorSpeeds(0, 0);
      break;
    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("[WEBSOCKET] Client #%u Connected from %d.%d.%d.%d\n", num, ip[0], ip[1], ip[2], ip[3]);
        setMotorSpeeds(0, 0);
        playToneSequence(2); // Connection chime
      }
      break;
    case WStype_TEXT:
      {
        String message = String((char*)payload);
        processCommand(num, message);
      }
      break;
    default:
      break;
  }
}

// ----------------------------------------------------------------------------
// COMMAND PROCESSING LOGIC
// ----------------------------------------------------------------------------
void processCommand(uint8_t clientNum, String message) {
  StaticJsonDocument<256> doc;
  DeserializationError error = deserializeJson(doc, message);

  if (error) {
    Serial.print("!! [JSON ERROR] ");
    Serial.println(error.f_str());
    return;
  }

  const char* type = doc["type"] | "";

  // 1. Locomotion Motor & Synchronized Steering Servo Command
  if (strcmp(type, "move") == 0) {
    int leftSpeed = doc["left"] | 0;   // -255 to 255
    int rightSpeed = doc["right"] | 0; // -255 to 255
    setMotorSpeeds(leftSpeed, rightSpeed);

    // Synchronize TowerPro chassis steering servo with differential wheel turning
    int steeringDiff = leftSpeed - rightSpeed;
    int minAngle = steeringCenterAngle - STEERING_DEFLECTION_MAX;
    int maxAngle = steeringCenterAngle + STEERING_DEFLECTION_MAX;
    int targetSteerAngle = map(steeringDiff, -510, 510, minAngle, maxAngle);
    setSteeringAngle(targetSteerAngle);
  }
  // 2. Camera Pan-Tilt Servo Command (GPIO 18 Pan / GPIO 19 Tilt)
  else if (strcmp(type, "pantilt") == 0) {
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
  // 3. Spotlight / Auxiliary Relay Toggle Command (GPIO 2 / D2)
  else if (strcmp(type, "spotlight") == 0 || strcmp(type, "relay") == 0) {
    bool state = doc["state"].as<bool>();
    setRelayState(state);
    Serial.printf("💡 [RELAY] State: %s | GPIO 2 Output: %s\n",
                  spotlightState ? "ON" : "OFF",
                  (spotlightState ? currentRelayActiveState : !currentRelayActiveState) == HIGH ? "HIGH (3.3V)" : "LOW (0V)");
  }
  // 4. Piezo Horn Trigger Command
  else if (strcmp(type, "horn") == 0) {
    bool state = doc["state"].as<bool>();
    digitalWrite(PIN_PIEZO_BUZZER, state ? HIGH : LOW);
  }
}

// ----------------------------------------------------------------------------
// MOTOR DRIVER CONTROL (TB6612FNG)
// ----------------------------------------------------------------------------
void setMotorSpeeds(int speedLeft, int speedRight) {
  speedLeft = constrain(speedLeft, -255, 255);
  speedRight = constrain(speedRight, -255, 255);

  // Left Motor Direction
  if (speedLeft > 0) {
    digitalWrite(PIN_MOTOR_AIN1, HIGH);
    digitalWrite(PIN_MOTOR_AIN2, LOW);
  } else if (speedLeft < 0) {
    digitalWrite(PIN_MOTOR_AIN1, LOW);
    digitalWrite(PIN_MOTOR_AIN2, HIGH);
  } else {
    digitalWrite(PIN_MOTOR_AIN1, LOW);
    digitalWrite(PIN_MOTOR_AIN2, LOW);
  }

  // Right Motor Direction
  if (speedRight > 0) {
    digitalWrite(PIN_MOTOR_BIN1, HIGH);
    digitalWrite(PIN_MOTOR_BIN2, LOW);
  } else if (speedRight < 0) {
    digitalWrite(PIN_MOTOR_BIN1, LOW);
    digitalWrite(PIN_MOTOR_BIN2, HIGH);
  } else {
    digitalWrite(PIN_MOTOR_BIN1, LOW);
    digitalWrite(PIN_MOTOR_BIN2, LOW);
  }

  // Scale Motor Speed according to MOTOR_MAX_SPEED_SCALE safety limit
  int absLeft = (int)(abs(speedLeft) * MOTOR_MAX_SPEED_SCALE);
  int absRight = (int)(abs(speedRight) * MOTOR_MAX_SPEED_SCALE);

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(PIN_MOTOR_PWMA, absLeft);
  ledcWrite(PIN_MOTOR_PWMB, absRight);
#else
  ledcWrite(PWM_CHAN_MOTORS_A, absLeft);
  ledcWrite(PWM_CHAN_MOTORS_B, absRight);
#endif
}

void setSteeringAngle(int angle) {
  currentSteeringAngle = constrain(angle, 35, 145);
  servoSteering.write(currentSteeringAngle);
}

void setGimbalAngles(float pan, float tilt) {
  targetPanAngle  = constrain(pan, 0.0f, 180.0f);
  targetTiltAngle = constrain(tilt, (float)TILT_MIN_ANGLE, (float)TILT_MAX_ANGLE);
  Serial.printf("📷 [GIMBAL TARGET] Pan: %.1f° | Tilt: %.1f°\n", targetPanAngle, targetTiltAngle);
}

void setRelayState(bool state) {
  spotlightState = state;
  pinMode(PIN_RELAY_SPOTLIGHT, OUTPUT);
  digitalWrite(PIN_RELAY_SPOTLIGHT, spotlightState ? LOW : HIGH);
}

// ----------------------------------------------------------------------------
// LIVE SERIAL MONITOR STEERING TRIM CALIBRATION & RELAY TEST
// ----------------------------------------------------------------------------
void checkSerialCalibration() {
  static String numBuffer = "";

  while (Serial.available() > 0) {
    char c = Serial.read();

    if (c == '+' || c == '=' || c == 'd' || c == 'D') {
      numBuffer = "";
      steeringCenterAngle = constrain(steeringCenterAngle + 5, 45, 135);
      setSteeringAngle(steeringCenterAngle);
      Serial.printf("🎯 [STEERING TRIM] Nudge Right (+5°) -> New Center: %d°\n", steeringCenterAngle);
    } else if (c == '-' || c == '_' || c == 'a' || c == 'A') {
      numBuffer = "";
      steeringCenterAngle = constrain(steeringCenterAngle - 5, 45, 135);
      setSteeringAngle(steeringCenterAngle);
      Serial.printf("🎯 [STEERING TRIM] Nudge Left (-5°) -> New Center: %d°\n", steeringCenterAngle);
    } else if (c == 'r' || c == 'R') {
      numBuffer = "";
      setRelayState(!spotlightState);
      Serial.printf("💡 [RELAY TEST] State: %s | GPIO 2 Output: %s\n",
                    spotlightState ? "ON" : "OFF",
                    (spotlightState ? currentRelayActiveState : !currentRelayActiveState) == HIGH ? "HIGH (3.3V)" : "LOW (0V)");
    } else if (c == 'i' || c == 'I') {
      numBuffer = "";
      currentRelayActiveState = (currentRelayActiveState == LOW) ? HIGH : LOW;
      setRelayState(spotlightState);
      Serial.printf("🔄 [RELAY POLARITY TOGGLED] New Active Mode: %s\n",
                    currentRelayActiveState == LOW ? "ACTIVE-LOW (0V = ON)" : "ACTIVE-HIGH (3.3V = ON)");
    } else if (isDigit(c)) {
      numBuffer += c;
    } else if (c == '\n' || c == '\r') {
      if (numBuffer.length() > 0) {
        int newAngle = numBuffer.toInt();
        if (newAngle >= 45 && newAngle <= 135) {
          steeringCenterAngle = newAngle;
          setSteeringAngle(steeringCenterAngle);
          Serial.printf("🎯 [STEERING TRIM] Set Exact Center Angle to: %d°\n", steeringCenterAngle);
        } else {
          Serial.println("⚠️ [STEERING TRIM] Angle must be between 45° and 135°.");
        }
        numBuffer = "";
      }
    }
  }
}

// ----------------------------------------------------------------------------
// AUDIO FEEDBACK (PIEZO BUZZER)
// ----------------------------------------------------------------------------
void playToneSequence(int type) {
  if (type == 1) { // Boot Tone
    digitalWrite(PIN_PIEZO_BUZZER, HIGH); delay(80);
    digitalWrite(PIN_PIEZO_BUZZER, LOW);  delay(60);
    digitalWrite(PIN_PIEZO_BUZZER, HIGH); delay(150);
    digitalWrite(PIN_PIEZO_BUZZER, LOW);
  } else if (type == 2) { // Client Connected Tone
    digitalWrite(PIN_PIEZO_BUZZER, HIGH); delay(100);
    digitalWrite(PIN_PIEZO_BUZZER, LOW);
  }
}

// ----------------------------------------------------------------------------
// TELEMETRY STATUS REPORTING
// ----------------------------------------------------------------------------
void sendTelemetry() {
  StaticJsonDocument<256> doc;
  doc["type"] = "telemetry";
  doc["uptime"] = millis() / 1000;
  doc["spotlight"] = spotlightState;
  doc["pan"] = (int)round(currentPanAngle);
  doc["tilt"] = (int)round(currentTiltAngle);
  doc["steer_center"] = steeringCenterAngle;
  doc["wifi_rssi"] = WiFi.RSSI();

  String jsonString;
  serializeJson(doc, jsonString);
  webSocket.broadcastTXT(jsonString);
}

// ----------------------------------------------------------------------------
// SMOOTH SERVO MOTION INTERPOLATION (50Hz Slew Rate Limiting)
// ----------------------------------------------------------------------------
void updateSmoothServos() {
  if (millis() - lastServoTick >= 20) { // 50 Hz smooth update cycle
    lastServoTick = millis();

    // Smooth Pan Movement
    if (currentPanAngle < targetPanAngle) {
      currentPanAngle = min(currentPanAngle + (float)GIMBAL_MAX_STEP_DEG, targetPanAngle);
    } else if (currentPanAngle > targetPanAngle) {
      currentPanAngle = max(currentPanAngle - (float)GIMBAL_MAX_STEP_DEG, targetPanAngle);
    }
    int effPan = INVERT_PAN_DIRECTION ? (180 - (int)round(currentPanAngle)) : (int)round(currentPanAngle);
    servoPan.write(constrain(effPan + PAN_TRIM_OFFSET, 0, 180));

    // Smooth Tilt Movement
    if (currentTiltAngle < targetTiltAngle) {
      currentTiltAngle = min(currentTiltAngle + (float)GIMBAL_MAX_STEP_DEG, targetTiltAngle);
    } else if (currentTiltAngle > targetTiltAngle) {
      currentTiltAngle = max(currentTiltAngle - (float)GIMBAL_MAX_STEP_DEG, targetTiltAngle);
    }
    int effTilt = INVERT_TILT_DIRECTION ? (180 - (int)round(currentTiltAngle)) : (int)round(currentTiltAngle);
    servoTilt.write(constrain(effTilt + TILT_TRIM_OFFSET, 0, 180));
  }
}