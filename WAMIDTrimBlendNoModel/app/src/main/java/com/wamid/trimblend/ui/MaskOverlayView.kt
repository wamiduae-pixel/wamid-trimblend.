package com.wamid.trimblend.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.opencv.core.Mat

data class Proposal(val mask01: Mat, val score: Float, val label: String?)

class MaskOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
): View(ctx, attrs) {
    var proposals: List<Proposal> = emptyList()
        set(value) { field = value; invalidate() }
    var onPick: ((Proposal)->Unit)? = null

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        proposals.forEach { p ->
            val bmp = maskToBitmap(p.mask01, width, height)
            c.drawBitmap(bmp, 0f, 0f, null)
        }
    }

    override fun onTouchEvent(evt: MotionEvent): Boolean {
        if (evt.action == MotionEvent.ACTION_UP && proposals.isNotEmpty()) {
            onPick?.invoke(proposals.maxBy { it.score })
            return true
        }
        return true
    }

    private fun maskToBitmap(mask01: Mat, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val buf = IntArray(w*h)
        val sx = mask01.cols().toFloat() / w
        val sy = mask01.rows().toFloat() / h
        var i = 0
        for (y in 0 until h) for (x in 0 until w) {
            val mx = (x*sx).toInt().coerceIn(0, mask01.cols()-1)
            val my = (y*sy).toInt().coerceIn(0, mask01.rows()-1)
            val v = mask01.get(my, mx)[0].toFloat().coerceIn(0f,1f)
            buf[i++] = Color.argb((v*120).toInt(), 0, 200, 255)
        }
        out.setPixels(buf, 0, w, 0, 0, w, h)
        return out
    }
}
