# SolSolve Model Training Guide

This guide will help you create the required ML models for the SolSolve app.

## Quick Start Options

### Option 1: No-Code Training (Recommended for beginners)
**Use Roboflow** - A web-based platform that makes training easy:
1. Go to [Roboflow.com](https://roboflow.com)
2. Create a new Object Detection project
3. Upload 400-800 images of Klondike solitaire games
4. Label the images with bounding boxes for:
   - `card_face_up` - tight box around each face-up card
   - `card_back` - box around card backs
   - `pile_slot_tableau` - box around each of the 7 tableau columns
   - `pile_slot_foundation` - box around each of the 4 foundation slots
5. Train with default settings (YOLOv8, ~50-100 epochs)
6. Export as "TFLite (Int8)" format
7. Download and rename to `detector.tflite`

### Option 2: Local Training (For advanced users)
**Use YOLOv8 locally:**
```bash
# Install ultralytics
pip install ultralytics

# Train detection model
yolo detect train data=solitaire.yaml model=yolov8n.pt imgsz=416 epochs=80 batch=32

# Export to TFLite
yolo export model=best.pt format=tflite int8=True
```

## Data Collection Requirements

### Image Requirements
- **Total images**: 400-800 (more is better)
- **Distribution**:
  - 60-70%: Full tableau views
  - 20-30%: Partial/angled views
  - 10%: Challenging conditions (low light, blur, etc.)
- **Variety**: Include different themes, backgrounds, devices
- **Actions**: Include waste/stock actions (Draw 3)

### Labeling Guidelines
- **card_face_up**: Tight bounding box including card border
- **pile_slot_***: Box centered on where cards sit, consistent size per slot
- **Occlusions**: Label visible cards even if partially covered
- **Orientation**: Portrait snapshots preferred

## Classification Models (Rank + Suit)

### Data Preparation
1. For each detected face-up card, crop the top-left corner
2. Expand with small margin (28-32% of card width/height)
3. Resize to 64x64 pixels
4. Organize into class folders:
   ```
   rank/
   ├── A/
   ├── 2/
   ├── 3/
   └── ...
   
   suit/
   ├── clubs/
   ├── diamonds/
   ├── hearts/
   └── spades/
   ```

### Training
**Using Roboflow:**
1. Create Classification project for rank
2. Create Classification project for suit
3. Upload crops to respective projects
4. Train with default settings
5. Export as TFLite (Int8)

**Using Keras locally:**
```python
from tensorflow import keras
from tensorflow.keras import layers

# Simple CNN for classification
model = keras.Sequential([
    layers.Conv2D(32, 3, activation='relu', input_shape=(64, 64, 3)),
    layers.MaxPooling2D(),
    layers.Conv2D(64, 3, activation='relu'),
    layers.MaxPooling2D(),
    layers.Conv2D(64, 3, activation='relu'),
    layers.Flatten(),
    layers.Dense(64, activation='relu'),
    layers.Dense(num_classes, activation='softmax')
])

# Train and export
model.fit(...)
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()
```

## Alternative: Single 52-Class Model

Instead of separate rank + suit models, you can train a single classifier:

### Labels
```
AC, 2C, 3C, 4C, 5C, 6C, 7C, 8C, 9C, 10C, JC, QC, KC,
AD, 2D, 3D, 4D, 5D, 6D, 7D, 8D, 9D, 10D, JD, QD, KD,
AH, 2H, 3H, 4H, 5H, 6H, 7H, 8H, 9H, 10H, JH, QH, KH,
AS, 2S, 3S, 4S, 5S, 6S, 7S, 8S, 9S, 10S, JS, QS, KS
```

### Training
Same process as rank/suit, but with 52 classes instead of 13+4.

## Model Performance Targets

- **Detector**: <20ms on Galaxy S23 Ultra with NNAPI/GPU
- **Classification**: <30ms for ~30 cards
- **Total pipeline**: <100ms per snapshot

## Troubleshooting

### Common Issues
1. **Poor detection**: Add more varied training images
2. **Wrong labels**: Ensure label order matches training export
3. **Slow inference**: Use int8 quantization, enable NNAPI/GPU
4. **Model not loading**: Check file names match config.json

### Validation
- Test on different devices and lighting conditions
- Validate with real Klondike games
- Check confidence thresholds in config.json

## Next Steps

1. **Collect training data** (most important step)
2. **Train detection model** using Roboflow or local setup
3. **Train classification models** (rank + suit OR card52)
4. **Test on real device** with actual solitaire games
5. **Iterate and improve** based on performance

## Resources

- [Roboflow Training Guide](https://docs.roboflow.com/)
- [YOLOv8 Documentation](https://docs.ultralytics.com/)
- [TensorFlow Lite Guide](https://www.tensorflow.org/lite)
- [Android NNAPI](https://developer.android.com/ndk/guides/neural-networks)

## Support

If you need help with training:
1. Check the main README.md for architecture details
2. Use Roboflow for no-code training
3. Join the project discussions for community help