package com.termoak.app.store

import android.graphics.Bitmap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode

/** PNGs as the stores want them: 24-bit RGB (no alpha channel), tagged sRGB. */
object Png {
    fun writeOpaque(bitmap: Bitmap, out: File) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        // Anything translucent goes over black (nothing should be).
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            pixels[i] = if (a == 0xFF) p and 0xFFFFFF else {
                fun ch(s: Int) = ((p shr s) and 0xFF) * a / 255
                (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
            }
        }
        image.setRGB(0, 0, w, h, pixels, 0, w)
        val writer = ImageIO.getImageWritersByFormatName("png").next()
        val type = ImageTypeSpecifier.createFromRenderedImage(image)
        val meta = writer.getDefaultImageMetadata(type, null)
        val root = IIOMetadataNode("javax_imageio_png_1.0")
        root.appendChild(IIOMetadataNode("sRGB").apply { setAttribute("renderingIntent", "Perceptual") })
        meta.mergeTree("javax_imageio_png_1.0", root)
        out.parentFile?.mkdirs()
        out.delete()
        ImageIO.createImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(IIOImage(image, null, meta))
        }
        writer.dispose()
    }
}
