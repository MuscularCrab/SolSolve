#!/usr/bin/env python3
"""
SolSolve Data Collection Helper Script

This script helps you prepare training data for the SolSolve ML models.
"""

import os
import cv2
import numpy as np
from pathlib import Path
import argparse

def create_directory_structure():
    """Create the recommended directory structure for training data."""
    dirs = [
        "raw_images",
        "detection_labels", 
        "classification_crops/rank",
        "classification_crops/suit",
        "classification_crops/card52"
    ]
    
    for dir_path in dirs:
        Path(dir_path).mkdir(parents=True, exist_ok=True)
        print(f"Created directory: {dir_path}")
    
    # Create rank subdirectories
    for rank in ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']:
        Path(f"classification_crops/rank/{rank}").mkdir(exist_ok=True)
    
    # Create suit subdirectories  
    for suit in ['clubs', 'diamonds', 'hearts', 'spades']:
        Path(f"classification_crops/suit/{suit}").mkdir(exist_ok=True)
    
    # Create card52 subdirectories
    suits = ['C', 'D', 'H', 'S']
    ranks = ['A', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'J', 'Q', 'K']
    for rank in ranks:
        for suit in suits:
            Path(f"classification_crops/card52/{rank}{suit}").mkdir(exist_ok=True)

def extract_frames_from_video(video_path, output_dir, frame_interval=30):
    """Extract frames from a video file for training data collection."""
    if not os.path.exists(video_path):
        print(f"Video file not found: {video_path}")
        return
    
    cap = cv2.VideoCapture(video_path)
    frame_count = 0
    extracted_count = 0
    
    while True:
        ret, frame = cap.read()
        if not ret:
            break
            
        if frame_count % frame_interval == 0:
            output_path = os.path.join(output_dir, f"frame_{extracted_count:04d}.jpg")
            cv2.imwrite(output_path, frame)
            extracted_count += 1
            print(f"Extracted frame {extracted_count} to {output_path}")
        
        frame_count += 1
    
    cap.release()
    print(f"Extracted {extracted_count} frames from video")

def create_sample_config():
    """Create a sample config.json for reference."""
    config = {
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
    
    import json
    with open("sample_config.json", "w") as f:
        json.dump(config, f, indent=2)
    
    print("Created sample_config.json for reference")

def main():
    parser = argparse.ArgumentParser(description="SolSolve Data Collection Helper")
    parser.add_argument("--setup", action="store_true", help="Create directory structure")
    parser.add_argument("--extract-video", type=str, help="Extract frames from video file")
    parser.add_argument("--frame-interval", type=int, default=30, help="Frame interval for extraction")
    parser.add_argument("--sample-config", action="store_true", help="Create sample config.json")
    
    args = parser.parse_args()
    
    if args.setup:
        create_directory_structure()
        print("\nDirectory structure created successfully!")
        print("Next steps:")
        print("1. Place your raw images in 'raw_images/' directory")
        print("2. Use a labeling tool (Roboflow, LabelImg, etc.) to create detection labels")
        print("3. Crop card corners for classification training")
        print("4. Train your models using the TRAINING_GUIDE.md")
    
    if args.extract_video:
        extract_frames_from_video(args.extract_video, "raw_images", args.frame_interval)
    
    if args.sample_config:
        create_sample_config()
    
    if not any([args.setup, args.extract_video, args.sample_config]):
        print("SolSolve Data Collection Helper")
        print("\nUsage:")
        print("  python data_collection_helper.py --setup                    # Create directory structure")
        print("  python data_collection_helper.py --extract-video video.mp4  # Extract frames from video")
        print("  python data_collection_helper.py --sample-config           # Create sample config.json")
        print("\nFor detailed training instructions, see: app/src/main/assets/models/TRAINING_GUIDE.md")

if __name__ == "__main__":
    main()