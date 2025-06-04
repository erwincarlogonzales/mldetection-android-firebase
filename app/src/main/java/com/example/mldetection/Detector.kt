package com.example.mldetection

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

class Detector(
    private val context: Context,
    private val listener: DetectionListener
) {
    private val ortEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var labels = mutableListOf<String>()

    init {
        createSession()
        loadLabels()
    }

    private fun createSession() {
        val modelBytes = context.assets.open(Constants.MODEL_PATH).readBytes()
        val sessionOptions = OrtSession.SessionOptions()

        if (Constants.USE_GPU) {
            sessionOptions.addNnapi()
            android.util.Log.i("Detector", "Using GPU (NNAPI) execution")
        } else {
            // CPU execution - 6x faster for INT8 models (default behavior)
            android.util.Log.i("Detector", "Using CPU execution")
        }

        session = ortEnvironment.createSession(modelBytes, sessionOptions)
        sessionOptions.close()
    }

    private fun loadLabels() {
        context.assets.open(Constants.LABELS_PATH).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                labels = reader.lineSequence().filter { it.isNotBlank() }.toMutableList()
            }
        }
    }

    fun detect(bitmap: Bitmap) {
        session ?: return listener.onEmptyDetection()

        val startTime = SystemClock.uptimeMillis()

        try {
            val resizedBitmap = Bitmap.createScaledBitmap(
                bitmap,
                Constants.INPUT_WIDTH,
                Constants.INPUT_HEIGHT,
                true
            )

            val floatBuffer = preProcess(resizedBitmap)
            val inputName = session?.inputNames?.first() ?: return
            val shape = longArrayOf(1, 3, Constants.INPUT_HEIGHT.toLong(), Constants.INPUT_WIDTH.toLong())

            OnnxTensor.createTensor(ortEnvironment, floatBuffer, shape).use { inputTensor ->
                session?.run(mapOf(inputName to inputTensor))?.use { results ->
                    val outputTensorValue = results[0]?.value
                    val detections = parseOutput(outputTensorValue)
                    val finalDetections = applyNMS(detections)

                    val inferenceTime = SystemClock.uptimeMillis() - startTime

                    if (finalDetections.isEmpty()) {
                        listener.onEmptyDetection()
                    } else {
                        listener.onDetection(finalDetections, inferenceTime)
                    }
                }
            }

            resizedBitmap.recycle()

        } catch (e: Exception) {
            listener.onEmptyDetection()
        }
    }

    private fun parseOutput(outputTensorValue: Any?): List<BoundingBox> {
        return try {
            @Suppress("UNCHECKED_CAST")
            val outputArray3D = outputTensorValue as Array<Array<FloatArray>>
            if (outputArray3D.isNotEmpty()) {
                postProcess(outputArray3D[0]) // [channels, boxes]
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun preProcess(bitmap: Bitmap): FloatBuffer {
        val floatBuffer = FloatBuffer.allocate(1 * 3 * Constants.INPUT_WIDTH * Constants.INPUT_HEIGHT)
        floatBuffer.rewind()

        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (y in 0 until Constants.INPUT_HEIGHT) {
            for (x in 0 until Constants.INPUT_WIDTH) {
                val pixelIndex = y * bitmap.width + x
                if (pixelIndex >= pixels.size) continue

                val pixel = pixels[pixelIndex]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                val bufferIndexBase = y * Constants.INPUT_WIDTH + x
                floatBuffer.put(bufferIndexBase, r)
                floatBuffer.put(bufferIndexBase + Constants.INPUT_WIDTH * Constants.INPUT_HEIGHT, g)
                floatBuffer.put(bufferIndexBase + 2 * Constants.INPUT_WIDTH * Constants.INPUT_HEIGHT, b)
            }
        }

        floatBuffer.rewind()
        return floatBuffer
    }

    private fun postProcess(outputArray: Array<FloatArray>): List<BoundingBox> {
        val detections = mutableListOf<BoundingBox>()

        if (outputArray.isEmpty() || outputArray[0].isEmpty()) return emptyList()

        val numChannels = outputArray.size
        val numBoxes = outputArray[0].size
        val numClasses = numChannels - 4

        for (i in 0 until numBoxes) {
            var maxClassScore = 0f
            var classId = -1

            for (c in 0 until numClasses) {
                val score = outputArray[4 + c][i]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = c
                }
            }

            if (maxClassScore >= Constants.CONFIDENCE_THRESHOLD) {
                val cx_pixels = outputArray[0][i]
                val cy_pixels = outputArray[1][i]
                val width_pixels = outputArray[2][i]
                val height_pixels = outputArray[3][i]

                val x1_pixels = cx_pixels - width_pixels / 2f
                val y1_pixels = cy_pixels - height_pixels / 2f
                val x2_pixels = cx_pixels + width_pixels / 2f
                val y2_pixels = cy_pixels + height_pixels / 2f

                val x1_norm = (x1_pixels / Constants.INPUT_WIDTH).coerceIn(0f, 1f)
                val y1_norm = (y1_pixels / Constants.INPUT_HEIGHT).coerceIn(0f, 1f)
                val x2_norm = (x2_pixels / Constants.INPUT_WIDTH).coerceIn(0f, 1f)
                val y2_norm = (y2_pixels / Constants.INPUT_HEIGHT).coerceIn(0f, 1f)

                if (x1_norm < x2_norm && y1_norm < y2_norm) {
                    val label = if (classId >= 0 && classId < labels.size) labels[classId] else "Unknown"

                    detections.add(
                        BoundingBox(
                            x1 = x1_norm, y1 = y1_norm, x2 = x2_norm, y2 = y2_norm,
                            confidence = maxClassScore,
                            classId = classId,
                            label = label
                        )
                    )
                }
            }
        }

        return detections
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        if (boxes.isEmpty()) return emptyList()

        val sortedBoxes = boxes.sortedByDescending { it.confidence }
        val selectedBoxes = mutableListOf<BoundingBox>()
        val active = BooleanArray(sortedBoxes.size) { true }

        for (i in sortedBoxes.indices) {
            if (!active[i]) continue

            selectedBoxes.add(sortedBoxes[i])

            for (j in (i + 1) until sortedBoxes.size) {
                if (active[j]) {
                    if (calculateIoU(sortedBoxes[i], sortedBoxes[j]) >= Constants.IOU_THRESHOLD) {
                        active[j] = false
                    }
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val xMin = max(box1.x1, box2.x1)
        val yMin = max(box1.y1, box2.y1)
        val xMax = min(box1.x2, box2.x2)
        val yMax = min(box1.y2, box2.y2)

        val intersectionWidth = max(0f, xMax - xMin)
        val intersectionHeight = max(0f, yMax - yMin)
        val intersectionArea = intersectionWidth * intersectionHeight

        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)

        if (box1Area <= 0f || box2Area <= 0f) return 0f

        val unionArea = box1Area + box2Area - intersectionArea
        return if (unionArea <= 1e-6) 0f else (intersectionArea / unionArea).coerceIn(0f, 1f)
    }

    fun close() {
        session?.close()
        ortEnvironment.close()
    }

    interface DetectionListener {
        fun onEmptyDetection()
        fun onDetection(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }
}