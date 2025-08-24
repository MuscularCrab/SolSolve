#!/usr/bin/env python3
"""
SolSolve Quick Start Training Script

This script automates the entire training pipeline for quick setup.
Run this after preparing your labeled data.
"""

import os
import sys
import subprocess
import argparse
from pathlib import Path

def check_dependencies():
    """Check if required packages are installed"""
    required_packages = ['ultralytics', 'tensorflow', 'opencv-python', 'numpy', 'PIL']
    missing_packages = []
    
    for package in required_packages:
        try:
            if package == 'PIL':
                import PIL
            elif package == 'opencv-python':
                import cv2
            else:
                __import__(package)
        except ImportError:
            missing_packages.append(package)
    
    if missing_packages:
        print("❌ Missing required packages:")
        for pkg in missing_packages:
            print(f"   - {pkg}")
        print("\nInstall with: pip install -r requirements.txt")
        return False
    
    print("✓ All required packages are installed")
    return True

def run_command(command, description):
    """Run a command and handle errors"""
    print(f"\n🚀 {description}")
    print(f"Command: {command}")
    
    try:
        result = subprocess.run(command, shell=True, check=True, capture_output=True, text=True)
        print("✓ Command completed successfully")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ Command failed with error code {e.returncode}")
        print(f"Error output: {e.stderr}")
        return False

def quick_start_training(data_dir, output_dir="trained_models"):
    """Run the complete training pipeline"""
    print("🎯 SolSolve Quick Start Training Pipeline")
    print("=" * 60)
    
    # Check dependencies
    if not check_dependencies():
        return False
    
    data_path = Path(data_dir)
    if not data_path.exists():
        print(f"❌ Data directory not found: {data_dir}")
        return False
    
    # Check if data is properly organized
    required_dirs = [
        "detection_data/images/train",
        "detection_data/images/val",
        "rank_data",
        "suit_data"
    ]
    
    missing_dirs = []
    for dir_path in required_dirs:
        if not (data_path / dir_path).exists():
            missing_dirs.append(dir_path)
    
    if missing_dirs:
        print("❌ Data directory is not properly organized. Missing:")
        for dir_path in missing_dirs:
            print(f"   - {dir_path}")
        print("\nRun data preparation first:")
        print(f"python prepare_training_data.py --image-dir {data_dir}")
        return False
    
    print("✓ Data directory structure looks good")
    
    # Create output directory
    output_path = Path(output_dir)
    output_path.mkdir(exist_ok=True)
    
    # Step 1: Train detection model
    print("\n" + "="*60)
    print("STEP 1: Training Detection Model")
    print("="*60)
    
    detection_cmd = f"python train_models.py --data-path {data_dir} --output-dir {output_dir} --train-detector"
    if not run_command(detection_cmd, "Training detection model..."):
        print("❌ Detection training failed. Check the error above.")
        return False
    
    # Step 2: Train rank classifier
    print("\n" + "="*60)
    print("STEP 2: Training Rank Classifier")
    print("="*60)
    
    rank_cmd = f"python train_models.py --data-path {data_dir} --output-dir {output_dir} --train-rank"
    if not run_command(rank_cmd, "Training rank classifier..."):
        print("❌ Rank training failed. Check the error above.")
        return False
    
    # Step 3: Train suit classifier
    print("\n" + "="*60)
    print("STEP 3: Training Suit Classifier")
    print("="*60)
    
    suit_cmd = f"python train_models.py --data-path {data_dir} --output-dir {output_dir} --train-suit"
    if not run_command(suit_cmd, "Training suit classifier..."):
        print("❌ Suit training failed. Check the error above.")
        return False
    
    # Step 4: Create final config
    print("\n" + "="*60)
    print("STEP 4: Creating Final Configuration")
    print("="*60)
    
    # Check if all models were created
    model_files = ["detector.tflite", "rank.tflite", "suit.tflite"]
    missing_models = []
    
    for model_file in model_files:
        if not (output_path / model_file).exists():
            missing_models.append(model_file)
    
    if missing_models:
        print(f"❌ Missing trained models: {missing_models}")
        return False
    
    # Create config.json
    config_cmd = f"python train_models.py --data-path {data_dir} --output-dir {output_dir}"
    run_command(config_cmd, "Creating final configuration...")
    
    print("\n" + "="*60)
    print("🎉 TRAINING COMPLETE!")
    print("="*60)
    
    print(f"\n📁 Your trained models are in: {output_dir}")
    print("\n📋 Models created:")
    for model_file in model_files:
        model_path = output_path / model_file
        if model_path.exists():
            size_mb = model_path.stat().st_size / (1024 * 1024)
            print(f"   ✓ {model_file} ({size_mb:.1f} MB)")
    
    config_path = output_path / "config.json"
    if config_path.exists():
        print(f"   ✓ config.json")
    
    print(f"\n🚀 Next steps:")
    print(f"1. Copy models to: app/src/main/assets/models/")
    print(f"2. Test in your SolSolve app")
    print(f"3. Adjust confidence thresholds in config.json if needed")
    
    return True

def main():
    parser = argparse.ArgumentParser(description="SolSolve Quick Start Training")
    parser.add_argument("--data-dir", type=str, required=True, help="Path to your organized training data directory")
    parser.add_argument("--output-dir", type=str, default="trained_models", help="Output directory for trained models")
    
    args = parser.parse_args()
    
    if not quick_start_training(args.data_dir, args.output_dir):
        print("\n❌ Training pipeline failed. Please check the errors above.")
        sys.exit(1)

if __name__ == "__main__":
    main()
