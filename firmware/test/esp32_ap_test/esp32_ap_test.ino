/*
 * ============================================================================
 * PROJECT ALUPIHAN — ESP32 AP & WEBSOCKET COMMUNICATION TEST SKETCH
 * Author: Sir Vince Zamora & Sir Heinrich Del Rosario
 * Target: Main ESP32 Development Board (ESP32-WROOM-32)
 * 
 * Objective:
 *   1. Create Wi-Fi Access Point ("Alupihan_Rover" / "rover1234") at 192.168.4.1.
 *   2. Run WebSocket server on Port 8080 and HTTP Diagnostics on Port 80.
 *   3. Print all incoming commands from the AlupihanRover app directly to 
 *      the Serial Monitor (115200 Baud) in real time.
 * ============================================================================
 */

#include <WiFi.h>
#include <WebServer.h>
#include <WebSocketsServer.h>
#include <ArduinoJson.h>

// ----------------------------------------------------------------------------
// MODE SELECTION: Set to true to connect to Home Wi-Fi, or false for Rover AP Mode
// ----------------------------------------------------------------------------
#define USE_STATION_MODE true  // Change to false when testing standalone AP mode!

// Network Configurations
const char* STATION_SSID = "HUAWEI-PBZM";  // Replace with your Home Wi-Fi Name
const char* STATION_PASS = "JfvyqXrK";  // Replace with your Home Wi-Fi Password

const char* AP_SSID = "Alupihan_Rover";
const char* AP_PASS = "rover1234";

const int WEBSOCKET_PORT = 8080;
const int HTTP_PORT = 80;

WebServer server(HTTP_PORT);
WebSocketsServer webSocket(WEBSOCKET_PORT);

unsigned long messageCount = 0;

void handleWebSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  switch (type) {
    case WStype_DISCONNECTED:
      Serial.printf("\n[WEBSOCKET] ❌ Client #%u Disconnected.\n", num);
      break;

    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("\n[WEBSOCKET] ✅ Client #%u Connected from %d.%d.%d.%d\n", 
                      num, ip[0], ip[1], ip[2], ip[3]);
        
        // Send initial handshake JSON
        StaticJsonDocument<128> doc;
        doc["status"] = "connected";
        doc["server"] = "ESP32_Test_Server";
        String response;
        serializeJson(doc, response);
        webSocket.sendTXT(num, response);
      }
      break;

    case WStype_TEXT:
      {
        messageCount++;
        String text = String((char*)payload);
        Serial.printf("\n[MSG #%lu] Received from Client #%u: %s\n", messageCount, num, text.c_str());

        // Parse JSON payload to log structured commands
        StaticJsonDocument<256> doc;
        DeserializationError error = deserializeJson(doc, text);

        if (!error) {
          const char* cmdType = doc["type"] | "unknown";
          if (strcmp(cmdType, "move") == 0) {
            int left = doc["left"] | 0;
            int right = doc["right"] | 0;
            Serial.printf(" └─ 🚗 LOCOMOTION DRIVE -> Left Motor: %d | Right Motor: %d\n", left, right);
          } else if (strcmp(cmdType, "pantilt") == 0) {
            int pan = doc["pan"] | 90;
            int tilt = doc["tilt"] | 90;
            Serial.printf(" └─ 📷 PAN-TILT SERVO -> Pan: %d° | Tilt: %d°\n", pan, tilt);
          } else if (strcmp(cmdType, "towerpro") == 0) {
            int angle = doc["angle"] | 90;
            Serial.printf(" └─ ⚙️ TOWERPRO SERVO -> Angle: %d°\n", angle);
          } else if (strcmp(cmdType, "spotlight") == 0) {
            bool state = doc["state"] | false;
            Serial.printf(" └─ 💡 60W SPOTLIGHT RELAY -> State: %s\n", state ? "ON (HIGH)" : "OFF (LOW)");
          } else if (strcmp(cmdType, "horn") == 0) {
            bool state = doc["state"] | false;
            Serial.printf(" └─ 📢 PIEZO HORN -> State: %s\n", state ? "ACTIVE" : "INACTIVE");
          }
        }

        // Echo response back to client
        StaticJsonDocument<128> replyDoc;
        replyDoc["status"] = "ok";
        replyDoc["rx_count"] = messageCount;
        String replyText;
        serializeJson(replyDoc, replyText);
        webSocket.sendTXT(num, replyText);
      }
      break;

    default:
      break;
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("  PROJECT ALUPIHAN — ESP32 TEST SKETCH");
  Serial.println("=======================================================");

  IPAddress ip;
  if (USE_STATION_MODE) {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect();
    delay(100);
    WiFi.begin(STATION_SSID, STATION_PASS);
    Serial.printf(">> [WIFI STA] Connecting to Home Wi-Fi: %s ...\n", STATION_SSID);
    while (WiFi.status() != WL_CONNECTED) {
      delay(500);
      Serial.print(".");
    }
    ip = WiFi.localIP();
    Serial.printf("\n>> [WIFI STA] Connected! Assigned IP: %s\n", ip.toString().c_str());
  } else {
    WiFi.mode(WIFI_AP);
    WiFi.softAP(AP_SSID, AP_PASS);
    ip = WiFi.softAPIP();
    Serial.print(">> [WIFI AP] Access Point Started!\n");
    Serial.printf("   SSID:     %s\n", AP_SSID);
    Serial.printf("   Password: %s\n", AP_PASS);
    Serial.printf("   IP Addr:  %s\n", ip.toString().c_str());
  }

  // 2. Initialize HTTP Server
  server.on("/", []() {
    String html = "<html><body style='font-family:sans-serif; text-align:center; padding:20px; background:#121212; color:#fff;'>";
    html += "<h1>Project Alupihan — ESP32 Test Server</h1>";
    html += "<p>Status: <b>ONLINE</b></p>";
    html += "<p>WebSocket Port: <b>8080</b></p>";
    html += "</body></html>";
    server.send(200, "text/html", html);
  });
  server.begin();
  Serial.printf(">> [HTTP SERVER] Running on http://%s/\n", ip.toString().c_str());

  // 3. Initialize WebSocket Server
  webSocket.begin();
  webSocket.onEvent(handleWebSocketEvent);
  Serial.printf(">> [WEBSOCKET] Running on ws://%s:8080/\n", ip.toString().c_str());
  Serial.println("=======================================================");
  Serial.printf(">> Target WebSocket URL for App: ws://%s:8080\n", ip.toString().c_str());
  Serial.println("=======================================================\n");
}

void loop() {
  server.handleClient();
  webSocket.loop();
}
