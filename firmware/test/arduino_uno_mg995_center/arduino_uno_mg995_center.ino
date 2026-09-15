/*
 * ============================================================================
 * Project Saver — Utility Tool: MG995 Servo Steering Center Calibration
 * Target Board: Arduino Uno (ATmega328P)
 *
 * Purpose: Moves the TowerPro MG995 steering servo on Pin 6 to exactly 90°
 *          so you can physically attach the steering horn centered.
 *
 * Hardware Wiring:
 *   - MG995 Servo Signal (Orange/Yellow): Pin 6 on Arduino Uno
 *   - MG995 Servo VCC (Red): 5V Power Supply (External 5V recommended for MG995)
 *   - MG995 Servo GND (Brown/Black): GND (Shared with Arduino GND)
 * ============================================================================
 */

#include <Servo.h>

const int SERVO_PIN = 6;
const int CENTER_ANGLE = 95;    // Straight ahead center position
const int HARD_LEFT_ANGLE = 65;  // 95 - 30 deg left
const int HARD_RIGHT_ANGLE = 125; // 95 + 30 deg right

Servo steeringServo;

void moveToAngleSmooth(int targetAngle, int speedMs = 15) {
  int currentAngle = steeringServo.read();
  if (currentAngle < targetAngle) {
    for (int pos = currentAngle; pos <= targetAngle; pos++) {
      steeringServo.write(pos);
      delay(speedMs);
    }
  } else {
    for (int pos = currentAngle; pos >= targetAngle; pos--) {
      steeringServo.write(pos);
      delay(speedMs);
    }
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\n=======================================================");
  Serial.println("   MG995 SERVO STEERING LEFT-RIGHT SWEEP TEST TOOL");
  Serial.println("=======================================================");

  // Attach MG995 servo on Pin 6
  steeringServo.attach(SERVO_PIN, 544, 2400);

  // Initialize at 95° Center
  steeringServo.write(CENTER_ANGLE);
  Serial.print(">> [INITIALIZE] Center position: ");
  Serial.print(CENTER_ANGLE);
  Serial.println("°");
  delay(2000);
}

void loop() {
  // 1. Move to CENTER (95°)
  Serial.print(">> [STEERING] Center Position -> ");
  Serial.print(CENTER_ANGLE);
  Serial.println("°");
  moveToAngleSmooth(CENTER_ANGLE);
  delay(2000);

  // 2. Move to HARD LEFT (65°)
  Serial.print("◀◀ [STEERING] Turning LEFT -> ");
  Serial.print(HARD_LEFT_ANGLE);
  Serial.println("°");
  moveToAngleSmooth(HARD_LEFT_ANGLE);
  delay(2000);

  // 3. Move back to CENTER (95°)
  Serial.print(">> [STEERING] Returning to Center -> ");
  Serial.print(CENTER_ANGLE);
  Serial.println("°");
  moveToAngleSmooth(CENTER_ANGLE);
  delay(1000);

  // 4. Move to HARD RIGHT (125°)
  Serial.print("▶▶ [STEERING] Turning RIGHT -> ");
  Serial.print(HARD_RIGHT_ANGLE);
  Serial.println("°");
  moveToAngleSmooth(HARD_RIGHT_ANGLE);
  delay(2000);
}
