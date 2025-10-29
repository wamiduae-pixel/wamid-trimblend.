package com.wamid.trimblend

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

object BlendSingleOverlay {
    fun blend(
        baseBgr: Mat,
        mask01: Mat,
        overlayBgra: Mat,
        fit: String,
        scale: Double,
        rotateDeg: Double,
        offsetX: Int,
        offsetY: Int,
        opacity: Double,
        toneMatch: Boolean,
        aoStrength: Double
    ): Mat {
        val out = baseBgr.clone()
        val bbox = bboxFromMask(mask01)
        val roiW = bbox.width; val roiH = bbox.height
        val fitMat = fitTo(overlayBgra, roiW, roiH, fit)
        val rigidMat = rigid(fitMat, scale, rotateDeg)
        val canvas = pasteCentered(out.size(), rigidMat, bbox.center(), offsetX, offsetY)

        val bgra = ArrayList<Mat>(4)
        Core.split(canvas, bgra)
        val overBgr = Mat()
        Core.merge(listOf(bgra[0], bgra[1], bgra[2]), overBgr)
        val overA = Mat(); bgra[3].convertTo(overA, CvType.CV_32F, 1.0/255.0)

        val alpha = Mat(); Core.multiply(overA, mask01, alpha)
        Imgproc.GaussianBlur(alpha, alpha, Size(0.0,0.0), 0.8)
        Core.multiply(alpha, Scalar(opacity), alpha)

        val overTone = if (toneMatch) toneMatchToBase(overBgr, baseBgr, mask01) else overBgr
        return alphaBlend(out, overTone, alpha)
    }

    private fun bboxFromMask(mask01: Mat): Rect {
        val u8 = Mat(); mask01.convertTo(u8, CvType.CV_8U, 255.0)
        val cnt = mutableListOf<MatOfPoint>()
        Imgproc.findContours(u8, cnt, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        if (cnt.isEmpty()) return Rect(0,0,mask01.width(), mask01.height())
        var r = Imgproc.boundingRect(cnt[0])
        for (i in 1 until cnt.size) r = r.union(Imgproc.boundingRect(cnt[i]))
        return r
    }
    private fun fitTo(bgra: Mat, tw: Int, th: Int, mode: String): Mat {
        val s = if (mode == "contain") min(tw / bgra.width().toDouble(), th / bgra.height().toDouble())
                else max(tw / bgra.width().toDouble(), th / bgra.height().toDouble())
        val out = Mat(); Imgproc.resize(bgra, out, Size(bgra.width()*s, bgra.height()*s)); return out
    }
    private fun rigid(bgra: Mat, scale: Double, rotateDeg: Double): Mat {
        val scaled = Mat(); Imgproc.resize(bgra, scaled, Size(bgra.width()*scale, bgra.height()*scale))
        val M = Imgproc.getRotationMatrix2D(Point(scaled.width()/2.0, scaled.height()/2.0), rotateDeg, 1.0)
        val out = Mat(); Imgproc.warpAffine(scaled, out, M, scaled.size(), Imgproc.INTER_LINEAR, Core.BORDER_TRANSPARENT, Scalar(0.0,0.0,0.0,0.0))
        return out
    }
    private fun pasteCentered(canvasSize: Size, tile: Mat, center: Point, dx: Int, dy: Int): Mat {
        val out = Mat.zeros(canvasSize, CvType.CV_8UC4)
        val x0 = (center.x - tile.width()/2 + dx).toInt(); val y0 = (center.y - tile.height()/2 + dy).toInt()
        val roi = Rect(
            max(0, x0), max(0, y0),
            min(tile.width(), canvasSize.width.toInt() - max(0, x0)),
            min(tile.height(), canvasSize.height.toInt() - max(0, y0))
        )
        if (roi.width <= 0 || roi.height <= 0) return out
        tile.submat(Rect(max(0, -x0), max(0, -y0), roi.width, roi.height))
            .copyTo(out.submat(roi))
        return out
    }
    private fun toneMatchToBase(overlayBgr: Mat, baseBgr: Mat, mask01: Mat): Mat {
        val yBase = luminance(baseBgr); val yOver = luminance(overlayBgr)
        val bin = Mat(); Imgproc.threshold(mask01, bin, 0.5, 1.0, Imgproc.THRESH_BINARY)
        val mBase = Core.mean(yBase, bin.convertToMask())
        val mOver = Core.mean(yOver, bin.convertToMask())
        val ratio = (mBase.`val`[0] + 1e-6) / (mOver.`val`[0] + 1e-6)
        val out = Mat(); Core.multiply(overlayBgr, Scalar(ratio,ratio,ratio), out)
        Core.min(out, Scalar(255.0,255.0,255.0), out)
        return out
    }
    private fun luminance(bgr: Mat): Mat {
        val ch = mutableListOf<Mat>(); Core.split(bgr, ch)
        val y = Mat(); Core.addWeighted(ch[0],0.114, ch[1],0.587, 0.0, y)
        Core.addWeighted(y,1.0, ch[2],0.299, 0.0, y); return y
    }
    private fun alphaBlend(base: Mat, over: Mat, alpha01: Mat): Mat {
        val a3 = mutableListOf(alpha01, alpha01, alpha01); val alpha3 = Mat(); Core.merge(a3, alpha3)
        val inv = Mat(); Core.subtract(Scalar(1.0,1.0,1.0), alpha3, inv)
        val o = Mat(); val b = Mat()
        Core.multiply(over, alpha3, o)
        Core.multiply(base, inv, b)
        Core.add(o, b, b)
        b.convertTo(b, CvType.CV_8U)
        return b
    }
    private fun Mat.convertToMask(): Mat { val u = Mat(); this.convertTo(u, CvType.CV_8U, 255.0); return u }
}
