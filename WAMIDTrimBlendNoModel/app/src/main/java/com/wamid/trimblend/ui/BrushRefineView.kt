package com.wamid.trimblend.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class BrushRefineView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
): View(ctx, attrs) {
    enum class Mode { INCLUDE, EXCLUDE }
    var mode: Mode = Mode.INCLUDE
    private val path = Path()
    private var bmp: Bitmap? = null
    private var canvasBuf: Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN; style = Paint.Style.STROKE; strokeWidth = 28f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    fun clear() { bmp?.eraseColor(Color.TRANSPARENT); invalidate() }
    fun getStrokesBitmap(): Bitmap {
        ensure()
        return bmp!!
    }

    private fun ensure() {
        if (bmp == null || bmp?.width != width || bmp?.height != height) {
            bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            canvasBuf = Canvas(bmp!!)
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        ensure()
        c.drawBitmap(bmp!!, 0f, 0f, null)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        ensure()
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                path.reset(); path.moveTo(e.x, e.y)
                paint.color = if (mode==Mode.INCLUDE) Color.GREEN else Color.RED
            }
            MotionEvent.ACTION_MOVE -> {
                path.lineTo(e.x, e.y)
                canvasBuf?.drawPath(path, paint)
            }
            MotionEvent.ACTION_UP -> {
                canvasBuf?.drawPath(path, paint); path.reset()
            }
        }
        invalidate(); return true
    }
}
