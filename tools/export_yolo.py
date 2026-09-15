"""Export the exact offline model contract consumed by YoloModel.kt (Python 3.11)."""
from pathlib import Path
import hashlib
import json
import shutil
import argparse


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--weights", default="yolo11l.pt")
    args = parser.parse_args()
    from ultralytics import YOLO
    import tensorflow as tf
    import numpy as np

    model = YOLO(args.weights)
    if (model.task != "detect" or len(model.names) != 80 or model.names[0] != "person"
            or model.model.yaml.get("scale") != "l"):
        raise ValueError("Expected YOLO11 Large COCO detection weights (80 classes; person=0)")
    exported = Path(model.export(format="tflite", imgsz=640, half=True, nms=False, batch=1, device="cpu"))
    # Ultralytics may return the FP32 path despite writing both precisions.
    fp16 = exported.parent / (Path(args.weights).stem + "_float16.tflite")
    if not fp16.is_file():
        raise FileNotFoundError(f"Missing FP16 export: {fp16}")
    interpreter = tf.lite.Interpreter(model_path=str(fp16))
    interpreter.allocate_tensors()
    inputs, outputs = interpreter.get_input_details(), interpreter.get_output_details()
    assert len(inputs) == len(outputs) == 1
    assert list(inputs[0]["shape"]) == [1, 640, 640, 3]
    assert list(outputs[0]["shape"]) == [1, 84, 8400]
    assert inputs[0]["dtype"] == outputs[0]["dtype"] == np.float32
    interpreter.set_tensor(inputs[0]["index"], np.zeros((1, 640, 640, 3), dtype=np.float32))
    interpreter.invoke()
    assert np.isfinite(interpreter.get_tensor(outputs[0]["index"])).all()

    assets = Path(__file__).resolve().parents[1] / "SaverRover/app/src/main/assets"
    assets.mkdir(parents=True, exist_ok=True)
    destination = assets / "yolo11l_float16.tflite"
    shutil.copyfile(fp16, destination)
    manifest = {
        "model": "YOLO11 Large", "task": "detect", "dataset": "COCO", "person_class": 0,
        "ultralytics": "8.3.161", "precision": "FP16 weights / FP32 IO",
        "input": [1, 640, 640, 3], "output": [1, 84, 8400],
        "box_format": "normalized cx,cy,w,h in letterboxed input", "embedded_nms": False,
        "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(),
        "weights_sha256": hashlib.sha256(Path(args.weights).read_bytes()).hexdigest(),
        "source": "https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11l.pt",
    }
    (assets / "yolo11l_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Validated and installed {destination} ({destination.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
