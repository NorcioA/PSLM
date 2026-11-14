package com.example.samplee.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.example.samplee.model.SlotInfo
import kotlin.math.ceil

/**
 * A top-down picture of a parking lot.
 *  - green slot  = free for the chosen date/time  (tap to choose it)
 *  - red slot    = taken (a car is drawn in it)
 *  - blue slot   = the slot you chose
 * Slots sit in rows facing a driving lane; the entrance / exit is at the bottom.
 */
class ParkingLotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface Listener {
        fun onSlotSelected(slot: SlotInfo?)
        fun onTakenSlotTapped(slot: SlotInfo)
    }

    var listener: Listener? = null

    var selectedSlotId: Long? = null
        private set

    val selectedLabel: String?
        get() = slots.firstOrNull { it.id == selectedSlotId }?.slotLabel

    private var slots: List<SlotInfo> = emptyList()
    private val slotRects = ArrayList<RectF>()
    private val laneRects = ArrayList<RectF>()
    private var contentHeight = 0f

    private val d = resources.displayMetrics.density
    private val cols = 6
    private val pad = 12 * d
    private val gap = 6 * d
    private val laneH = 34 * d
    private val pairGap = 10 * d
    private val footerH = 44 * d
    private val header = 10 * d

    private fun sp(value: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private val asphaltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#546E7A") }
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#37474F") }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5")
        strokeWidth = 2 * d
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(14 * d, 10 * d), 0f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
        isFakeBoldText = true
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
        isFakeBoldText = true
        color = Color.WHITE
    }
    private val tmp = RectF()

    // ------------------------------------------------------------------ data

    fun setSlots(newSlots: List<SlotInfo>) {
        slots = newSlots
        // Drop the selection when that slot is no longer free for the new time window.
        val stillFree = newSlots.any { it.id == selectedSlotId && it.isFree }
        if (!stillFree) selectedSlotId = null
        requestLayout()
        invalidate()
    }

    fun clearSelection() {
        selectedSlotId = null
        invalidate()
    }

    // ---------------------------------------------------------------- layout

    private fun buildGeometry(width: Float) {
        slotRects.clear()
        laneRects.clear()
        if (slots.isEmpty()) {
            contentHeight = 120 * d
            return
        }
        val slotW = (width - 2 * pad - (cols - 1) * gap) / cols
        val slotH = slotW * 1.3f
        val rows = ceil(slots.size / cols.toFloat()).toInt()
        val pairs = (rows + 1) / 2
        val pairHeight = 2 * slotH + laneH + pairGap

        for (p in 0 until pairs) {
            val top = header + p * pairHeight
            laneRects.add(RectF(0f, top + slotH, width, top + slotH + laneH))
        }
        for (i in slots.indices) {
            val row = i / cols
            val col = i % cols
            val pairTop = header + (row / 2) * pairHeight
            val top = if (row % 2 == 0) pairTop else pairTop + slotH + laneH
            val left = pad + col * (slotW + gap)
            slotRects.add(RectF(left, top, left + slotW, top + slotH))
        }
        val rowsInLastPair = rows - 2 * (pairs - 1)
        val lastTop = header + (pairs - 1) * pairHeight
        val lastHeight = if (rowsInLastPair == 2) 2 * slotH + laneH else slotH + laneH
        contentHeight = lastTop + lastHeight + footerH
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        buildGeometry(w.toFloat())
        setMeasuredDimension(w, ceil(contentHeight).toInt())
    }

    // ------------------------------------------------------------------ draw

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        canvas.drawRect(0f, 0f, w, height.toFloat(), asphaltPaint)

        if (slots.isEmpty()) {
            labelPaint.color = Color.WHITE
            canvas.drawText("No slots to show", w / 2, height / 2f, labelPaint)
            return
        }

        // driving lanes with a dashed centre line
        for (lane in laneRects) {
            canvas.drawRect(lane, lanePaint)
            canvas.drawLine(pad, lane.centerY(), w - pad, lane.centerY(), dashPaint)
        }

        // slots
        strokePaint.strokeWidth = 2 * d
        for (i in slots.indices) {
            val slot = slots[i]
            val r = slotRects[i]
            val selected = slot.id == selectedSlotId

            fillPaint.color = when {
                selected -> Color.parseColor("#1E88E5")
                slot.isFree -> Color.parseColor("#81C784")
                else -> Color.parseColor("#E57373")
            }
            canvas.drawRoundRect(r, 6 * d, 6 * d, fillPaint)

            strokePaint.strokeWidth = if (selected) 4 * d else 2 * d
            canvas.drawRoundRect(r, 6 * d, 6 * d, strokePaint)

            if (!slot.isFree && !selected) drawCar(canvas, r)

            labelPaint.color = if (selected) Color.WHITE else Color.parseColor("#1B1F2A")
            canvas.drawText(slot.slotLabel, r.centerX(), r.top + 16 * d, labelPaint)
        }

        // entrance / exit
        tmp.set(0f, height - footerH, w, height.toFloat())
        canvas.drawRect(tmp, lanePaint)
        canvas.drawText("▲  ENTRANCE / EXIT", w / 2, tmp.centerY() + 5 * d, footerPaint)
    }

    /** A small car seen from above, drawn in the lower part of a taken slot. */
    private fun drawCar(canvas: Canvas, slot: RectF) {
        val bw = slot.width() * 0.58f
        val bh = slot.height() * 0.5f
        val left = slot.centerX() - bw / 2
        val top = slot.top + slot.height() * 0.38f
        tmp.set(left, top, left + bw, top + bh)
        fillPaint.color = Color.parseColor("#B71C1C")
        canvas.drawRoundRect(tmp, 6 * d, 6 * d, fillPaint)
        tmp.set(left + bw * 0.14f, top + bh * 0.16f, left + bw * 0.86f, top + bh * 0.42f)
        fillPaint.color = Color.parseColor("#FFCDD2")
        canvas.drawRoundRect(tmp, 3 * d, 3 * d, fillPaint)
    }

    // ----------------------------------------------------------------- touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val index = slotRects.indexOfFirst { it.contains(event.x, event.y) }
                if (index >= 0) handleTap(slots[index])
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun handleTap(slot: SlotInfo) {
        if (!slot.isFree) {
            listener?.onTakenSlotTapped(slot)
            return
        }
        if (selectedSlotId == slot.id) {
            selectedSlotId = null // tap again to un-choose
            listener?.onSlotSelected(null)
        } else {
            selectedSlotId = slot.id
            listener?.onSlotSelected(slot)
        }
        invalidate()
    }
}
