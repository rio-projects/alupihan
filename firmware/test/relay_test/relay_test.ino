/*
 * ============================================================================
 * PROJECT SAVER — SIMPLE RELAY HARDWARE BLINK TEST
 * Target Pin: GPIO 4 (D4)
 * ============================================================================
 */

#define PIN_RELAY 4 // GPIO 4 (D4)

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=======================================================");
  Serial.println("   SAVER — SIMPLE RELAY BLINK TEST (GPIO 4 / D4)");
  Serial.println("=======================================================");
  
  pinMode(PIN_RELAY, OUTPUT);
  digitalWrite(PIN_RELAY, HIGH); // Start OFF
}

void loop() {
  // 1. RELAY ON
  Serial.println("💡 RELAY -> ON  (LOW / 0V)  [CLICK!]");
  digitalWrite(PIN_RELAY, LOW);
  delay(1000);

  // 2. RELAY OFF
  Serial.println("💡 RELAY -> OFF (HIGH / 3.3V) [CLICK!]");
  digitalWrite(PIN_RELAY, HIGH);
  delay(1000);
}