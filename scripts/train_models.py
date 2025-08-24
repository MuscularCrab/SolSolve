#!/usr/bin/env python3
"""
SolSolve Local Model Training Script

This script trains all three required models locally:
1. Card detector (YOLOv8)
2. Rank classifier (13 classes)
3. Suit classifier (4 classes)

Usage:
    python train_models.py --data-path /path/to/your/196/images
"""

import os
import sys
import shutil
import argparse
import json
from pathlib import Path
import cv2
import numpy as np
from PIL import Image
import yaml

try:
    from ultralytics import YOLO
except ImportError:
    print("Error: ultralytics not installed. Run: pip install ultralytics")
    sys.exit(1)

try:
    import tensorflow as tf
except ImportError:
    print("Error: tensorflow not installed. Run: pip install tensorflow")
    sys.exit(1)

class SolSolveTrainer:
    def __init__(self, data_path, output_dir="trained_models"):
        self.data_path = Path(data_path)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(exist_ok=True)
        
        # Model configurations
        self.detector_config = {
            "input_size": 416,
            "epochs": 100,
            "batch_size": 16,
            "patience": 20
        }
        
        self.classifier_config = {
            "input_size": 64,
            "epochs": 50,
            "batch_size": 32,
            "patience": 15
        }
        
    def setup_directories(self):
        """Create training directory structure"""
        dirs = [
            "detection_data",
            "rank_data", 
            "suit_data",
            "models"
        ]
        
        for dir_name in dirs:
            (self.output_dir / dir_name).mkdir(exist_ok=True)
            
        # Create rank subdirectories
        for rank in ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']:
            (self.output_dir / "rank_data" / rank).mkdir(exist_ok=True)
            
        # Create suit subdirectories
        for suit in ['clubs', 'diamonds', 'hearts', 'spades']:
            (self.output_dir / "suit_data" / suit).mkdir(exist_ok=True)
            
        print("✓ Directory structure created")
    
    def prepare_detection_data(self):
        """Prepare YOLO format detection data"""
        detection_dir = self.output_dir / "detection_data"
        
        # Create YOLO directory structure
        (detection_dir / "images" / "train").mkdir(parents=True, exist_ok=True)
        (detection_dir / "images" / "val").mkdir(parents=True, exist_ok=True)
        (detection_dir / "labels" / "train").mkdir(parents=True, exist_ok=True)
        (detection_dir / "labels" / "val").mkdir(parents=True, exist_ok=True)
        
        # Copy images to training directories
        image_files = list(self.data_path.glob("*.jpg")) + list(self.data_path.glob("*.png"))
        
        if not image_files:
            print("❌ No images found in data path")
            return False
            
        # Split into train/val (80/20)
        np.random.shuffle(image_files)
        split_idx = int(len(image_files) * 0.8)
        train_files = image_files[:split_idx]
        val_files = image_files[split_idx:]
        
        print(f"📊 Found {len(image_files)} images")
        print(f"   Training: {len(train_files)}")
        print(f"   Validation: {len(val_files)}")
        
        # Copy training images
        for img_file in train_files:
            shutil.copy2(img_file, detection_dir / "images" / "train" / img_file.name)
            
        # Copy validation images  
        for img_file in val_files:
            shutil.copy2(img_file, detection_dir / "images" / "val" / img_file.name)
        
        # Create YAML config
        yaml_config = {
            "path": str(detection_dir.absolute()),
            "train": "images/train",
            "val": "images/val",
            "nc": 4,  # number of classes
            "names": ["card_face_up", "card_back", "pile_slot_tableau", "pile_slot_foundation"]
        }
        
        with open(detection_dir / "data.yaml", "w") as f:
            yaml.dump(yaml_config, f, default_flow_style=False)
            
        print("⚠️  IMPORTANT: You need to create YOLO format labels for detection training")
        print("   Use a tool like LabelImg or Roboflow to create .txt files in labels/train and labels/val")
        print("   Each .txt file should contain: class_id x_center y_center width height")
        print("   Class IDs: 0=card_face_up, 1=card_back, 2=pile_slot_tableau, 3=pile_slot_foundation")
        
        return True
    
    def prepare_classification_data(self):
        """Prepare classification data from detection crops"""
        print("⚠️  For classification training, you need to:")
        print("   1. Run detection on your images to get card crops")
        print("   2. Manually sort crops into rank_data/ and suit_data/ folders")
        print("   3. Each crop should be 64x64 pixels")
        print("   4. Ensure balanced distribution across classes")
        
        return True
    
    def train_detector(self):
        """Train the card detection model"""
        detection_dir = self.output_dir / "detection_data"
        data_yaml = detection_dir / "data.yaml"
        
        if not data_yaml.exists():
            print("❌ Detection data.yaml not found. Run prepare_detection_data() first")
            return False
            
        print("🚀 Training detection model...")
        
        # Initialize YOLOv8 model
        model = YOLO('yolov8n.pt')  # Start with nano model
        
        # Train the model
        results = model.train(
            data=str(data_yaml),
            epochs=self.detector_config["epochs"],
            imgsz=self.detector_config["input_size"],
            batch=self.detector_config["batch_size"],
            patience=self.detector_config["patience"],
            save=True,
            project=str(self.output_dir / "models"),
            name="detector"
        )
        
        # Export to TFLite
        best_model = self.output_dir / "models" / "detector" / "weights" / "best.pt"
        if best_model.exists():
            model = YOLO(str(best_model))
            model.export(format='tflite', int8=True, imgsz=self.detector_config["input_size"])
            
            # Copy to output directory
            tflite_file = best_model.parent / "best.tflite"
            if tflite_file.exists():
                shutil.copy2(tflite_file, self.output_dir / "detector.tflite")
                print("✓ Detection model exported to TFLite")
            else:
                print("❌ Failed to export detection model")
        else:
            print("❌ No best model found after training")
            
        return True
    
    def train_classifier(self, model_type):
        """Train rank or suit classifier"""
        if model_type not in ["rank", "suit"]:
            print("❌ Invalid model type. Use 'rank' or 'suit'")
            return False
            
        data_dir = self.output_dir / f"{model_type}_data"
        if not data_dir.exists():
            print(f"❌ {model_type} data directory not found")
            return False
            
        print(f"🚀 Training {model_type} classifier...")
        
        # Count samples per class
        class_counts = {}
        for class_dir in data_dir.iterdir():
            if class_dir.is_dir():
                class_counts[class_dir.name] = len(list(class_dir.glob("*.jpg")) + list(class_dir.glob("*.png")))
        
        if not class_counts:
            print(f"❌ No training data found in {data_dir}")
            return False
            
        print(f"📊 {model_type} class distribution:")
        for class_name, count in class_counts.items():
            print(f"   {class_name}: {count} samples")
        
        # Create simple CNN model
        num_classes = len(class_counts)
        input_shape = (self.classifier_config["input_size"], self.classifier_config["input_size"], 3)
        
        model = tf.keras.Sequential([
            tf.keras.layers.Conv2D(32, 3, activation='relu', input_shape=input_shape),
            tf.keras.layers.MaxPooling2D(),
            tf.keras.layers.Conv2D(64, 3, activation='relu'),
            tf.keras.layers.MaxPooling2D(),
            tf.keras.layers.Conv2D(64, 3, activation='relu'),
            tf.keras.layers.GlobalAveragePooling2D(),
            tf.keras.layers.Dropout(0.5),
            tf.keras.layers.Dense(128, activation='relu'),
            tf.keras.layers.Dropout(0.3),
            tf.keras.layers.Dense(num_classes, activation='softmax')
        ])
        
        model.compile(
            optimizer='adam',
            loss='categorical_crossentropy',
            metrics=['accuracy']
        )
        
        # Data augmentation
        data_augmentation = tf.keras.Sequential([
            tf.keras.layers.RandomFlip("horizontal"),
            tf.keras.layers.RandomRotation(0.1),
            tf.keras.layers.RandomZoom(0.1),
            tf.keras.layers.RandomBrightness(0.2),
        ])
        
        # Create data generators
        train_datagen = tf.keras.preprocessing.image.ImageDataGenerator(
            preprocessing_function=data_augmentation,
            rescale=1./255,
            validation_split=0.2
        )
        
        train_generator = train_datagen.flow_from_directory(
            str(data_dir),
            target_size=(self.classifier_config["input_size"], self.classifier_config["input_size"]),
            batch_size=self.classifier_config["batch_size"],
            class_mode='categorical',
            subset='training'
        )
        
        validation_generator = train_datagen.flow_from_directory(
            str(data_dir),
            target_size=(self.classifier_config["input_size"], self.classifier_config["input_size"]),
            batch_size=self.classifier_config["batch_size"],
            class_mode='categorical',
            subset='validation'
        )
        
        # Train the model
        history = model.fit(
            train_generator,
            epochs=self.classifier_config["epochs"],
            validation_data=validation_generator,
            callbacks=[
                tf.keras.callbacks.EarlyStopping(
                    patience=self.classifier_config["patience"],
                    restore_best_weights=True
                ),
                tf.keras.callbacks.ReduceLROnPlateau(
                    factor=0.5,
                    patience=5,
                    min_lr=1e-7
                )
            ]
        )
        
        # Export to TFLite
        converter = tf.lite.TFLiteConverter.from_keras_model(model)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.target_spec.supported_types = [tf.float16]
        
        tflite_model = converter.convert()
        
        # Save TFLite model
        tflite_path = self.output_dir / f"{model_type}.tflite"
        with open(tflite_path, 'wb') as f:
            f.write(tflite_model)
            
        print(f"✓ {model_type} classifier exported to TFLite")
        return True
    
    def create_config(self):
        """Create config.json for the trained models"""
        config = {
            "detector": {
                "file": "detector.tflite",
                "labels": ["card_face_up", "card_back", "pile_slot_tableau", "pile_slot_foundation"],
                "inputSize": self.detector_config["input_size"],
                "confidenceThreshold": 0.35,
                "nmsIoU": 0.45
            },
            "rank": {
                "file": "rank.tflite",
                "labels": ["A","2","3","4","5","6","7","8","9","10","J","Q","K"],
                "inputSize": self.classifier_config["input_size"],
                "confidenceThreshold": 0.6
            },
            "suit": {
                "file": "suit.tflite",
                "labels": ["clubs","diamonds","hearts","spades"],
                "inputSize": self.classifier_config["input_size"],
                "confidenceThreshold": 0.6
            }
        }
        
        config_path = self.output_dir / "config.json"
        with open(config_path, 'w') as f:
            json.dump(config, f, indent=2)
            
        print("✓ Config.json created")
        return True
    
    def run_full_training(self):
        """Run complete training pipeline"""
        print("🎯 Starting SolSolve Model Training Pipeline")
        print("=" * 50)
        
        # Setup
        self.setup_directories()
        
        # Prepare detection data
        if not self.prepare_detection_data():
            return False
            
        # Prepare classification data
        if not self.prepare_classification_data():
            return False
            
        print("\n📋 Next Steps:")
        print("1. Create YOLO labels for detection training")
        print("2. Prepare classification crops")
        print("3. Run: python train_models.py --train-detector")
        print("4. Run: python train_models.py --train-rank")
        print("5. Run: python train_models.py --train-suit")
        
        return True

def main():
    parser = argparse.ArgumentParser(description="SolSolve Local Model Training")
    parser.add_argument("--data-path", type=str, required=True, help="Path to your 196 training images")
    parser.add_argument("--output-dir", type=str, default="trained_models", help="Output directory for trained models")
    parser.add_argument("--setup", action="store_true", help="Setup training environment")
    parser.add_argument("--train-detector", action="store_true", help="Train detection model")
    parser.add_argument("--train-rank", action="store_true", help="Train rank classifier")
    parser.add_argument("--train-suit", action="store_true", help="Train suit classifier")
    parser.add_argument("--full-pipeline", action="store_true", help="Run complete training pipeline")
    
    args = parser.parse_args()
    
    trainer = SolSolveTrainer(args.data_path, args.output_dir)
    
    if args.setup or args.full_pipeline:
        trainer.run_full_training()
    
    if args.train_detector:
        trainer.train_detector()
        
    if args.train_rank:
        trainer.train_classifier("rank")
        
    if args.train_suit:
        trainer.train_classifier("suit")
    
    if not any([args.setup, args.train_detector, args.train_rank, args.train_suit, args.full_pipeline]):
        print("No training action specified. Use --help for options.")

if __name__ == "__main__":
    main()
