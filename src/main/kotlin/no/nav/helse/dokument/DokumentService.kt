package no.nav.helse.dokument

import java.net.URL

interface DokumentService {
    fun hentDokumenter(urls: List<URL>) : List<Dokument>
}
