package com.wamid.trimblend

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ImageIO {
    fun saveTemp(ctx: Context, bmp: Bitmap, name: String) =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", saveFile(ctx, bmp, name))

    private fun saveFile(ctx: Context, bmp: Bitmap, name: String): File {
        val f = File(ctx.cacheDir, name)
        FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return f
    }
}
