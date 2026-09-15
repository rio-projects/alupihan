/*
 * ============================================================================
 * Project Saver — Subsystem Test #3: 2-Axis Pan-Tilt Servo Gimbal Test
 * Target Board: Main ESP32 (NodeMCU / WROOM-32)
 *
 * Hardware Wiring:
 *   - Pan Servo (Horizontal): Signal -> GPIO 18, VCC -> 5V Buck Converter, GND -> GND
 *   - Tilt Servo (Vertical):   Signal -> GPIO 19, VCC -> 5V Buck Converter, GND -> GND
 * ============================================================================
 */

#include <WiFi.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>
#include <ESP32Servo.h>

// ----------------------------------------------------------------------------
// 1. WI-FI & SERVO CONFIGURATION
// ----------------------------------------------------------------------------
#define USE_STATION_MODE true  // true = Home Wi-Fi | false = AP Mode (192.168.4.1)

const char* STATION_SSID = "HUAWEI-PBZM";
const char* STATION_PASS = "JfvyqXrK";

const char* AP_SSID = "Saver_Rover";
const char* AP_PASS = "rover1234";

#define PAN_SERVO_PIN  18  // Main ESP32 GPIO 18 (Pan Servo Signal)
#define TILT_SERVO_PIN 19  // Main ESP32 GPIO 19 (Tilt Servo Signal)

Servo panServo;
Servo tiltServo;

WebSocketsServer webSocket = WebSocketsServer(8080);
int currentPan = 90;
int currentTilt = 90;

// ----------------------------------------------------------------------------
// 2. SERVO CONTROL HELPER
// ----------------------------------------------------------------------------
void setGimbalAngles(int pan, int tilt) {
  currentPan = constrain(pan, 0, 180);
  currentTilt = constrain(tilt, 0, 180);

  panServo.write(currentPan);
  tiltServo.write(currentTilt);

  Serial.printf("📷 [GIMBAL] Pan: %d° | Tilt: %d°\n", currentPan, currentTilt);
}

// ----------------------------------------------------------------------------
// 3. WEBSOCKET EVENT HANDLER
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
        doc["subsystem"] = "gimbal_test";
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
        if (strcmp(commandType, "pantilt") == 0) {
          int pan = doc["pan"] | 90;
          int tilt = doc["tilt"] | 90;
          setGimbalAngles(pan, tilt);
        }
      }
      break;

    default:
      break;
  }
}

// ----------------------------------------------------------------------------
// 4. SETUP
// ----------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("  PROJECT SAVER — 2-AXIS PAN-TILT SERVO TEST");
  Serial.println("=======================================================");

  // Attach Servos with standard 500us - 2400us pulse widths
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  panServo.setPeriodHertz(50);
  tiltServo.setPeriodHertz(50);

  panServo.attach(PAN_SERVO_PIN, 500, 2400);
  tiltServo.attach(TILT_SERVO_PIN, 500, 2400);

  // Initial Center position (90°, 90°)
  setGimbalAngles(90, 90);

  // Wi-Fi Setup
  if (USE_STATION_MODE) {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);
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
}
