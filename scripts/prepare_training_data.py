#!/usr/bin/env python3
"""
SolSolve Training Data Preparation Script

This script helps prepare your 196 images for model training by:
1. Organizing images into proper directory structure
2. Creating sample crops for classification training
3. Setting up YOLO format detection data
"""

import os
import shutil
import argparse
from pathlib import Path
import cv2
import numpy as np
from PIL import Image
import random

def create_training_structure(output_dir="training_data"):
    """Create the complete training directory structure"""
    base_dir = Path(output_dir)
    
    # Main directories
    dirs = [
        "detection_data/images/train",
        "detection_data/images/val", 
        "detection_data/labels/train",
        "detection_data/labels/val",
        "rank_data",
        "suit_data",
        "card52_data"
    ]
    
    for dir_path in dirs:
        (base_dir / dir_path).mkdir(parents=True, exist_ok=True)
    
    # Create rank subdirectories (13 classes)
    for rank in ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']:
        (base_dir / "rank_data" / rank).mkdir(exist_ok=True)
    
    # Create suit subdirectories (4 classes)
    for suit in ['clubs', 'diamonds', 'hearts', 'spades']:
        (base_dir / "suit_data" / suit).mkdir(exist_ok=True)
    
    # Create card52 subdirectories (52 classes)
    suits = ['C', 'D', 'H', 'S']
    ranks = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']
    for rank in ranks:
        for suit in suits:
            (base_dir / "card52_data" / f"{rank}{suit}").mkdir(exist_ok=True)
    
    print(f"✓ Created training directory structure in {output_dir}")
    return base_dir

def organize_images(image_dir, output_dir, train_split=0.8):
    """Organize images into train/val split for detection training"""
    image_dir = Path(image_dir)
    detection_dir = Path(output_dir) / "detection_data"
    
    # Find all image files
    image_extensions = ['.jpg', '.jpeg', '.png', '.bmp', '.tiff']
    image_files = []
    for ext in image_extensions:
        image_files.extend(image_dir.glob(f"*{ext}"))
        image_files.extend(image_dir.glob(f"*{ext.upper()}"))
    
    if not image_files:
        print(f"❌ No images found in {image_dir}")
        return False
    
    print(f"📊 Found {len(image_files)} images")
    
    # Shuffle and split
    random.shuffle(image_files)
    split_idx = int(len(image_files) * train_split)
    train_files = image_files[:split_idx]
    val_files = image_files[split_idx:]
    
    print(f"   Training: {len(train_files)} images")
    print(f"   Validation: {len(val_files)} images")
    
    # Copy training images
    for img_file in train_files:
        shutil.copy2(img_file, detection_dir / "images" / "train" / img_file.name)
    
    # Copy validation images
    for img_file in val_files:
        shutil.copy2(img_file, detection_dir / "images" / "val" / img_file.name)
    
    print("✓ Images organized into train/val split")
    return True

def create_sample_crops(image_dir, output_dir, num_samples=10):
    """Create sample crops from images for classification training"""
    image_dir = Path(image_dir)
    output_dir = Path(output_dir)
    
    # Find some images to create sample crops
    image_files = list(image_dir.glob("*.jpg")) + list(image_dir.glob("*.png"))
    if not image_files:
        print("❌ No images found for creating sample crops")
        return False
    
    # Take a subset for sample crops
    sample_images = random.sample(image_files, min(num_samples, len(image_files)))
    
    print(f"🎯 Creating sample crops from {len(sample_images)} images...")
    
    for i, img_file in enumerate(sample_images):
        try:
            # Load image
            img = cv2.imread(str(img_file))
            if img is None:
                continue
            
            h, w = img.shape[:2]
            
            # Create multiple random crops
            for crop_idx in range(3):
                # Random crop size (64x64 to 128x128)
                crop_size = random.randint(64, 128)
                
                # Random position
                x = random.randint(0, max(0, w - crop_size))
                y = random.randint(0, max(0, h - crop_size))
                
                # Extract crop
                crop = img[y:y+crop_size, x:x+crop_size]
                
                # Resize to 64x64
                crop_resized = cv2.resize(crop, (64, 64))
                
                # Save crop
                crop_filename = f"sample_crop_{i:03d}_{crop_idx:02d}.jpg"
                crop_path = output_dir / "sample_crops" / crop_filename
                crop_path.parent.mkdir(exist_ok=True)
                
                cv2.imwrite(str(crop_path), crop_resized)
                
        except Exception as e:
            print(f"⚠️  Error processing {img_file}: {e}")
    
    print("✓ Sample crops created in sample_crops/ directory")
    return True

def create_yolo_config(output_dir):
    """Create YOLO configuration file"""
    config = {
        "path": str(Path(output_dir).absolute()),
        "train": "detection_data/images/train",
        "val": "detection_data/images/val",
        "nc": 4,  # number of classes
        "names": [
            "card_face_up",
            "card_back", 
            "pile_slot_tableau",
            "pile_slot_foundation"
        ]
    }
    
    config_path = Path(output_dir) / "detection_data" / "data.yaml"
    
    import yaml
    with open(config_path, 'w') as f:
        yaml.dump(config, f, default_flow_style=False)
    
    print("✓ Created YOLO config: detection_data/data.yaml")
    return True

def create_labeling_guide(output_dir):
    """Create a labeling guide for manual annotation"""
    guide_content = """# SolSolve Labeling Guide

## Detection Labels (YOLO format)

You need to create .txt files for each image in:
- detection_data/labels/train/
- detection_data/labels/val/

Each .txt file should contain one line per detected object:
class_id x_center y_center width height

### Class IDs:
0: card_face_up - Face-up playing cards
1: card_back - Card backs
2: pile_slot_tableau - Tableau pile slots (7 columns)
3: pile_slot_foundation - Foundation pile slots (4 slots)

### Example label file (image_001.txt):
0 0.5 0.3 0.1 0.15
0 0.7 0.4 0.1 0.15
2 0.2 0.8 0.08 0.12
2 0.35 0.8 0.08 0.12
2 0.5 0.8 0.08 0.12
2 0.65 0.8 0.08 0.12
2 0.8 0.8 0.08 0.12
3 0.1 0.2 0.08 0.12
3 0.3 0.2 0.08 0.12
3 0.5 0.2 0.08 0.12
3 0.7 0.2 0.08 0.12

## Classification Data

### Rank Classification (13 classes)
Place 64x64 pixel card corner crops in:
rank_data/A/, rank_data/2/, ..., rank_data/K/

### Suit Classification (4 classes)  
Place 64x64 pixel card corner crops in:
suit_data/clubs/, suit_data/diamonds/, suit_data/hearts/, suit_data/spades/

## Tools for Labeling

### Detection Labeling:
- LabelImg: https://github.com/heartexlabs/labelImg
- Roboflow: https://roboflow.com
- CVAT: https://cvat.org

### Classification Crops:
- Use detection model to find cards first
- Crop top-left corner of each detected card
- Resize to 64x64 pixels
- Sort into appropriate class folders

## Next Steps

1. Label your detection data using YOLO format
2. Create classification crops from detected cards
3. Run training scripts:
   python train_models.py --train-detector
   python train_models.py --train-rank  
   python train_models.py --train-suit
"""
    
    guide_path = Path(output_dir) / "LABELING_GUIDE.md"
    with open(guide_path, 'w') as f:
        f.write(guide_content)
    
    print("✓ Created labeling guide: LABELING_GUIDE.md")
    return True

def main():
    parser = argparse.ArgumentParser(description="Prepare SolSolve training data")
    parser.add_argument("--image-dir", type=str, required=True, help="Directory containing your 196 training images")
    parser.add_argument("--output-dir", type=str, default="training_data", help="Output directory for organized training data")
    parser.add_argument("--create-samples", action="store_true", help="Create sample crops for classification")
    parser.add_argument("--num-samples", type=int, default=10, help="Number of sample crops to create")
    
    args = parser.parse_args()
    
    print("🎯 SolSolve Training Data Preparation")
    print("=" * 50)
    
    # Create directory structure
    base_dir = create_training_structure(args.output_dir)
    
    # Organize images
    if not organize_images(args.image_dir, args.output_dir):
        return
    
    # Create YOLO config
    create_yolo_config(args.output_dir)
    
    # Create sample crops if requested
    if args.create_samples:
        create_sample_crops(args.image_dir, args.output_dir, args.num_samples)
    
    # Create labeling guide
    create_labeling_guide(args.output_dir)
    
    print("\n✅ Training data preparation complete!")
    print(f"\n📁 Your training data is organized in: {args.output_dir}")
    print("\n📋 Next steps:")
    print("1. Label your detection data (see LABELING_GUIDE.md)")
    print("2. Prepare classification crops")
    print("3. Run training: python train_models.py --train-detector")
    print("4. Run training: python train_models.py --train-rank")
    print("5. Run training: python train_models.py --train-suit")

if __name__ == "__main__":
    main()
