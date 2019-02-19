package no.nav.helse.dokument

interface DokumentService {
    fun hentDokumenter(paths: List<String>) : List<Dokument>
}
