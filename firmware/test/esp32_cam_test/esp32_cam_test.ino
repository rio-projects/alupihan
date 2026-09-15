/*
 * ============================================================================
 * Project Saver — Subsystem Test #1: ESP32-CAM WebSocket & HTTP Stream Test
 * Target Board: AI Thinker ESP32-CAM (OV2640 / OV7670)
 * ============================================================================
 */

#include "esp_camera.h"
#include <WiFi.h>
#include "esp_http_server.h"

// ----------------------------------------------------------------------------
// 1. WI-FI MODE SELECTION
// ----------------------------------------------------------------------------
#define USE_STATION_MODE true

#if USE_STATION_MODE
  const char* ssid = "HUAWEI-PBZM";      // <--- Wi-Fi SSID
  const char* password = "JfvyqXrK"; // <--- Wi-Fi Password
#else
  const char* ssid = "ESP32_CAM_TEST";
  const char* password = "12345678";
#endif

// ----------------------------------------------------------------------------
// 2. AI THINKER ESP32-CAM INTERNAL CAMERA PINOUT
// ----------------------------------------------------------------------------
#define PWDN_GPIO_NUM    32
#define RESET_GPIO_NUM   -1
#define XCLK_GPIO_NUM     0
#define SIOD_GPIO_NUM    26
#define SIOC_GPIO_NUM    27

#define Y9_GPIO_NUM      35
#define Y8_GPIO_NUM      34
#define Y7_GPIO_NUM      39
#define Y6_GPIO_NUM      36
#define Y5_GPIO_NUM      21
#define Y4_GPIO_NUM      19
#define Y3_GPIO_NUM      18
#define Y2_GPIO_NUM       5
#define VSYNC_GPIO_NUM   25
#define HREF_GPIO_NUM    23
#define PCLK_GPIO_NUM    22

httpd_handle_t control_httpd = NULL;
httpd_handle_t stream_httpd = NULL;
static int ws_client_fd = -1;
static const uint32_t WS_FRAME_INTERVAL_MS = 33; // Target approximately 30 FPS
static uint32_t last_ws_frame_ms = 0;
static uint32_t ws_frames_sent = 0;

// ----------------------------------------------------------------------------
// 3. WEBSOCKET CAMERA HANDLER (/ws)
// ----------------------------------------------------------------------------
static esp_err_t ws_camera_handler(httpd_req_t *req) {
  if (req->method == HTTP_GET) {
    // Do not start transmitting from the upgrade callback. The HTTP server has
    // not finished changing the socket into a WebSocket until this returns.
    Serial.printf(">> [WEBSOCKET] Upgrade requested on socket FD: %d\n",
                  httpd_req_to_sockfd(req));
    return ESP_OK;
  }

  // The app sends a small "start" message after onopen. Receiving it proves
  // that the upgrade is complete and the socket is ready for outgoing frames.
  httpd_ws_frame_t request_frame;
  memset(&request_frame, 0, sizeof(request_frame));
  esp_err_t ret = httpd_ws_recv_frame(req, &request_frame, 0);
  if (ret != ESP_OK) {
    Serial.printf("!! [WEBSOCKET] Unable to read client frame: 0x%x\n", ret);
    return ret;
  }

  // Drain the payload even though its contents are not needed.
  if (request_frame.len > 0) {
    uint8_t *payload = (uint8_t *)malloc(request_frame.len + 1);
    if (!payload) return ESP_ERR_NO_MEM;
    request_frame.payload = payload;
    ret = httpd_ws_recv_frame(req, &request_frame, request_frame.len);
    free(payload);
    if (ret != ESP_OK) return ret;
  }

  ws_client_fd = httpd_req_to_sockfd(req);
  Serial.printf(">> [WEBSOCKET] Streaming started on socket FD: %d\n", ws_client_fd);
  return ESP_OK;
}

// Lightweight Base64 Encoder for ESP32 Frame Buffers
static const char b64_table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static String base64_encode(const uint8_t *data, size_t input_length) {
  String encodedString = "";
  encodedString.reserve(((input_length + 2) / 3) * 4);
  for (size_t i = 0; i < input_length; i += 3) {
    uint32_t b0 = data[i];
    uint32_t b1 = (i + 1 < input_length) ? data[i + 1] : 0;
    uint32_t b2 = (i + 2 < input_length) ? data[i + 2] : 0;
    uint32_t triple = (b0 << 16) | (b1 << 8) | b2;

    encodedString += b64_table[(triple >> 18) & 0x3F];
    encodedString += b64_table[(triple >> 12) & 0x3F];
    encodedString += (i + 1 < input_length) ? b64_table[(triple >> 6) & 0x3F] : '=';
    encodedString += (i + 2 < input_length) ? b64_table[triple & 0x3F] : '=';
  }
  return encodedString;
}

// Broadcast frame over WebSocket
void broadcast_ws_frame() {
  if (ws_client_fd < 0 || !control_httpd) return;

  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) return;

  const size_t jpeg_len = fb->len;
  String b64 = base64_encode(fb->buf, fb->len);
  esp_camera_fb_return(fb);

  httpd_ws_frame_t ws_pkt;
  memset(&ws_pkt, 0, sizeof(httpd_ws_frame_t));
  ws_pkt.type = HTTPD_WS_TYPE_TEXT;
  ws_pkt.payload = (uint8_t*)b64.c_str();
  ws_pkt.len = b64.length();

  esp_err_t ret = httpd_ws_send_frame_async(control_httpd, ws_client_fd, &ws_pkt);
  if (ret != ESP_OK) {
    Serial.printf("!! [WEBSOCKET] Frame send failed on FD %d: 0x%x\n",
                  ws_client_fd, ret);
    ws_client_fd = -1;
  } else {
    ws_frames_sent++;
    if (ws_frames_sent % 30 == 0) {
      Serial.printf(">> [WEBSOCKET] Sent %u frames; JPEG %u bytes\n",
                    ws_frames_sent, (unsigned int)jpeg_len);
    }
  }
}

// ----------------------------------------------------------------------------
// 4. SINGLE FRAME JPEG CAPTURE HANDLER (/capture)
// ----------------------------------------------------------------------------
static esp_err_t capture_handler(httpd_req_t *req) {
  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    httpd_resp_send_500(req);
    return ESP_FAIL;
  }

  httpd_resp_set_type(req, "image/jpeg");
  httpd_resp_set_hdr(req, "Content-Disposition", "inline; filename=capture.jpg");
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
  httpd_resp_set_hdr(req, "Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
  httpd_resp_set_hdr(req, "Pragma", "no-cache");

  esp_err_t res = httpd_resp_send(req, (const char *)fb->buf, fb->len);
  esp_camera_fb_return(fb);
  return res;
}

// ----------------------------------------------------------------------------
// 5. MJPEG STREAM HANDLER (/stream)
// ----------------------------------------------------------------------------
#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char* _STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* _STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

static esp_err_t stream_handler(httpd_req_t *req) {
  camera_fb_t * fb = NULL;
  esp_err_t res = ESP_OK;
  size_t _jpg_buf_len = 0;
  uint8_t * _jpg_buf = NULL;
  char part_buf[64];
  uint32_t frames_sent = 0;
  uint32_t report_started_ms = millis();

  Serial.printf(">> [MJPEG] Client connected on socket FD: %d\n",
                httpd_req_to_sockfd(req));

  res = httpd_resp_set_type(req, _STREAM_CONTENT_TYPE);
  if (res != ESP_OK) return res;
  httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");

  while (true) {
    fb = esp_camera_fb_get();
    if (!fb) {
      res = ESP_FAIL;
    } else {
      if (fb->format != PIXFORMAT_JPEG) {
        bool jpeg_converted = frame2jpg(fb, 80, &_jpg_buf, &_jpg_buf_len);
        esp_camera_fb_return(fb);
        fb = NULL;
        if (!jpeg_converted) res = ESP_FAIL;
      } else {
        _jpg_buf_len = fb->len;
        _jpg_buf = fb->buf;
      }
    }

    if (res == ESP_OK) {
      size_t hlen = snprintf(part_buf, 64, _STREAM_PART, _jpg_buf_len);
      res = httpd_resp_send_chunk(req, _STREAM_BOUNDARY, strlen(_STREAM_BOUNDARY));
      if (res == ESP_OK) res = httpd_resp_send_chunk(req, part_buf, hlen);
      if (res == ESP_OK) res = httpd_resp_send_chunk(req, (const char *)_jpg_buf, _jpg_buf_len);
      if (res == ESP_OK) {
        frames_sent++;
        if (frames_sent % 30 == 0) {
          const uint32_t now = millis();
          const uint32_t elapsed = now - report_started_ms;
          const float fps = elapsed > 0 ? 30000.0f / elapsed : 0.0f;
          Serial.printf(">> [MJPEG] %u frames, %.1f FPS, last JPEG %u bytes\n",
                        frames_sent, fps, (unsigned int)_jpg_buf_len);
          report_started_ms = now;
        }
      }
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

  Serial.printf("!! [MJPEG] Client disconnected after %u frames: 0x%x\n",
                frames_sent, res);
  return res;
}

// ----------------------------------------------------------------------------
// 6. INDEX HTML WEBPAGE HANDLER (/)
// ----------------------------------------------------------------------------
static esp_err_t index_handler(httpd_req_t *req) {
  const char* html = 
    "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
    "<title>ESP32-CAM Camera Test</title><style>"
    "body{background:#0b0f19;color:#00E5FF;font-family:sans-serif;text-align:center;margin:0;padding:20px;}"
    "img{max-width:100%;border:2px solid #00E5FF;border-radius:10px;box-shadow:0 0 15px rgba(0,229,255,0.4);}"
    ".btn{display:inline-block;padding:8px 16px;margin:5px;background:#0284c7;color:#fff;text-decoration:none;border-radius:5px;font-weight:bold;}"
    "</style></head><body>"
    "<h2>📷 ESP32-CAM WEBSOCKET & MJPEG FEED</h2>"
    "<img id='cam' />"
    "<div>"
    "  <a href='/capture' target='_blank' class='btn'>📸 Single Snapshot (/capture)</a>"
    "  <a id='streamLink' target='_blank' class='btn'>🎥 MJPEG Stream (:81/stream)</a>"
    "</div>"
    "<script>"
    "const stream='http://'+location.hostname+':81/stream';"
    "document.getElementById('cam').src=stream;"
    "document.getElementById('streamLink').href=stream;"
    "</script>"
    "</body></html>";

  httpd_resp_set_type(req, "text/html");
  return httpd_resp_send(req, html, strlen(html));
}

void startCameraServer() {
  httpd_config_t control_config = HTTPD_DEFAULT_CONFIG();
  control_config.server_port = 80;

  httpd_uri_t index_uri   = { .uri = "/",        .method = HTTP_GET, .handler = index_handler,   .user_ctx = NULL };
  httpd_uri_t stream_uri  = { .uri = "/stream",   .method = HTTP_GET, .handler = stream_handler,  .user_ctx = NULL };
  httpd_uri_t capture_uri = { .uri = "/capture",  .method = HTTP_GET, .handler = capture_handler, .user_ctx = NULL };
  httpd_uri_t ws_uri      = { .uri = "/ws",       .method = HTTP_GET, .handler = ws_camera_handler, .user_ctx = NULL, .is_websocket = true };

  if (httpd_start(&control_httpd, &control_config) == ESP_OK) {
    httpd_register_uri_handler(control_httpd, &index_uri);
    httpd_register_uri_handler(control_httpd, &capture_uri);
    httpd_register_uri_handler(control_httpd, &ws_uri);
    Serial.println(">> [HTTP SERVER] Port 80: /ws, /capture, /");
  } else {
    Serial.println("!! [HTTP SERVER] Control server start failed!");
  }

  // The MJPEG handler never returns while a client is watching. Running it on
  // its own server prevents video from blocking capture and control requests.
  httpd_config_t stream_config = HTTPD_DEFAULT_CONFIG();
  stream_config.server_port = 81;
  stream_config.ctrl_port = control_config.ctrl_port + 1;
  if (httpd_start(&stream_httpd, &stream_config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &stream_uri);
    Serial.println(">> [STREAM SERVER] Port 81: /stream");
  } else {
    Serial.println("!! [STREAM SERVER] Start failed!");
  }
}

// ----------------------------------------------------------------------------
// 7. SETUP & INITIALIZATION
// ----------------------------------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=======================================================");
  Serial.println("  PROJECT SAVER — ESP32-CAM WEBSOCKET FIRMWARE");
  Serial.println("=======================================================");

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0       = Y2_GPIO_NUM;
  config.pin_d1       = Y3_GPIO_NUM;
  config.pin_d2       = Y4_GPIO_NUM;
  config.pin_d3       = Y5_GPIO_NUM;
  config.pin_d4       = Y6_GPIO_NUM;
  config.pin_d5       = Y7_GPIO_NUM;
  config.pin_d6       = Y8_GPIO_NUM;
  config.pin_d7       = Y9_GPIO_NUM;
  config.pin_xclk     = XCLK_GPIO_NUM;
  config.pin_pclk     = PCLK_GPIO_NUM;
  config.pin_vsync    = VSYNC_GPIO_NUM;
  config.pin_href     = HREF_GPIO_NUM;
  config.pin_sscb_sda = SIOD_GPIO_NUM;
  config.pin_sscb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn     = PWDN_GPIO_NUM;
  config.pin_reset    = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode    = CAMERA_GRAB_LATEST;

  if (psramFound()) {
    Serial.println(">> [PSRAM] Resolution: QVGA (320x240)");
    config.frame_size = FRAMESIZE_QVGA;
    // Slightly stronger JPEG compression keeps 30 FPS practical over Wi-Fi.
    config.jpeg_quality = 15;
    config.fb_count = 2;
  } else {
    Serial.println("!! [PSRAM] NO PSRAM Found. Resolution: QQVGA (160x120)");
    config.frame_size = FRAMESIZE_QQVGA;
    config.jpeg_quality = 15;
    config.fb_count = 1;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("!! [CAMERA INIT ERROR] Code 0x%x\n", err);
    return;
  }

  sensor_t * s = esp_camera_sensor_get();
  if (s != NULL) {
    s->set_vflip(s, 1);
    s->set_hmirror(s, 1);
  }

#if USE_STATION_MODE
  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);
  WiFi.begin(ssid, password);
  Serial.print(">> [WI-FI] Connecting to "); Serial.println(ssid);
  int retry = 0;
  while (WiFi.status() != WL_CONNECTED && retry < 30) {
    delay(500);
    Serial.print(".");
    retry++;
  }
  if (WiFi.status() == WL_CONNECTED) {
    WiFi.setSleep(false);
    Serial.println("\n>> [WI-FI] CONNECTED SUCCESSFULLY!");
    Serial.print("   IP Address: http://"); Serial.println(WiFi.localIP());
    Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.localIP()); Serial.println("/ws");
  } else {
    Serial.println("\n!! [WI-FI] Connection Failed!");
  }
#else
  WiFi.softAP(ssid, password);
  Serial.print("   WebSocket URL: ws://"); Serial.print(WiFi.softAPIP()); Serial.println("/ws");
#endif

  startCameraServer();
  Serial.println("=======================================================\n");
}

void loop() {
  const uint32_t now = millis();
  if (ws_client_fd >= 0 && now - last_ws_frame_ms >= WS_FRAME_INTERVAL_MS) {
    last_ws_frame_ms = now;
    broadcast_ws_frame();
  }
  delay(1); // Yield to Wi-Fi and the HTTP server without limiting FPS.
}
