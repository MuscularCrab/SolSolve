# SolSolve Helper Scripts

This directory contains helper scripts to assist with data collection and model training.

## data_collection_helper.py

A Python script to help you prepare training data for the SolSolve ML models.

### Features
- Create recommended directory structure for training data
- Extract frames from video files for data collection
- Generate sample configuration files

### Requirements
```bash
pip install opencv-python numpy
```

### Usage

#### Setup directory structure
```bash
python data_collection_helper.py --setup
```

#### Extract frames from video
```bash
python data_collection_helper.py --extract-video your_video.mp4
```

#### Create sample config
```bash
python data_collection_helper.py --sample-config
```

### Directory Structure Created
```
.
├── raw_images/                    # Place your raw images here
├── detection_labels/              # Detection labels (YOLO format)
├── classification_crops/
│   ├── rank/                      # Rank classification crops
│   │   ├── A/
│   │   ├── 2/
│   │   └── ...
│   ├── suit/                      # Suit classification crops
│   │   ├── clubs/
│   │   ├── diamonds/
│   │   ├── hearts/
│   │   └── spades/
│   └── card52/                    # 52-class classification crops
│       ├── AC/
│       ├── 2C/
│       └── ...
```

## Next Steps

1. **Collect images**: Record videos or take photos of Klondike solitaire games
2. **Extract frames**: Use the script to extract frames from videos
3. **Label data**: Use Roboflow or similar tools to label detection boxes
4. **Train models**: Follow the TRAINING_GUIDE.md in the models directory

## Tips for Data Collection

- **Variety**: Include different themes, backgrounds, lighting conditions
- **Actions**: Capture various game states (full tableau, partial, waste/stock actions)
- **Quality**: Ensure images are clear and cards are readable
- **Quantity**: Aim for 400-800 images total for good model performance

For detailed training instructions, see: `../app/src/main/assets/models/TRAINING_GUIDE.md`