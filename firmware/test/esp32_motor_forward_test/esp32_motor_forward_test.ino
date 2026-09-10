/*
 * ============================================================================
 * Project Alupihan — Standalone Motor Driver Forward Test
 * Target Board: Main ESP32 (NodeMCU / ESP32 WROOM-32)
 *
 * Purpose: Simple isolated test script to run all 4WD motors FORWARD.
 *          No Wi-Fi, WebSockets, or Servos required!
 *
 * Motor Driver Pinout Mapping (TB6612FNG / L298N):
 *   - Left Motors Speed (PWMA):  GPIO 14
 *   - Left Motors Dir 1 (AIN1):  GPIO 26
 *   - Left Motors Dir 2 (AIN2):  GPIO 27
 *   - Right Motors Speed (PWMB): GPIO 25
 *   - Right Motors Dir 1 (BIN1): GPIO 12
 *   - Right Motors Dir 2 (BIN2): GPIO 13
 *   - Driver Enable (STBY):      GPIO 33
 * ============================================================================
 */

// Pin Definitions
#define PIN_PWMA 14 // Left Speed PWM
#define PIN_AIN1 26 // Left Dir 1
#define PIN_AIN2 27 // Left Dir 2

#define PIN_PWMB 25 // Right Speed PWM
#define PIN_BIN1 12 // Right Dir 1
#define PIN_BIN2 13 // Right Dir 2

#define PIN_STBY 33 // Motor Driver Standby Enable

// ESP32 PWM LEDC Settings
#define PWM_FREQ 5000 // 5 kHz PWM frequency
#define PWM_RES  8    // 8-bit resolution (0 - 255)
#define CHAN_A   0    // LEDC Channel 0 for Left Speed
#define CHAN_B   1    // LEDC Channel 1 for Right Speed

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("    PROJECT ALUPIHAN — MOTOR FORWARD TEST");
  Serial.println("=======================================================");

  // 1. Configure Direction & Standby Pins as OUTPUT
  pinMode(PIN_AIN1, OUTPUT);
  pinMode(PIN_AIN2, OUTPUT);
  pinMode(PIN_BIN1, OUTPUT);
  pinMode(PIN_BIN2, OUTPUT);
  pinMode(PIN_STBY, OUTPUT);

  // 2. Enable Motor Driver Standby (STBY = HIGH)
  digitalWrite(PIN_STBY, HIGH);

  // 3. Set Direction FORWARD
  // Left Motors Forward
  digitalWrite(PIN_AIN1, HIGH);
  digitalWrite(PIN_AIN2, LOW);

  // Right Motors Forward
  digitalWrite(PIN_BIN1, HIGH);
  digitalWrite(PIN_BIN2, LOW);

  // 4. Drive Motors at ~80% Speed (200 out of 255)
#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
  // ESP32 Arduino Core 3.x+ API
  ledcAttach(PIN_PWMA, PWM_FREQ, PWM_RES);
  ledcAttach(PIN_PWMB, PWM_FREQ, PWM_RES);
  ledcWrite(PIN_PWMA, 200); // Left Speed PWM
  ledcWrite(PIN_PWMB, 200); // Right Speed PWM
#else
  // ESP32 Arduino Core 2.x API
  ledcSetup(CHAN_A, PWM_FREQ, PWM_RES);
  ledcAttachPin(PIN_PWMA, CHAN_A);
  ledcSetup(CHAN_B, PWM_FREQ, PWM_RES);
  ledcAttachPin(PIN_PWMB, CHAN_B);
  ledcWrite(CHAN_A, 200); // Left Speed PWM
  ledcWrite(CHAN_B, 200); // Right Speed PWM
#endif

  Serial.println(">> [MOTORS] Status: RUNNING FORWARD (Speed: 200/255)");
  Serial.println("=======================================================\n");
}

void loop() {
  // Keep motors running forward continuously
  delay(1000);
}
