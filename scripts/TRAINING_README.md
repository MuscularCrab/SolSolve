# SolSolve Local Model Training Guide

This guide will help you train all three required ML models locally using your 196 images.

## 🚀 Quick Start (Recommended)

If you want to get started immediately:

```bash
# 1. Install dependencies
pip install -r requirements.txt

# 2. Prepare your training data
python prepare_training_data.py --image-dir /path/to/your/196/images

# 3. Label your data (see labeling section below)

# 4. Run complete training pipeline
python quick_start.py --data-dir training_data
```

## 📋 Prerequisites

- Python 3.8+
- 196 training images (JPG/PNG format)
- GPU recommended (but not required)

## 🔧 Installation

1. **Install Python dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

2. **Verify installation:**
   ```bash
   python -c "import ultralytics, tensorflow, cv2; print('✓ All packages installed')"
   ```

## 📊 Training Data Requirements

### Image Requirements
- **Format**: JPG, PNG, BMP, TIFF
- **Content**: Klondike solitaire games
- **Variety**: Different themes, backgrounds, lighting conditions
- **Actions**: Include waste/stock actions (Draw 3)

### What You Need to Label

#### 1. Detection Model (4 classes)
- `card_face_up` - Face-up playing cards
- `card_back` - Card backs  
- `pile_slot_tableau` - Tableau pile slots (7 columns)
- `pile_slot_foundation` - Foundation pile slots (4 slots)

#### 2. Classification Models
- **Rank Classifier**: 13 classes (A, 2, 3, 4, 5, 6, 7, 8, 9, 10, J, Q, K)
- **Suit Classifier**: 4 classes (clubs, diamonds, hearts, spades)

## 🏗️ Step-by-Step Training Process

### Step 1: Prepare Training Data

```bash
python prepare_training_data.py --image-dir /path/to/your/196/images --output-dir training_data
```

This will:
- Create proper directory structure
- Split images into train/validation sets (80/20)
- Create YOLO configuration files
- Generate labeling guide

### Step 2: Label Your Data

#### Option A: Use LabelImg (Free, Local)
```bash
# Install LabelImg
pip install labelImg

# Launch labeling tool
labelImg
```

**LabelImg Instructions:**
1. Open `training_data/detection_data/images/train/`
2. Set format to YOLO
3. Label each image with bounding boxes
4. Save labels to `training_data/detection_data/labels/train/`

#### Option B: Use Roboflow (Web-based, Free tier)
1. Go to [Roboflow.com](https://roboflow.com)
2. Create new Object Detection project
3. Upload your images
4. Label with bounding boxes
5. Export as YOLO format
6. Download and place in labels folders

### Step 3: Prepare Classification Data

For rank and suit classification, you need 64x64 pixel crops of card corners:

1. **Use detection model first** to find cards
2. **Crop top-left corner** of each detected card
3. **Resize to 64x64 pixels**
4. **Sort into class folders**:
   ```
   training_data/rank_data/A/     # Ace crops
   training_data/rank_data/2/     # 2 crops
   training_data/rank_data/3/     # 3 crops
   ...
   training_data/suit_data/clubs/     # Club crops
   training_data/suit_data/diamonds/  # Diamond crops
   training_data/suit_data/hearts/    # Heart crops
   training_data/suit_data/spades/    # Spade crops
   ```

### Step 4: Train Models

#### Option A: Train All Models at Once
```bash
python quick_start.py --data-dir training_data
```

#### Option B: Train Models Individually
```bash
# Train detection model
python train_models.py --data-path training_data --train-detector

# Train rank classifier
python train_models.py --data-path training_data --train-rank

# Train suit classifier
python train_models.py --data-path training_data --train-suit
```

## ⚙️ Training Configuration

### Detection Model (YOLOv8)
- **Input size**: 416x416 pixels
- **Epochs**: 100 (with early stopping)
- **Batch size**: 16
- **Model**: YOLOv8n (nano)

### Classification Models (CNN)
- **Input size**: 64x64 pixels
- **Epochs**: 50 (with early stopping)
- **Batch size**: 32
- **Architecture**: Simple CNN with data augmentation

## 📈 Expected Results

With 196 images properly labeled:

- **Detection Model**: 85-95% mAP (mean Average Precision)
- **Rank Classifier**: 90-98% accuracy
- **Suit Classifier**: 90-98% accuracy

## 🔍 Monitoring Training

### Detection Training
Training progress is saved in `trained_models/models/detector/`:
- `weights/best.pt` - Best model weights
- `results.png` - Training curves
- `confusion_matrix.png` - Confusion matrix

### Classification Training
Training progress is displayed in real-time with:
- Loss curves
- Accuracy metrics
- Early stopping indicators

## 🚨 Troubleshooting

### Common Issues

#### 1. "No images found"
- Check image file extensions (.jpg, .png, etc.)
- Verify image directory path
- Ensure images are not corrupted

#### 2. "CUDA out of memory"
- Reduce batch size in training config
- Use smaller input image size
- Close other GPU applications

#### 3. "Poor detection accuracy"
- Add more varied training images
- Improve labeling quality
- Increase training epochs
- Check class balance

#### 4. "Model not loading in app"
- Verify model file names match config.json
- Check TensorFlow Lite compatibility
- Ensure models are properly exported

### Performance Optimization

#### For Better Accuracy:
- Increase training epochs
- Use larger model (YOLOv8s instead of YOLOv8n)
- Add more training data
- Improve data quality

#### For Faster Training:
- Use GPU acceleration
- Reduce input image size
- Use smaller model architecture
- Reduce batch size

## 📱 Integration with SolSolve App

After training, copy models to your app:

```bash
# Copy trained models
cp trained_models/*.tflite app/src/main/assets/models/

# Copy configuration
cp trained_models/config.json app/src/main/assets/models/
```

## 📚 Additional Resources

- [YOLOv8 Documentation](https://docs.ultralytics.com/)
- [TensorFlow Lite Guide](https://www.tensorflow.org/lite)
- [LabelImg Tutorial](https://github.com/heartexlabs/labelImg)
- [Roboflow Training Guide](https://docs.roboflow.com/)

## 🤝 Getting Help

If you encounter issues:

1. Check the troubleshooting section above
2. Verify your data format and structure
3. Check console output for error messages
4. Ensure all dependencies are installed
5. Try with a smaller subset of images first

## 🎯 Next Steps After Training

1. **Test models** on new images
2. **Validate performance** on real solitaire games
3. **Adjust confidence thresholds** in config.json
4. **Iterate and improve** based on results
5. **Deploy to your SolSolve app**

---

**Happy Training! 🎉**

Your 196 images should provide a solid foundation for training accurate card detection and classification models.
