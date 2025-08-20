SolSolve models (Klondike Draw 3)

This folder contains on-device ML models and configuration used to detect cards, piles, and card identities from a snapshot of a Klondike Draw 3 game. The app expects models in TFLite format and a `config.json` describing file names, labels, and thresholds.

### Folder contents
- detector.tflite        — Object detector for cards and (optionally) pile slots
- rank.tflite            — Rank classifier (A,2,3,…,K) for corner crops
- suit.tflite            — Suit classifier (clubs,diamonds,hearts,spades) for corner crops
- OR card52.tflite       — Single classifier with 52 classes like "4C" (4 of Clubs)
- config.json            — Model metadata and thresholds (see templates)

Only one classification approach is required: either rank+suit or card52. Keep file names matching `config.json`.

### Recommended pipeline
1) Detector (YOLO/SSD TFLite): detects bounding boxes for face-up cards and (optionally) pile slots.
2a) Rank+Suit classifiers: crop the top-left corner from each detected face-up card, run rank and suit classifiers separately; combine.
2b) 52-class classifier (card52): same crop, run a single classifier that outputs labels like `AC`, `10H`, `QS`, `4C`.
3) Assign cards to tableau columns by x-clustering; order within a column by y.
4) Build the Klondike Draw 3 state (tableau, waste, stock, foundations) and compute moves.

### Label formats
- Detector classes (suggested):
  - `card_face_up`
  - `card_back` (optional)
  - `pile_slot_tableau` (one class for all 7 columns)
  - `pile_slot_foundation` (one class for all 4)
  - Optionally: `pile_slot_waste`, `pile_slot_stock`

- Rank/Suit classification labels:
  - Rank classes: `A,2,3,4,5,6,7,8,9,10,J,Q,K`
  - Suit classes: `clubs,diamonds,hearts,spades`

- 52-class (card52) labels (preferred if you will train a single classifier):
  - Format: `^(A|[2-9]|10|J|Q|K)[CDHS]$`
  - Suits: `C=Clubs, D=Diamonds, H=Hearts, S=Spades`
  - Examples: `AC, 10H, QD, 7S, 4C`

### config.json templates

Rank + Suit variant:
```
{
  "detector": {
    "file": "detector.tflite",
    "labels": [
      "card_face_up",
      "card_back",
      "pile_slot_tableau",
      "pile_slot_foundation"
    ],
    "inputSize": 416,
    "confidenceThreshold": 0.35,
    "nmsIoU": 0.45
  },
  "rank": {
    "file": "rank.tflite",
    "labels": ["A","2","3","4","5","6","7","8","9","10","J","Q","K"],
    "inputSize": 64,
    "confidenceThreshold": 0.6
  },
  "suit": {
    "file": "suit.tflite",
    "labels": ["clubs","diamonds","hearts","spades"],
    "inputSize": 64,
    "confidenceThreshold": 0.6
  }
}
```

52-class (card52) variant:
```
{
  "detector": {
    "file": "detector.tflite",
    "labels": [
      "card_face_up",
      "card_back",
      "pile_slot_tableau",
      "pile_slot_foundation"
    ],
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

### Placement and file naming
- Put `.tflite` models and `config.json` in this folder: `app/src/main/assets/models/`
- File names in `config.json` must match the actual file names.
- Prefer int8-quantized models for speed and battery.

### Data collection (fastest path)
- Record short videos on real devices playing Klondike Draw 3. Export frames to images.
- Target 400–800 images total (more is better):
  - 60–70% full-tableau views; 20–30% partial/angled; 10% challenging (low light/blur).
  - Include different themes/backgrounds and devices; include waste/stock actions (Draw 3).

### Labeling guidelines (detector)
- `card_face_up`: tight bounding box including card border (exclude heavy shadows if possible).
- `pile_slot_*`: box centered on where the card sits; consistent size per slot within an image.
- Occlusions: still label visible face-up cards if enough area is visible to recognize identity.
- Orientation: portrait snapshots preferred; app auto-rotates using EXIF when needed.

### Generating corner crops for classification
If using rank+suit or 52-class classification, create crops from the top-left corner of each face-up card. You can automate this with a small script (example logic):
```
for each detected face-up card box:
  expand a small margin
  crop top-left region (e.g., 28–32% width, 28–32% height of the box)
  resize to 64x64, grayscale or RGB as your model expects
  save to class folder (e.g., 4C/0001.png)
```

### Training options
- No-code (Roboflow):
  - Create Object Detection project → upload & label detector classes.
  - Train with default augments, `inputSize≈416`, `epochs≈50–100`.
  - Export as `TFLite (Int8)`.
  - Create Classification project(s) for `rank` and `suit`, or a single `card52` project. Train and export TFLite (Int8).

- Local (YOLOv8 + Keras):
  - Detector (YOLOv8n):
    - `pip install ultralytics`
    - `yolo detect train data=solitaire.yaml model=yolov8n.pt imgsz=416 epochs=80 batch=32`
    - Export: `yolo export model=best.pt format=tflite int8=True`
  - Classifier (rank/suit or 52-class):
    - Small CNN on 64×64 crops, with light augments (brightness/contrast, ±5° rotation, slight scale).
    - Export with TFLiteConverter (int8). Ensure label order in `config.json` matches training.

### Runtime thresholds (starting points)
- Detector: `confidenceThreshold=0.35`, `nmsIoU=0.45`, `inputSize=416`.
- Classifiers: `confidenceThreshold=0.6`, `inputSize=64`.
Tune these based on validation results.

### Performance guidance (e.g., Galaxy S23 Ultra)
- Detector (YOLOv8n at 416): typically <10–20 ms with NNAPI/GPU.
- Classify ~30 cards at 64×64: <30 ms total.
- Entire pipeline on a snapshot: usually <100 ms.

### Troubleshooting
- Model not loaded: verify file names & paths; check `config.json` matches.
- Wrong labels: ensure label order in `config.json` is identical to training export.
- Rotated previews: ensure EXIF orientation is set by camera; the app corrects rotation before analysis.
- Poor detection: add more varied training images (lighting/themes), adjust thresholds, retrain.

### Versioning
- Keep copies of previous models (e.g., `detector_v2.tflite`) during iteration.
- Update `config.json` to point to the current files.

### Privacy and licensing
- Ensure you own/are permitted to use the screenshots and game assets in your dataset.
- Store any private datasets securely; do not commit datasets into the app repo.



