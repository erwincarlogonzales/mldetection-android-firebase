# ML Detection Android

A real-time object detection Android application using YOLO models with ONNX Runtime.

## Features

- **Real-time Detection**: Live camera feed with object detection overlay
- **ONNX Runtime Integration**: Optimized model inference using Microsoft's ONNX Runtime
- **Performance Optimized**: CPU-first approach for consistent performance across devices
- **Clean Architecture**: Modular design with clear separation of concerns

## Architecture Overview

The app follows a domain-driven architecture pattern:

```
├── Domain Models
│   ├── BoundingBox.kt          # Detection result data class
│   └── Constants.kt            # Configuration constants
├── Detection Engine
│   └── Detector.kt             # Core ML inference logic
├── UI Layer
│   ├── MainActivity.kt         # Camera handling & lifecycle
│   └── OverlayView.kt         # Detection visualization
└── Assets
    ├── model.onnx             # Your YOLO model (not included)
    └── labels.txt             # Class labels
```

## Quick Start

### Prerequisites

- Android Studio Arctic Fox or later
- Android device/emulator with API 26+
- Camera permission

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/erwincarlogonzales/mldetection-android-firebase.git
   cd mldetection-android-template
   ```

2. **Add your model files**
    - Place your YOLO model as `app/src/main/assets/model.onnx`
    - Update `app/src/main/assets/labels.txt` with your class names

3. **Configure detection parameters** in `Constants.kt`:
   ```kotlin
   const val CONFIDENCE_THRESHOLD = 0.25f  // Minimum detection confidence
   const val IOU_THRESHOLD = 0.5f          // Non-maximum suppression threshold
   ```

4. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```

## Deployment

This app is distributed via **Firebase App Distribution** for easy testing and deployment without app store approval.

### For Developers

**Build and distribute:**
```bash
# Build release APK
./gradlew assembleRelease

# Upload to Firebase App Distribution via console
# https://console.firebase.google.com/project/mldetection-android/appdistribution
```

**Quick deployment workflow:**
1. Make your changes and test locally
2. Build release APK: `./gradlew assembleRelease`
3. Upload APK to Firebase console
4. Add tester emails and distribute
5. Testers get email with download link

### For Testers

**First-time setup:**
1. Enable "Install from Unknown Sources" in Android settings
2. Click the download link from Firebase email
3. Install and grant camera permissions

**Updates:**
- You'll receive email notifications for new versions
- Simply download and install over the existing app

### Alternative Deployment Options

If Firebase Distribution isn't available:

**GitHub Releases:**
```bash
# Tag your release
git tag v1.0.0
git push origin v1.0.0

# Upload APK to GitHub releases page
# Share download link directly
```

**Direct APK Sharing:**
- Build APK and share via Google Drive, email, or file transfer
- Simpler but less organized than Firebase Distribution

## Performance Optimization

### Current Optimizations
- CPU-first execution for consistent performance
- Efficient bitmap preprocessing
- Non-maximum suppression for duplicate removal
- Background thread processing

### Potential Improvements
- Frame skipping for high-frequency cameras
- Bitmap pooling to reduce GC pressure
- Model quantization for faster inference
- GPU acceleration for supported devices

## Dependencies

- **CameraX**: Modern camera API with lifecycle awareness
- **ONNX Runtime**: Cross-platform ML inference
- **Firebase**: Analytics and crash reporting
- **Material Design**: UI components

## Troubleshooting

### Common Issues

**Model not loading**
- Verify model.onnx is in the correct assets folder
- Check model format compatibility with ONNX Runtime 1.21.1

**Poor detection performance**
- Adjust CONFIDENCE_THRESHOLD (try 0.1 - 0.5)
- Verify your model was trained properly
- Check lighting conditions

**App crashes on detection**
- Ensure model input/output shapes match expectations
- Check logcat for ONNX Runtime errors
- Verify labels.txt has correct number of classes

**Low FPS**
- Consider reducing model input size
- Enable GPU acceleration (if stable on target devices)
- Implement frame skipping

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Acknowledgments

- Built with [ONNX Runtime](https://onnxruntime.ai/)
- Camera functionality powered by [CameraX](https://developer.android.com/training/camerax)
- Inspired by YOLO object detection architecture