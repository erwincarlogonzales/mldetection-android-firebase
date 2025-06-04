package com.example.mldetection

object Constants {
    const val MODEL_PATH = "model.onnx"
    const val LABELS_PATH = "labels.txt"

    const val CONFIDENCE_THRESHOLD = 0.25f
    const val IOU_THRESHOLD = 0.5f
    
    // Force CPU execution
    const val USE_GPU = false

    // Model input dimensions
    const val INPUT_WIDTH = 640
    const val INPUT_HEIGHT = 640
}