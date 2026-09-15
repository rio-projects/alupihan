# Offline YOLO person detection

## Implemented pipeline

ESP32-CAM MJPEG → decoded frame → one replaceable pending bitmap → YOLO11 Large
FP16 TFLite → COCO person filtering → NMS → 3-of-5 confirmation → rescue controller
→ WebSocket stop, then horn → operator response.

The RGB heuristic is removed. Inference and rendering use separate workers. The
inference worker owns its bitmap copies and all interpreter/GPU resources. No
model download, cloud call, or Play services initialization occurs on the phone.

### Model contract

- YOLO11 **Large**, COCO detection, `person` class index 0.
- Asset: `SaverRover/app/src/main/assets/yolo11l_float16.tflite`.
- Input: float32 RGB `[1,640,640,3]`, normalized to 0–1, aspect-preserving resize
  with centered RGB 114 padding. FP16 describes the weights, not input buffer type.
- Output: float32 `[1,84,8400]`: normalized center-x/center-y/width/height and 80
  class probabilities. No objectness channel, embedded NMS, or sigmoid needed.
- Export pinned to Ultralytics 8.3.161. Newer exporters/model generations can use
  different layouts/coordinates and are not drop-in replacements.
- LiteRT 1.4.2 is bundled, with GPU compatibility checks and CPU fallback. GPU
  creation, invocation, and destruction occur on one thread. Runtime tensor shape
  and type checks reject incompatible assets with a visible model error.

Reference: [Ultralytics export](https://docs.ultralytics.com/modes/export/),
[pinned YOLO output implementation](https://github.com/ultralytics/ultralytics/blob/v8.3.161/ultralytics/nn/modules/head.py),
[Google standalone GPU runtime](https://ai.google.dev/edge/litert/android/gpu).
The Ultralytics model/exporter is subject to its upstream AGPL-3.0 or enterprise
license; see [Ultralytics licensing](https://www.ultralytics.com/license).

## Reproduce the asset

Use Python 3.11 in a virtual environment on a development computer with several
GB of free RAM/disk. These development downloads are not required during operation.

```sh
python3.11 -m venv .venv
. .venv/bin/activate
pip install torch==2.7.1 torchvision==0.22.1 --index-url https://download.pytorch.org/whl/cpu
pip install -r tools/model-requirements.txt
mkdir -p model-export
cd model-export
curl -fL https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11l.pt -o yolo11l.pt
python ../tools/export_yolo.py --weights yolo11l.pt
```

The script validates float IO, shapes, and finite inference output, then installs
the model and a SHA-256 provenance manifest in Android assets. The application
uses the local asset; an absent/invalid model displays **HUMAN DETECTION UNAVAILABLE**.

## Configuration and confirmation

`DetectionConfig` controls confidence (0.60), NMS IoU (0.45), positive votes (3),
window size (5), minimum start interval (200 ms), and maximum result age (1500 ms).
It can be supplied to `HumanDetector(context, config)` during development.
The default target is 5 evaluations/second. Slower inference reduces actual FPS;
missed ticks do not accumulate. Tune only after measuring the phone.

Each evaluated frame contributes a positive/negative vote. A current positive
frame plus at least three positives in the latest five evaluations confirms a
person. Confirmation can occur on the third evaluation; five initial frames are
not required. Multiple retained people produce multiple boxes. Non-person winning
classes, low scores, duplicate boxes, invalid boxes, and padding-only boxes do not
trigger rescues. Camera/session changes reset the evidence. Stale results are
discarded, and a slow-inference warning distinguishes them from a clear scene.
Boxes are mapped through letterboxing, display center-cropping, and mirroring.

## Operator control

`RescueController` is the sole authority for joystick movement commands:

- **MONITORING:** manual driving; confirmed detections latch rescue halt.
- **RESCUE_HALT:** stop is queued before horn-on; movement requests become zero.
- **MANUAL_OVERRIDE:** operator explicitly disables automatic rescue halts and
  retains manual driving. Detection and its status remain visible.
- **EMERGENCY_STOP:** blocks movement and cannot be bypassed by manual override
  or rescue acknowledgment. The operator must use **RESET STOP**.

**MARK LOCATION** and **MARK AS RESCUED** stop, silence the horn, and acknowledge
the alert. Automatic alerts rearm only after five consecutive evaluated clear
frames, avoiding immediate retriggering for the same visible subject. Location
marks retain the existing timestamp/IP behavior; they are not GPS coordinates.
**RESUME AI HALTS** explicitly rearms detection from manual override.

Pausing sends stop/horn-off and disconnects; resuming/reconnecting starts with zero
motor output and restores the horn if a rescue remains latched. Old connection
callbacks cannot clear replacement sockets. Commands are not queued while a
WebSocket is still connecting. AI errors remain visible; manual driving remains
available. Camera errors are distinguished from no-person observations.

## Measurements and limits

The UI and `SaverAI` logcat tag expose camera/AI FPS, mean/max preprocessing +
inference + postprocessing latency, initialization time, frame receive-to-result
age, confidence, person count, votes, and selected backend. Every two seconds,
device stats show process CPU usage (100% = one full core), process PSS RAM,
**battery** temperature, and Android thermal status (0–6, Android 10+).
Battery temperature is not SoC temperature. GPU/NPU utilization is not available
through a portable public API; use Android/vendor profiling tools on the phone.
No NPU performance or acceleration is claimed.

`SaverSafety` logs stop queue acceptance and monotonic timestamps. Acceptance
means queued in the local WebSocket, not acknowledged motor stoppage. Frame age
starts when a complete JPEG reaches the app; ESP32 capture/transport time is not
measured. True camera-event → confirmed detection → physical-stop latency requires
hardware observation or synchronized capture/firmware instrumentation.

This integration does not resolve the separate firmware review findings (GPIO34/
35 output assignments, missing drive-command watchdog, unsupported `towerpro`).
In particular, a network failure cannot guarantee a physical stop with the current
firmware. Validate firmware safety independently before driving around people.

## Validation

Run `./gradlew :app:testDebugUnitTest :app:assembleDebug` from `SaverRover`.
Unit tests exercise temporal confirmation/reset, letterbox mapping, person-only
filtering, NMS, multiple people, and rescue/override/emergency-stop priority.

On an ARM64 deployment phone:

1. Start with internet disabled and the rover Wi-Fi connected; confirm local model
   loading, reported GPU/CPU backend, live camera, and correct mirrored boxes.
2. Test standing/walking/lying/crouching people, multiple subjects, partial
   occlusions, distances, low light, spotlight, motion blur, and compression.
3. Test empty scenes containing wood, soil, vegetation, shadows, and skin-like
   colors. Record false positives and false negatives; COCO person detection is
   not a guarantee of detecting isolated body parts or severely obscured people.
4. Confirm one-frame detections do not halt. Check 3-of-5, acknowledgment rearm,
   manual override, emergency stop, and offline/model-error indicators.
5. Change IPs, interrupt Wi-Fi, pause/resume repeatedly, and rotate/recreate the
   activity during inference. Check that no stale results or queued movement run.
6. Measure end-to-end latency, RAM, CPU, temperature, thermal throttling and frame
   rates over a sustained 20-minute run. Measure physical stop latency separately.

Performance targets are unverified until this phone/rover validation is complete.
