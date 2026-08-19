package com.smartspend.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

data class DailyBarData(
    val day: Int,
    val amount: Double,
    val isSpike: Boolean
)

/**
 * Custom Android View for rendering the Daily Spend Bar Chart with anomaly spike highlights
 * and interactive tap selection with floating callout tooltips.
 */
class DailySpendChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8") // Bright Cyan Accent
    }
    private val selectedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    // Tooltip Paints
    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F172A") // Dark Slate
        style = Paint.Style.FILL
    }
    private val tooltipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F8FAFC") // White
        textSize = 28f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private var dataList: List<DailyBarData> = emptyList()
    private var maxAmount: Double = 1.0
    private var selectedIndex: Int = -1

    var onBarSelectedListener: ((DailyBarData) -> Unit)? = null

    fun setData(bars: List<DailyBarData>) {
        dataList = bars
        maxAmount = bars.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
        selectedIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (dataList.isEmpty()) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                val paddingLeft = paddingLeft.toFloat() + 16f
                val paddingRight = paddingRight.toFloat() + 16f
                val availableWidth = width.toFloat() - paddingLeft - paddingRight

                val barCount = dataList.size
                val barWidth = (availableWidth / barCount) * 0.65f
                val gap = (availableWidth - (barWidth * barCount)) / (barCount + 1).coerceAtLeast(1)

                val touchX = event.x

                var foundIndex = -1
                for (i in dataList.indices) {
                    val left = paddingLeft + gap + i * (barWidth + gap) - (gap / 2f)
                    val right = left + barWidth + (gap / 2f)
                    if (touchX in left..right) {
                        foundIndex = i
                        break
                    }
                }

                if (foundIndex != -1 && foundIndex != selectedIndex) {
                    selectedIndex = foundIndex
                    invalidate()
                    onBarSelectedListener?.invoke(dataList[foundIndex])
                    performClick()
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataList.isEmpty()) return

        val paddingLeft = paddingLeft.toFloat() + 16f
        val paddingRight = paddingRight.toFloat() + 16f
        val paddingTop = paddingTop.toFloat() + 52f
        val paddingBottom = paddingBottom.toFloat() + 40f

        val availableWidth = width.toFloat() - paddingLeft - paddingRight
        val availableHeight = height.toFloat() - paddingTop - paddingBottom

        val barCount = dataList.size
        val barWidth = (availableWidth / barCount) * 0.65f
        val gap = (availableWidth - (barWidth * barCount)) / (barCount + 1).coerceAtLeast(1)

        var selectedRect: RectF? = null
        var selectedItem: DailyBarData? = null

        for (i in dataList.indices) {
            val item = dataList[i]
            val left = paddingLeft + gap + i * (barWidth + gap)
            val right = left + barWidth

            val barHeight = ((item.amount / maxAmount) * availableHeight).toFloat().coerceAtLeast(12f)
            val top = paddingTop + availableHeight - barHeight
            val bottom = paddingTop + availableHeight

            val rect = RectF(left, top, right, bottom)

            if (i == selectedIndex) {
                selectedRect = rect
                selectedItem = item
                canvas.drawRoundRect(rect, 8f, 8f, selectedBarPaint)
                canvas.drawRoundRect(rect, 8f, 8f, selectedStrokePaint)
            } else {
                if (item.isSpike) {
                    barPaint.color = Color.parseColor("#EF4444") // Anomaly Red
                } else {
                    barPaint.color = Color.parseColor("#16803C") // Emerald Green
                }
                canvas.drawRoundRect(rect, 8f, 8f, barPaint)
            }

            // Draw day label every 3 days, selected day, or spike days
            if (i == selectedIndex) {
                textPaint.color = Color.parseColor("#38BDF8")
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText("${item.day}", left + barWidth / 2f, height.toFloat() - 8f, textPaint)
                textPaint.color = Color.parseColor("#94A3B8")
                textPaint.typeface = android.graphics.Typeface.DEFAULT
            } else if (barCount <= 15 || item.day % 3 == 1 || item.isSpike) {
                canvas.drawText("${item.day}", left + barWidth / 2f, height.toFloat() - 8f, textPaint)
            }
        }

        // Draw Floating Callout Tooltip above the selected bar
        if (selectedRect != null && selectedItem != null) {
            val labelText = "Day ${selectedItem.day}: ₹%.2f".format(selectedItem.amount)
            val textWidth = tooltipTextPaint.measureText(labelText)
            val tooltipPaddingHorizontal = 20f
            val tooltipHeight = 44f

            var tooltipCenterX = selectedRect.centerX()
            val minX = tooltipPaddingHorizontal + (textWidth / 2f)
            val maxX = width.toFloat() - tooltipPaddingHorizontal - (textWidth / 2f)
            tooltipCenterX = tooltipCenterX.coerceIn(minX, maxX)

            val tooltipTop = (selectedRect.top - tooltipHeight - 12f).coerceAtLeast(4f)
            val tooltipBottom = tooltipTop + tooltipHeight
            val tooltipLeft = tooltipCenterX - (textWidth / 2f) - tooltipPaddingHorizontal
            val tooltipRight = tooltipCenterX + (textWidth / 2f) + tooltipPaddingHorizontal

            val tooltipBgRect = RectF(tooltipLeft, tooltipTop, tooltipRight, tooltipBottom)
            canvas.drawRoundRect(tooltipBgRect, 12f, 12f, tooltipBgPaint)
            canvas.drawText(labelText, tooltipCenterX, tooltipBottom - 12f, tooltipTextPaint)
        }
    }
}
