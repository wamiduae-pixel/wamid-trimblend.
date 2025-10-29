package com.wamid.trimblend.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

data class PolyPoint(var x: Float, var y: Float)

class PolygonMaskView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
): View(ctx, attrs) {
    val pts = mutableListOf<PolyPoint>()
    var closed = false
    var onChange: ((List<PointF>, Boolean)->Unit)? = null

    private val paintEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(160, 0, 255, 200); strokeWidth = 3f; style = Paint.Style.STROKE }
    private val paintPt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (pts.size > 1) {
            val path = Path()
            path.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
            if (closed) path.close()
            c.drawPath(path, paintEdge)
        }
        pts.forEach { c.drawCircle(it.x, it.y, 4f, paintPt) }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (closed) { pts.clear(); closed = false }
                pts += PolyPoint(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pts.isNotEmpty()) { pts[pts.size-1].x = e.x; pts[pts.size-1].y = e.y }
            }
            MotionEvent.ACTION_UP -> {
                if (pts.size >= 3) {
                    val d = hypot(e.x - pts[0].x, e.y - pts[0].y)
                    if (d < 18f) closed = true
                }
                onChange?.invoke(pts.map { PointF(it.x, it.y) }, closed)
            }
        }
        invalidate(); return true
    }
}
