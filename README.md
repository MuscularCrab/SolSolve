# SolSolve (Android)

A snapshot-based assistant that analyzes a Klondike (Draw 3) solitaire game running on another phone and suggests steps to solve it in as few moves as possible.

## Status
- App: Jetpack Compose + CameraX snapshot flow with scanning overlay, scan status, internal log, and friendly UI.
- Heuristics: OpenCV contour pass (optional) for quick card/pile cues.
- Models: Pluggable TFLite pipeline (detector + rank/suit, or 52-class card classifier). Model assets and docs included.

## Features
- Snapshot-first UX: Tap Take Snapshot, then Solving Mode with a mini preview.
- Scan status + internal log: Visible reasoning steps, partial detection warnings, and errors.
- Demo mode: Optional sample suggestions that vary with detected layout (for demos before ML models are ready).
- Delete all: Clears cached snapshots and in-memory state.

## Requirements
- Android Studio Koala or newer
- Android SDK: compileSdk 36, minSdk 24, Kotlin 2.0.x
- Device with camera (tested path designed for Galaxy S23 Ultra)

## Build & Run
1) Open in Android Studio and let Gradle sync.
2) If build complains about SDK path, create `local.properties` in project root:
```
sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```
3) Run on a device (recommended) or emulator with camera.

CLI build:
```
./gradlew :app:assembleDebug
```

## Permissions
- Camera (`android.permission.CAMERA`)
- The app shows an explicit "Grant Camera Permission" button before opening the camera.

## App Flow
1) Preview Mode
   - Grant camera permission
   - Tap "Take Snapshot"
2) Solving Mode
   - Shows a mini snapshot preview
   - Internal "Solving…" indicator while the app reasons
   - Scan log lists detection metrics, warnings, and step-by-step reasoning
   - Refresh to rescan; Delete All to clear cache and memory

## Models (TFLite)
Two options are supported for card identity:
- Rank + Suit classifiers (corner crops)
- 52-class classifier (`AC, 10H, QS, 4C, …`)

Place models and config under:
```
app/src/main/assets/models/
```
See the detailed docs in:
```
app/src/main/assets/models/README.md
```

Quick summary:
- detector.tflite (object detector for `card_face_up`, `pile_slot_*`, …)
- rank.tflite + suit.tflite OR a single card52.tflite
- config.json describing the file names, labels, input sizes, and thresholds

### Example config.json (card52)
```json
{
  "detector": {
    "file": "detector.tflite",
    "labels": ["card_face_up","card_back","pile_slot_tableau","pile_slot_foundation"],
    "inputSize": 416,
    "confidenceThreshold": 0.35,
    "nmsIoU": 0.45
  },
  "card52": {
    "file": "card52.tflite",
    "labels": [
      "AC","2C","3C","4C","5C","6C","7C","8C","9C","10C","JC","QC","KC",
      "AD","2D","3D","4D","5D","6D","7D","8D","9D","10D","JD","QD","KD",
      "AH","2H","3H","4H","5H","6H","7H","8H","9H","10H","JH","QH","KH",
      "AS","2S","3S","4S","5S","6S","7S","8S","9S","10S","JS","QS","KS"
    ],
    "inputSize": 64,
    "confidenceThreshold": 0.6
  }
}
```

## Data & Training (Klondike Draw 3)
- Target 400–800 images (more is better) from real devices and themes
- Label detector boxes for `card_face_up`, `pile_slot_tableau`, `pile_slot_foundation` (and optionally `card_back`, `pile_slot_waste/stock`)
- For classification, crop top-left rank/suit corners of detected face-up cards
- Training options:
  - No-code: Roboflow detection + classification, export TFLite (int8)
  - Local: YOLOv8n for detection; Keras/TFLite for rank/suit or 52-class
- See `app/src/main/assets/models/README.md` for a complete guide

## Architecture
- UI: Jetpack Compose (Material 3)
- Camera: CameraX (Preview + ImageCapture)
- Analysis: Snapshot pipeline, OpenCV (optional heuristics), pluggable TFLite inference
- State & Logs: Compose state holders with visible "Scan log" output

## Performance
- Snapshot decode: downsample + RGB_565
- Detector input ~416, card corner crops 64×64
- S23 Ultra: detector + 30 crops typically <100ms total on-device (with NNAPI/GPU)

## Troubleshooting
- Crash on launch with scroll error: fixed by using LazyColumn (avoid nested verticalScroll)
- Camera bind failures: ensure permission granted; logs will display errors
- Rotated snapshots: EXIF is read; image rotated to portrait before analysis
- No moves: When no model is installed, moves are disabled; enable Demo for sample suggestions

## Roadmap
- Integrate TFLite loader + inference; switch Demo off by default
- Solver engine for Klondike Draw 3 using recognized state
- Annotated overlays for QA (toggle) and confidence summaries
- Batch testing and metrics view

## Privacy & Licensing
- Do not include private datasets or trained models in the repo unless cleared
- Ensure rights to any screenshots/video used for training
- See `LICENSE` for repository license details

## Contributing
- PRs welcome for UI improvements, detectors, and solver logic
- Please include device info and logs for any bug reports
