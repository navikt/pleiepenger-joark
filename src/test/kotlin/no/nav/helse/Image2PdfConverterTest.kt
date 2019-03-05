package no.nav.helse

import no.nav.helse.journalforing.converter.ImageScaler
import java.io.File
import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test

class Image2PdfConverterTest {

    @Test
    @Ignore
    fun `Skaler bilde`() {
        val hoyopplost = ImageScaler.downToA4("hoyopplost.jpg".fromResources(), "jpeg")
        val pathHotopplost = "${System.getProperty("user.dir")}/scaled-image-hoyopplost-${UUID.randomUUID()}.jpg"
        val fileHoyopplost = File(pathHotopplost)
        fileHoyopplost.writeBytes(hoyopplost)

        val widescreen = ImageScaler.downToA4("widescreen.jpg".fromResources(), "jpeg")
        val pathWidescreen = "${System.getProperty("user.dir")}/scaled-image-widescreen-${UUID.randomUUID()}.jpg"
        val fileWidescreen = File(pathWidescreen)
        fileWidescreen.writeBytes(widescreen)
    }
}