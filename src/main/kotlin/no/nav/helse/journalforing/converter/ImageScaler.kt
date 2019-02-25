package no.nav.helse.journalforing.converter

import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Image
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.AffineTransformOp.TYPE_BILINEAR
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object ImageScaler {

    fun downToA4(origImage: ByteArray, format: String): ByteArray {
        val A4 = PDRectangle.A4
        var image = ImageIO.read(ByteArrayInputStream(origImage))

        image = rotatePortrait(image)

        val pdfPageDim = Dimension(A4.width.toInt(), A4.height.toInt())
        val origDim = Dimension(image.width, image.height)
        val newDim = getScaledDimension(origDim, pdfPageDim)

        return if (newDim == origDim) {
            origImage
        } else {
            val scaledImg = scaleDown(image, newDim)
            toBytes(scaledImg, format)
        }
    }

    private fun rotatePortrait(image: BufferedImage): BufferedImage {
        if (image.height >= image.width) {
            return image
        }

        var rotatedImage = BufferedImage(image.height, image.width, image.type)
        val transform = AffineTransform()
        transform.rotate(Math.toRadians(90.0), (image.height / 2f).toDouble(), (image.height / 2f).toDouble())
        val op = AffineTransformOp(transform, TYPE_BILINEAR)
        rotatedImage = op.filter(image, rotatedImage)
        return rotatedImage
    }

    private fun getScaledDimension(imgSize: Dimension, a4: Dimension): Dimension {
        val originalWidth = imgSize.width
        val originalHeight = imgSize.height
        val a4Width = a4.width
        val a4Height = a4.height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > a4Width) {
            newWidth = a4Width
            newHeight = newWidth * originalHeight / originalWidth
        }

        if (newHeight > a4Height) {
            newHeight = a4Height
            newWidth = newHeight * originalWidth / originalHeight
        }

        return Dimension(newWidth, newHeight)
    }

    private fun scaleDown(origImage: BufferedImage, newDim: Dimension): BufferedImage {
        val newWidth = newDim.getWidth().toInt()
        val newHeight = newDim.getHeight().toInt()
        val tempImg = origImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
        val scaledImg = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_3BYTE_BGR)
        val g = scaledImg.graphics as Graphics2D
        g.drawImage(tempImg, 0, 0, null)
        g.dispose()
        return scaledImg
    }

    private fun toBytes(img: BufferedImage, format: String): ByteArray {
        ByteArrayOutputStream().use { baos ->
            ImageIO.write(img, format, baos)
            return baos.toByteArray()
        }
    }
}
