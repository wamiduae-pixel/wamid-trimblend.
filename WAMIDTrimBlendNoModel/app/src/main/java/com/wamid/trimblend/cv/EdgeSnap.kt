package com.wamid.trimblend.cv

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object EdgeSnap {
    fun snapPoints(base: Mat, points: List<Point>, win: Int = 6): List<Point> {
        val gray = Mat(); Imgproc.cvtColor(base, gray, Imgproc.COLOR_BGR2GRAY)
        val edges = Mat(); Imgproc.Canny(gray, edges, 80.0, 160.0)
        val out = ArrayList<Point>(points.size)
        for (p in points) {
            var best = p; var bestV = -1.0
            val x0 = (p.x - win).toInt(); val y0 = (p.y - win).toInt()
            for (y in y0 until y0 + 2*win + 1) {
                for (x in x0 until x0 + 2*win + 1) {
                    if (x in 0 until edges.cols() && y in 0 until edges.rows()) {
                        val v = edges.get(y, x)?.get(0)?.toDouble() ?: 0.0
                        if (v > bestV) { bestV = v; best = Point(x.toDouble(), y.toDouble()) }
                    }
                }
            }
            out += best
        }
        return out
    }
}
