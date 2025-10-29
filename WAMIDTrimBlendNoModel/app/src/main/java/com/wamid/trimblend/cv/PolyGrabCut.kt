package com.wamid.trimblend.cv

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object PolyGrabCut {
    fun run(base: Mat, poly: List<Point>): Mat {
        val mask = Mat.zeros(base.size(), CvType.CV_8U)
        Imgproc.fillPoly(mask, listOf(MatOfPoint(*poly.map { Point(it.x, it.y) }.toTypedArray())), Scalar(255.0))
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0,5.0))
        val sureFg = Mat(); Imgproc.erode(mask, sureFg, k)
        val inv = Mat(); Core.bitwise_not(mask, inv)
        val sureBg = Mat(); Imgproc.dilate(inv, sureBg, k)

        val gc = Mat(base.size(), CvType.CV_8U, Scalar(Imgproc.GC_PR_BGD.toDouble()))
        gc.setTo(Scalar(Imgproc.GC_PR_FGD.toDouble()), mask)
        gc.setTo(Scalar(Imgproc.GC_FGD.toDouble()), sureFg)
        gc.setTo(Scalar(Imgproc.GC_BGD.toDouble()), sureBg)
        val bg = Mat(); val fg = Mat()
        Imgproc.grabCut(base, gc, Rect(), bg, fg, 2, Imgproc.GC_INIT_WITH_MASK)

        val prFg = Mat(); Core.compare(gc, Scalar(Imgproc.GC_PR_FGD.toDouble()), prFg, Core.CMP_EQ)
        val f = Mat(); Core.compare(gc, Scalar(Imgproc.GC_FGD.toDouble()), f, Core.CMP_EQ)
        val out = Mat(); Core.bitwise_or(prFg, f, out)
        out.convertTo(out, CvType.CV_32F, 1.0/255.0)
        return out
    }
}
