package com.smartspend.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class CategorySlice(
    val category: String,
    val amount: Double,
    val percentage: Int,
    val colorHex: String
)

/**
 * Custom Android View for drawing a Category Spending Donut Chart.
 */
class CategoryDonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 38f
        strokeCap = Paint.Cap.ROUND
    }

    private var slices: List<CategorySlice> = emptyList()

    fun setSlices(data: List<CategorySlice>) {
        slices = data
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (slices.isEmpty()) {
            // Empty state placeholder ring
            paint.color = Color.parseColor("#E2E8F0")
            val size = Math.min(width, height).toFloat() - 40f
            val cx = width / 2f
            val cy = height / 2f
            canvas.drawCircle(cx, cy, size / 2f, paint)
            return
        }

        val padding = 40f
        val diameter = Math.min(width, height).toFloat() - padding * 2
        val left = (width - diameter) / 2f
        val top = (height - diameter) / 2f
        val rect = RectF(left, top, left + diameter, top + diameter)

        val totalAmt = slices.sumOf { it.amount }.coerceAtLeast(1.0)
        var startAngle = -90f

        for (slice in slices) {
            val sweepAngle = ((slice.amount / totalAmt) * 360f).toFloat()
            if (sweepAngle > 0f) {
                try {
                    paint.color = Color.parseColor(slice.colorHex)
                } catch (e: Exception) {
                    paint.color = Color.parseColor("#6366F1")
                }
                // Leave a 4-degree gap between donut slices
                val gap = if (slices.size > 1) 4f else 0f
                canvas.drawArc(rect, startAngle, sweepAngle - gap, false, paint)
                startAngle += sweepAngle
            }
        }
    }
}
