/*
 * ============================================================================
 * PROJECT SAVER — ESP32-CAM MJPEG VIDEO STREAMING FIRMWARE
 * Author: Sir Vince Zamora & Sir Heinrich Del Rosario
 * Hardware Target: ESP32-CAM (AI-Thinker Model with OV2640 Sensor)
 * ============================================================================
 */

#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ----------------------------------------------------------------------------
// WI-FI NETWORK CONFIGURATION
// ----------------------------------------------------------------------------
#define USE_STATION_MODE    true  // true = Connect to Home Wi-Fi | false = Connect to Rover AP

const char* STATION_SSID = "HUAWEI-PBZM";
const char* STATION_PASS = "JfvyqXrK";

// Static IP for Home Network (Main ESP32 = 192.168.18.88 | ESP32-CAM = 192.168.18.89)
IPAddress local_IP(192, 168, 18, 89);
IPAddress gateway(192, 168, 18, 1);
IPAddress subnet(255, 255, 255, 0);

const char* AP_SSID = "Saver_Rover";
const char* AP_PASS = "rover1234";

// ----------------------------------------------------------------------------
// CAMERA PIN CONFIGURATION (AI-THINKER MODEL)
// ----------------------------------------------------------------------------
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char* _STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* _STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

httpd_handle_t stream_httpd = NULL;

static esp_err_t stream_handler(httpd_req_t *req) {
  camera_fb_t * fb = NULL;
  esp_err_t res = ESP_OK;
  size_t _jpg_buf_len = 0;
  uint8_t * _jpg_buf = NULL;
  char * part_buf[64];

  res = httpd_resp_set_type(req, _STREAM_CONTENT_TYPE);
  if (res != ESP_OK) return res;

  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

  while (true) {
    fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("[ESP32-CAM] Frame capture failed");
      res = ESP_FAIL;
    } else {
      if (fb->format != PIXFORMAT_JPEG) {
        bool jpeg_converted = frame2jpg(fb, 80, &_jpg_buf, &_jpg_buf_len);
        esp_camera_fb_return(fb);
        fb = NULL;
        if (!jpeg_converted) {
          Serial.println("[ESP32-CAM] JPEG compression failed");
          res = ESP_FAIL;
        }
      } else {
        _jpg_buf_len = fb->len;
        _jpg_buf = fb->buf;
      }
    }

    if (res == ESP_OK) {
      size_t hlen = snprintf((char *)part_buf, 64, _STREAM_PART, _jpg_buf_len);
      res = httpd_resp_send_chunk(req, (const char *)part_buf, hlen);
    }
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, (const char *)_jpg_buf, _jpg_buf_len);
    }
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, _STREAM_BOUNDARY, strlen(_STREAM_BOUNDARY));
    }

    if (fb) {
      esp_camera_fb_return(fb);
      fb = NULL;
      _jpg_buf = NULL;
    } else if (_jpg_buf) {
      free(_jpg_buf);
      _jpg_buf = NULL;
    }

    if (res != ESP_OK) break;
  }

  return res;
}

void startCameraServer() {
  httpd_config_t config = HTTPD_DEFAULT_CONFIG();
  config.server_port = 81;
  config.ctrl_port = 81;
  config.lru_purge_enable = true;
  config.max_open_sockets = 5;

  httpd_uri_t stream_uri = {
    .uri       = "/stream",
    .method    = HTTP_GET,
    .handler   = stream_handler,
    .user_ctx  = NULL
  };

  Serial.printf("[ESP32-CAM] Starting MJPEG Stream Server on Port: '%d'\n", config.server_port);
  if (httpd_start(&stream_httpd, &config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &stream_uri);
  }
}

void setup() {
  // Disable ESP32 Brownout Detector to prevent stream resets during voltage dips
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  Serial.setDebugOutput(true);
  delay(1000);
  Serial.println("\n=======================================================");
  Serial.println("  PROJECT SAVER — ESP32-CAM SURVEILLANCE MODULE");
  Serial.println("=======================================================");

  // Hardware Power-Cycle / Hardware Reset of OV2640 Camera Sensor via PWDN (GPIO 32)
  // This clears frozen I2C/SCCB bus state caused by slow voltage ramp during battery power-on!
  pinMode(PWDN_GPIO_NUM, OUTPUT);
  digitalWrite(PWDN_GPIO_NUM, HIGH); // Camera Power Down
  delay(150);
  digitalWrite(PWDN_GPIO_NUM, LOW);  // Camera Power Up
  delay(150);

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 10000000; // 10 MHz XCLK for 100% sensor initialization stability
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_LATEST;
  config.fb_location = CAMERA_FB_IN_PSRAM;

  // Frame size & quality configuration
  if (psramFound()) {
    config.frame_size = FRAMESIZE_VGA; // 640x480 resolution
    config.jpeg_quality = 10;          // High quality JPEG
    config.fb_count = 2;
  } else {
    config.frame_size = FRAMESIZE_CIF; // 400x296 resolution for non-PSRAM models
    config.jpeg_quality = 12;
    config.fb_count = 1;
  }

  // Camera Initialization with Automatic Hardware Reset & Retry Loop (Up to 5 attempts)
  esp_err_t err = ESP_FAIL;
  int cameraAttempts = 0;
  while (err != ESP_OK && cameraAttempts < 5) {
    err = esp_camera_init(&config);
    if (err != ESP_OK) {
      Serial.printf("[ESP32-CAM] Camera init failed (0x%x). Retrying (%d/5)...\n", err, cameraAttempts + 1);
      // Hard reset OV2640 sensor on failure
      digitalWrite(PWDN_GPIO_NUM, HIGH);
      delay(100);
      digitalWrite(PWDN_GPIO_NUM, LOW);
      delay(200);
      cameraAttempts++;
    }
  }

  if (err != ESP_OK) {
    Serial.printf("[ESP32-CAM] Fatal: Camera init failed with error 0x%x\n", err);
    return;
  }

  // Wi-Fi Connection Logic (Dual Network Support)
  if (USE_STATION_MODE) {
    WiFi.mode(WIFI_STA);
    WiFi.disconnect(true);
    delay(100);

    WiFi.config(local_IP, gateway, subnet);
    WiFi.begin(STATION_SSID, STATION_PASS);
    Serial.printf("[ESP32-CAM] Connecting to Home Wi-Fi (%s)...", STATION_SSID);
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
      delay(500);
      Serial.print(".");
      attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\n[ESP32-CAM] Wi-Fi Connected!");
      Serial.print("   Stream URL: http://"); Serial.print(WiFi.localIP()); Serial.println(":81/stream");
      startCameraServer();
      return;
    }

    Serial.println("\n[ESP32-CAM] Home Wi-Fi failed. Falling back to Rover AP...");
  }

  // Fallback / AP Connection Logic - Reset static IP config to prevent subnet mismatch
  WiFi.disconnect(true);
  delay(100);
  WiFi.mode(WIFI_STA);
  WiFi.config(INADDR_NONE, INADDR_NONE, INADDR_NONE);
  WiFi.begin(AP_SSID, AP_PASS);
  Serial.printf("[ESP32-CAM] Connecting to Rover AP (%s)...", AP_SSID);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\n[ESP32-CAM] Connected to Rover AP!");
  Serial.print("   Stream URL: http://"); Serial.print(WiFi.localIP()); Serial.println(":81/stream");

  startCameraServer();
}

void loop() {
  delay(10000);
}