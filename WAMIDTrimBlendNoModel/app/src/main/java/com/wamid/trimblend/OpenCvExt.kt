package com.wamid.trimblend

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

object OpenCvExt {
    fun toBitmap(m: Mat): Bitmap {
        val bmp = createBitmap(m.cols(), m.rows())
        Utils.matToBitmap(m, bmp)
        return bmp
    }
    fun Mat_to01(m: Mat): Mat {
        val out = Mat(); m.convertTo(out, CvType.CV_32F, 1.0/255.0); return out
    }
}
