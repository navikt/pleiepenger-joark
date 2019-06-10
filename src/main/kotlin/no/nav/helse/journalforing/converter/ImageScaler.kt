package no.nav.helse.journalforing.converter

import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.AffineTransformOp.TYPE_BILINEAR
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger: Logger = LoggerFactory.getLogger(ImageScaler::class.java)
private val A4_DIM = Dimension(PDRectangle.A4.width.toInt(), PDRectangle.A4.height.toInt())

object ImageScaler {

    fun downToA4(origImage: ByteArray, format: String): ByteArray {
        logger.trace("Skalerer til A4")
        val image = ImageIO.read(ByteArrayInputStream(origImage))
        logger.trace("Bildestørrelse (bytes) = ${origImage.size}")
        val origDim = Dimension(image.width, image.height)
        logger.trace("Bildeoppløsning (bredde*høyde) =  ${image.width}*${image.height}")
        val rotate = shouldRotate(image)
        logger.trace("Kommer til å rotere bildet = $rotate")
        val newDim = getScaledDimension(origDim, rotate)
        logger.trace("Bildeoppløsning etter nedskalering blir (bredde*høyde) = ${newDim.width}*${newDim.height}")

        return if (newDim == origDim) {
            logger.trace("Den nedskalerte oppløsningen er den samme som på originalbildet. Trenger ikke gjøre noe.")
            origImage
        } else {
            logger.trace("Starter nedskalering av bilde")
            val scaledImg = scaleWithBufferedImageGetScaledInstance(image, newDim)
            logger.trace("Bildet nedskalert.")
            val finalizedImg = if (rotate) {
                logger.trace("Roterer bildet.")
                rotatePortrait(scaledImg)
            } else {
                logger.trace("Roterer ikke bildet.")
                scaledImg
            }
            toBytes(finalizedImg, format)
        }
    }

    private fun shouldRotate(image: BufferedImage) : Boolean {
        return image.width > image.height
    }

    private fun rotatePortrait(image: BufferedImage): BufferedImage {
        var rotatedImage = BufferedImage(image.height, image.width, image.type)
        val transform = AffineTransform()
        transform.rotate(Math.toRadians(90.0), (image.height / 2f).toDouble(), (image.height / 2f).toDouble())
        val op = AffineTransformOp(transform, TYPE_BILINEAR)
        rotatedImage = op.filter(image, rotatedImage)
        return rotatedImage
    }

    private fun getScaledDimension(
        imgSize: Dimension,
        rotate: Boolean
    ): Dimension {

        val originalWidth = imgSize.width
        val originalHeight = imgSize.height

        val a4Width = if (rotate) A4_DIM.height else A4_DIM.width
        val a4Height = if (rotate) A4_DIM.width else A4_DIM.height

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

    private fun scaleWithBufferedImageGetScaledInstance(origImage: BufferedImage, newDim: Dimension): BufferedImage {
        val newWidth = newDim.getWidth().toInt()
        val newHeight = newDim.getHeight().toInt()
        val tempImg = origImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH)
        val scaledImg = BufferedImage(newWidth, newHeight, BufferedImage.TYPE_3BYTE_BGR)
        val g = scaledImg.graphics as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.drawImage(tempImg, 0, 0, null)
        g.dispose()
        return scaledImg
    }

    private fun scaleWithGraphics2dScale(origImage: BufferedImage, newDim: Dimension): BufferedImage {
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
