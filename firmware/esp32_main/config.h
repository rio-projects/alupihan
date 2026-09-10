#ifndef CONFIG_H
#define CONFIG_H

// ============================================================================
// PROJECT ALUPIHAN - ESP32 MAIN CONTROLLER HARDWARE PIN DEFINITIONS & CONFIG
// Author: Sir Vince Zamora & Sir Heinrich Del Rosario
// Target Hardware: ESP32-WROOM-32 NodeMCU / DevKit
// ============================================================================

// ----------------------------------------------------------------------------
// WI-FI NETWORK CONFIGURATION
// ----------------------------------------------------------------------------
#define USE_STATION_MODE    true  // true = Home Wi-Fi Network | false = AP Hotspot Mode
#define USE_STATIC_IP       true  // true = Fixed Static IP (192.168.18.88)

// Station Mode Credentials (Home Wi-Fi)
#define STATION_SSID        "HUAWEI-PBZM"
#define STATION_PASS        "JfvyqXrK"

// Access Point Mode Credentials (Fallback Hotspot)
#define AP_SSID             "Alupihan_Rover"
#define AP_PASS             "rover1234"

// Network Ports
#define WEBSOCKET_PORT      8080
#define HTTP_SERVER_PORT    80

// ----------------------------------------------------------------------------
// TB6612FNG MOTOR DRIVER PINS (4WD Differential Wheel Drive)
// ----------------------------------------------------------------------------
#define PIN_MOTOR_PWMA      14   // Left Motors Speed PWM (GPIO 14)
#define PIN_MOTOR_AIN1      26   // Left Motors Direction 1 (GPIO 26)
#define PIN_MOTOR_AIN2      27   // Left Motors Direction 2 (GPIO 27)

#define PIN_MOTOR_PWMB      25   // Right Motors Speed PWM (GPIO 25)
#define PIN_MOTOR_BIN1      12   // Right Motors Direction 1 (GPIO 12)
#define PIN_MOTOR_BIN2      13   // Right Motors Direction 2 (GPIO 13)

#define PIN_MOTOR_STBY      33   // Motor Driver Standby Enable (GPIO 33 - Active HIGH)

// PWM Channel Properties & Motor Speed Limiter (60% Max Speed for smooth driving)
#define PWM_FREQ_MOTORS         5000 // 5 kHz PWM frequency
#define PWM_RES_MOTORS          8    // 8-bit resolution (0 - 255)
#define PWM_CHAN_MOTORS_A       6    // LEDC Channel 6 for Left Motors PWMA
#define PWM_CHAN_MOTORS_B       7    // LEDC Channel 7 for Right Motors PWMB
#define MOTOR_MAX_SPEED_SCALE   0.60f // 60% max power multiplier for slower driving control

// ----------------------------------------------------------------------------
// SERVO MOTOR PINS & PWM SETTINGS
// ----------------------------------------------------------------------------
#define PIN_SERVO_PAN       18   // Horizontal Pan Servo (GPIO 18 / D18)
#define PIN_SERVO_TILT      19   // Vertical Tilt Servo (GPIO 19 / D19)
#define PIN_SERVO_STEERING  16   // TowerPro High-Torque Steering Servo (GPIO 16 / D16)

#define SERVO_MIN_PULSE_US  500  // Standard servo min pulse in microseconds
#define SERVO_MAX_PULSE_US  2400 // Standard servo max pulse in microseconds

// Default Servo Angles & Calibration Trim Offsets (Degrees)
#define PAN_DEFAULT_ANGLE       90  // Default Pan position (90° - Centered Left/Right)
#define TILT_DEFAULT_ANGLE      30  // Default Tilt position (30° - Resting view angle)

#define TILT_MIN_ANGLE          25  // Minimum tilt limit (prevents servo stall/buzzing against pan bracket)
#define TILT_MAX_ANGLE          160 // Maximum tilt limit (prevents servo stall/buzzing against top frame)

#define STEERING_CENTER_DEFAULT 90
#define STEERING_DEFLECTION_MAX 40  // +/- 40 degrees steer range

#define PAN_TRIM_OFFSET         0    // Pan servo trim
#define TILT_TRIM_OFFSET        0    // Tilt servo trim
#define STEER_TRIM_OFFSET       0    // Steering servo trim

// Servo Direction Inversion (Invert axes to match app swipes)
#define INVERT_PAN_DIRECTION    false // false: joystick right = turn right, joystick left = turn left
#define INVERT_TILT_DIRECTION   false // false: joystick up = look up, joystick down = look down

// Gimbal Motion Smoothing (Slew Rate Limit - Fast & Responsive Camera Movement)
#define GIMBAL_SMOOTHING_ENABLE true  // Enable smooth camera interpolation
#define GIMBAL_MAX_STEP_DEG     1.5f  // Degrees per 20ms tick (1.5f = 75°/sec Fast & Responsive)

// ----------------------------------------------------------------------------
// AUXILIARY HARDWARE PINS (Spotlight Relay & Piezo Horn)
// ----------------------------------------------------------------------------
#define PIN_RELAY_SPOTLIGHT 2    // Spotlight Relay Pin (GPIO 4 / D4)
#define PIN_PIEZO_BUZZER    21   // Active Piezo Horn Pin (GPIO 21 / D21)

// Relay Active Trigger Level:
// LOW = Active-LOW trigger mode (0V GND = ON when triggered, 3.3V = OFF)
#define RELAY_ACTIVE_STATE  LOW

#endif // CONFIG_H