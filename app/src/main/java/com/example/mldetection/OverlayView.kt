package com.example.mldetection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var boundingBoxes = listOf<BoundingBox>()
    private var inferenceTime = 0L
    private val objectCounts = mutableMapOf<String, Int>()

    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textBackgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 160
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 50f
    }

    private val bounds = Rect()

    fun clear() {
        boundingBoxes = listOf()
        objectCounts.clear()
        invalidate()
    }

    fun setResults(detections: List<BoundingBox>, inferenceMs: Long) {
        boundingBoxes = detections
        inferenceTime = inferenceMs

        objectCounts.clear()
        for (box in detections) {
            objectCounts[box.label] = (objectCounts[box.label] ?: 0) + 1
        }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw bounding boxes
        for (box in boundingBoxes) {
            val left = box.x1 * width
            val top = box.y1 * height
            val right = box.x2 * width
            val bottom = box.y2 * height

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val label = "${box.label}: ${(box.confidence * 100).toInt()}%"
            textPaint.getTextBounds(label, 0, label.length, bounds)

            canvas.drawRect(
                left,
                top - bounds.height() - 8,
                left + bounds.width() + 8,
                top,
                textBackgroundPaint
            )

            canvas.drawText(label, left + 4, top - 4, textPaint)
        }

        // Draw counts and inference time in top corner
        var y = 60f
        val padding = 10f

        // Inference time
        val timeText = "${inferenceTime}ms"
        textPaint.getTextBounds(timeText, 0, timeText.length, bounds)
        canvas.drawRect(
            padding,
            y - bounds.height() - padding,
            padding + bounds.width() + padding * 2,
            y + padding,
            textBackgroundPaint
        )
        canvas.drawText(timeText, padding * 2, y, textPaint)
        y += bounds.height() + padding * 3

        // Object counts
        for ((label, count) in objectCounts) {
            val text = "$label: $count"
            textPaint.getTextBounds(text, 0, text.length, bounds)

            canvas.drawRect(
                padding,
                y - bounds.height() - padding,
                padding + bounds.width() + padding * 2,
                y + padding,
                textBackgroundPaint
            )
            canvas.drawText(text, padding * 2, y, textPaint)
            y += bounds.height() + padding * 2
        }
    }
}