package no.nav.helse.journalforing.converter

import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.AffineTransformOp.TYPE_BILINEAR
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger: Logger = LoggerFactory.getLogger("nav.ImageScaler")

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
        logger.trace("Henter ny mål for bildet")
        val newWidth = newDim.getWidth().toInt()
        val newHeight = newDim.getHeight().toInt()
        logger.trace("newWidth=$newWidth")
        logger.trace("newHeight=$newHeight")
        logger.trace("Genererer et bilde med riktig dimensjon")
        val destinationImage = BufferedImage(newWidth, newHeight, origImage.type)
        logger.trace("Kalkulerer skalering")
        val sx = newWidth.toDouble() / origImage.width.toDouble()
        val sy = newHeight.toDouble() / origImage.height.toDouble()
        logger.trace("sx=$sx")
        logger.trace("sy=$sy")
        logger.trace("Genererer Graphics2d Object")
        val graphics2D = destinationImage.createGraphics()
        logger.trace("Setter Rendering Hints")
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        logger.trace("Setter skaleringsverdier")
        graphics2D.scale(sx, sy)
        logger.trace("Genererer det nye bildet med ritkig oppløsning")
        graphics2D.drawImage(origImage, 0, 0, null)
        graphics2D.dispose()
        return destinationImage
    }

    private fun toBytes(img: BufferedImage, format: String): ByteArray {
        ByteArrayOutputStream().use { baos ->
            ImageIO.write(img, format, baos)
            return baos.toByteArray()
        }
    }
}
