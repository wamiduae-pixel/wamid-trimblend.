package com.wamid.trimblend

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wamid.trimblend.databinding.ActivityMainBinding
import com.wamid.trimblend.ui.BrushRefineView
import com.wamid.trimblend.ui.PolygonMaskView
import com.wamid.trimblend.ui.Proposal
import com.wamid.trimblend.ui.MaskOverlayView
import org.opencv.android.OpenCVLoader
import org.opencv.core.*
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private var uriBase: Uri? = null
    private var uriMask: Uri? = null
    private var uriOverlay: Uri? = null

    private var fitMode = "cover"
    private var toneMatchOn = true
    private var aoStrength = 0.15

    private val pickBase = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uriBase = it; showPreview() }
    }
    private val pickMask = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uriMask = it; showPreview() }
    }
    private val pickOverlay = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uriOverlay = it; showPreview() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenCVLoader.initLocal()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnBase.setOnClickListener { pickBase.launch(arrayOf("image/*")) }
        b.btnMask.setOnClickListener { pickMask.launch(arrayOf("image/*")) }
        b.btnOverlay.setOnClickListener { pickOverlay.launch(arrayOf("image/*")) }

        b.btnCover.setOnClickListener { fitMode = "cover" }
        b.btnContain.setOnClickListener { fitMode = "contain" }
        b.btnToneMatch.setOnClickListener {
            toneMatchOn = !toneMatchOn
            b.btnToneMatch.text = "Tone Match: " + if (toneMatchOn) "ON" else "OFF"
        }
        b.btnAo.setOnClickListener {
            aoStrength = when {
                aoStrength <= 0.05 -> 0.15
                aoStrength <= 0.2 -> 0.30
                else -> 0.0
            }
            b.btnAo.text = "AO: %s".format(String.format("%.2f", aoStrength))
        }

        setupNoModelUI()

        b.btnRender.setOnClickListener {
            val bmp = processOnce(preview = false) ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, ImageIO.saveTemp(this@MainActivity, bmp, "wamid_result.png"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share result"))
        }
    }

    private fun showPreview() {
        if (uriBase != null && uriOverlay != null) {
            val result = processOnce(preview = true)
            if (result != null) b.imgPreview.setImageBitmap(result)
        }
    }

    private fun processOnce(preview: Boolean): Bitmap? {
        val base = uriBase?.let { readMat(it, asColor = true) } ?: return null
        val overlay = uriOverlay?.let { readMat(it, asColor = true, keepAlpha = true) } ?: return null

        // If user provided a mask, use it; else no blend yet (wait for polygon/refine)
        val mask = uriMask?.let { readMask01(it, base.size()) }

        if (mask != null) {
            val result = BlendSingleOverlay.blend(
                baseBgr = base,
                mask01 = mask,
                overlayBgra = overlay,
                fit = fitMode,
                scale = b.seekScale.progress / 100.0,
                rotateDeg = b.seekRotate.progress - 180.0,
                offsetX = 0, offsetY = 0,
                opacity = b.seekOpacity.progress / 100.0,
                toneMatch = toneMatchOn,
                aoStrength = aoStrength
            )
            return OpenCvExt.toBitmap(result)
        }
        return OpenCvExt.toBitmap(base)
    }

    private fun setupNoModelUI() {
        // Polygon mode
        b.btnDetect.text = "Polygon Mode"
        b.btnDetect.setOnClickListener {
            b.polygonView.visibility = android.view.View.VISIBLE
            b.brushView.visibility = android.view.View.GONE
        }
        b.polygonView.onChange = { pts, closed ->
            if (closed && pts.size >= 3) {
                val base = readMat(uriBase ?: return@onChange, asColor = true) ?: return@onChange
                val ocvPts = pts.map { Point(it.x.toDouble(), it.y.toDouble()) }
                val snapped = com.wamid.trimblend.cv.EdgeSnap.snapPoints(base, ocvPts)
                val mask01 = com.wamid.trimblend.cv.PolyGrabCut.run(base, snapped)
                runBlendWithMask(mask01)
            }
        }

        // Refine mode
        b.btnRefine.setOnClickListener {
            b.brushView.visibility = android.view.View.VISIBLE
            b.brushToolbar.visibility = android.view.View.VISIBLE
        }
        b.btnBrushFg.setOnClickListener { b.brushView.mode = BrushRefineView.Mode.INCLUDE }
        b.btnBrushBg.setOnClickListener { b.brushView.mode = BrushRefineView.Mode.EXCLUDE }
        b.btnBrushRun.setOnClickListener {
            val base = readMat(uriBase ?: return@setOnClickListener, asColor = true) ?: return@setOnClickListener
            val strokes = b.brushView.getStrokesBitmap()
            // start from last mask if any: use preview alpha from maskOverlay as fallback (not stored here)
            val init = Mat.zeros(base.size(), CvType.CV_32F)
            val refined = refineWithGrabCutFromBitmap(base, init, strokes)
            runBlendWithMask(refined)
            b.brushToolbar.visibility = android.view.View.GONE
            b.brushView.visibility = android.view.View.GONE
            b.brushView.clear()
        }
    }

    private fun runBlendWithMask(mask01: Mat) {
        val base = readMat(uriBase!!, asColor = true)!!
        val overlay = readMat(uriOverlay!!, asColor = true, keepAlpha = true)!!
        Imgproc.GaussianBlur(mask01, mask01, Size(0.0,0.0), 0.8)
        val result = BlendSingleOverlay.blend(
            baseBgr = base,
            mask01 = mask01,
            overlayBgra = overlay,
            fit = fitMode,
            scale = b.seekScale.progress / 100.0,
            rotateDeg = b.seekRotate.progress - 180.0,
            offsetX = 0, offsetY = 0,
            opacity = b.seekOpacity.progress / 100.0,
            toneMatch = toneMatchOn,
            aoStrength = aoStrength
        )
        b.imgPreview.setImageBitmap(OpenCvExt.toBitmap(result))
    }

    // I/O helpers
    private fun readMat(uri: Uri, asColor: Boolean, keepAlpha: Boolean = false): Mat? {
        val isr = contentResolver.openInputStream(uri) ?: return null
        val bytes = isr.readBytes()
        val buf = MatOfByte(*bytes)
        val flags = when {
            keepAlpha -> Imgcodecs.IMREAD_UNCHANGED
            asColor -> Imgcodecs.IMREAD_COLOR
            else -> Imgcodecs.IMREAD_GRAYSCALE
        }
        return Imgcodecs.imdecode(buf, flags)
    }

    private fun readMask01(uri: Uri, sizeTo: Size): Mat {
        val m = readMat(uri, asColor = false)!!
        val gray = if (m.channels() == 1) m else {
            val g = Mat(); Imgproc.cvtColor(m, g, Imgproc.COLOR_BGR2GRAY); g
        }
        val f = Mat(); gray.convertTo(f, CvType.CV_32F, 1.0/255.0)
        val out = Mat(); Imgproc.resize(f, out, sizeTo)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0,3.0))
        val bin = Mat(); Imgproc.threshold(out, bin, 0.5, 1.0, Imgproc.THRESH_BINARY)
        Imgproc.erode(bin, bin, kernel)
        return bin
    }

    private fun refineWithGrabCutFromBitmap(base: Mat, initMask01: Mat, strokesBmp: Bitmap): Mat {
        val gc = Mat(base.size(), CvType.CV_8U)
        gc.setTo(Scalar(Imgproc.GC_PR_BGD.toDouble()))
        val initBin = Mat(); Imgproc.threshold(initMask01, initBin, 0.5, 1.0, Imgproc.THRESH_BINARY)
        gc.setTo(Scalar(Imgproc.GC_PR_FGD.toDouble()), initBin)

        val sx = Mat(strokesBmp.height, strokesBmp.width, CvType.CV_8UC4)
        org.opencv.android.Utils.bitmapToMat(strokesBmp, sx)
        Imgproc.resize(sx, sx, base.size())
        val channels = mutableListOf<Mat>(); Core.split(sx, channels)
        val g = channels[1]; val r = channels[2]
        val sureFg = Mat(); Imgproc.threshold(g, sureFg, 200.0, 255.0, Imgproc.THRESH_BINARY)
        val sureBg = Mat(); Imgproc.threshold(r, sureBg, 200.0, 255.0, Imgproc.THRESH_BINARY)

        gc.setTo(Scalar(Imgproc.GC_FGD.toDouble()), sureFg)
        gc.setTo(Scalar(Imgproc.GC_BGD.toDouble()), sureBg)

        val bgModel = Mat(); val fgModel = Mat()
        Imgproc.grabCut(base, gc, Rect(), bgModel, fgModel, 2, Imgproc.GC_INIT_WITH_MASK)

        val prFg = Mat(); Core.compare(gc, Scalar(Imgproc.GC_PR_FGD.toDouble()), prFg, Core.CMP_EQ)
        val fg = Mat(); Core.compare(gc, Scalar(Imgproc.GC_FGD.toDouble()), fg, Core.CMP_EQ)
        val out = Mat(); Core.bitwise_or(prFg, fg, out)
        out.convertTo(out, CvType.CV_32F, 1.0/255.0)
        return out
    }
}