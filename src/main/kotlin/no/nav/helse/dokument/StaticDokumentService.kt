package no.nav.helse.dokument

import io.ktor.http.ContentType

class StaticDokumentService : DokumentService {

    private val staticDocumenter = listOf(
        Dokument(
            tittel = "Søknadsdetaljer",
            contentType = ContentType("application", "pdf"),
            content = "generert_soknad.pdf".fromResources()
        ),
        Dokument(
            tittel = "Legeerklæring",
            contentType = ContentType("application", "pdf"),
            content = "legeerklaering.pdf".fromResources()
        )
    )

    override fun hentDokumenter(paths: List<String>): List<Dokument> {
        return staticDocumenter
    }

    private fun String.fromResources() : ByteArray {
        return Thread.currentThread().contextClassLoader.getResource(this).readBytes()
    }
}