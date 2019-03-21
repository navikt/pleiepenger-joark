package no.nav.helse.journalforing.gateway

internal data class JournalPostRequest(
    val forsokEndeligJF: Boolean,
    val forsendelseInformasjon: ForsendelseInformasjon,
    val dokumentInfoHoveddokument: JoarkDokument,
    val dokumentInfoVedlegg : List<JoarkDokument>
)


internal data class ForsendelseInformasjon(
    val tittel: String,
    val bruker: Map<String, Map<String, Map<String, String>>>,
    val avsender: Map<String, Map<String, Map<String, String>>>,
    val tema: String, /// OMS
    val kanalReferanseId: String, // Vår unike ID
    val forsendelseMottatt: String, // yyyy-MM-dd'T'HH:mm:ssZ
    val forsendelseInnsendt: String, // yyyy-MM-dd'T'HH:mm:ssZ
    val mottaksKanal: String, // NAV_NO
    val arkivSak: ArkivSak? = null // Referense til sak. Per nå opprettes sak i Gosys så denne blir ikke satt.
)

internal data class JoarkDokument(
    val tittel: String,
    val dokumentTypeId: String? = null, // Enten må dokumentId være satt
    val brevkode: String? = null, // Eller brevkode + dokumentkategori
    val dokumentkategori: String? = null,
    val dokumentVariant: List<DokumentVariant>
)

internal data class DokumentVariant(
    val arkivFilType: ArkivFilType,
    val variantFormat: VariantFormat,
    val dokument: ByteArray
)

enum class ArkivFilType  {
    PDFA,
    XML,
    JSON
}

enum class VariantFormat  {
    ORIGINAL,
    ARKIV
}

internal class ArkivSak(
    val arkivSakSystem: String,
    val arkivSakId: String
)