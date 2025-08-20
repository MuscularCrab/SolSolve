SolSolve models (Klondike Draw 3)

Place your exported TFLite models and configuration here:

- detector.tflite   → Object detector for cards/piles
- rank.tflite       → Rank classifier (A,2,3,…,K)
- suit.tflite       → Suit classifier (clubs,diamonds,hearts,spades)
- config.json       → Model metadata and thresholds (see template below)

Notes
- Keep file names matching the values in config.json.
- Use int8-quantized TFLite for best on-device performance.
- If you retrain, just replace the .tflite files and adjust thresholds.

config.json template
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

Delivery checklist
- detector.tflite
- rank.tflite
- suit.tflite
- config.json


