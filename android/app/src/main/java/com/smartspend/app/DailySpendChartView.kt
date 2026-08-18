package com.smartspend.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

data class DailyBarData(
    val day: Int,
    val amount: Double,
    val isSpike: Boolean
)

/**
 * Custom Android View for rendering the Daily Spend Bar Chart with anomaly spike highlights.
 */
class DailySpendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private var dataList: List<DailyBarData> = emptyList()
    private var maxAmount: Double = 1.0

    fun setData(bars: List<DailyBarData>) {
        dataList = bars
        maxAmount = bars.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataList.isEmpty()) return

        val paddingLeft = paddingLeft.toFloat() + 16f
        val paddingRight = paddingRight.toFloat() + 16f
        val paddingTop = paddingTop.toFloat() + 16f
        val paddingBottom = paddingBottom.toFloat() + 40f

        val availableWidth = width.toFloat() - paddingLeft - paddingRight
        val availableHeight = height.toFloat() - paddingTop - paddingBottom

        val barCount = dataList.size
        val barWidth = (availableWidth / barCount) * 0.65f
        val gap = (availableWidth - (barWidth * barCount)) / (barCount + 1).coerceAtLeast(1)

        for (i in dataList.indices) {
            val item = dataList[i]
            val left = paddingLeft + gap + i * (barWidth + gap)
            val right = left + barWidth

            val barHeight = ((item.amount / maxAmount) * availableHeight).toFloat().coerceAtLeast(12f)
            val top = paddingTop + availableHeight - barHeight
            val bottom = paddingTop + availableHeight

            if (item.isSpike) {
                barPaint.color = Color.parseColor("#EF4444") // Anomaly Red
            } else {
                barPaint.color = Color.parseColor("#16803C") // Primary Emerald Green
            }

            val rect = RectF(left, top, right, bottom)
            canvas.drawRoundRect(rect, 8f, 8f, barPaint)

            // Draw day label every 3 days or for peak/spike days to avoid clutter
            if (barCount <= 15 || item.day % 3 == 1 || item.isSpike) {
                canvas.drawText("${item.day}", left + barWidth / 2f, height.toFloat() - 8f, textPaint)
            }
        }
    }
}
