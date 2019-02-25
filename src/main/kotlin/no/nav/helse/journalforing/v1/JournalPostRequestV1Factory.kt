package no.nav.helse.journalforing.v1

import no.nav.helse.dokument.Dokument
import no.nav.helse.journalforing.*
import no.nav.helse.journalforing.gateway.*
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.IllegalStateException

private const val AKTOR_ID_KEY = "aktoer"
private const val IDENT_KEY = "ident"
private const val PERSON_KEY = "person"

private const val PDF_CONTENT_TYPE = "application/pdf"
private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

object JournalPostRequestV1Factory {
    internal fun instance(
        tittel: String,
        mottaker: AktoerId,
        tema: Tema,
        kanal: Kanal,
        sakId: SakId,
        fagSystem: FagSystem,
        dokumenter: List<Dokument>,
        mottatt: ZonedDateTime,
        typeReferanse: TypeReferanse) : JournalPostRequest {

        if (dokumenter.isEmpty()) {
            throw IllegalStateException("Det må sendes minst ett dokument")
        }

        val forsendelseInformasjon = ForsendelseInformasjon(
            tittel = tittel,
            bruker = lagAktorStruktur(aktorId = mottaker),
            avsender = lagAktorStruktur(aktorId = mottaker), // I Versjon 1 er det kun innlogget bruker som laster opp vedlegg og fyller ut søknad, så bruker == avsender
            tema = tema.value,
            kanalReferanseId = "${fagSystem.kode}-${sakId.value}", // I Versjon 1 settes ID fra sak som kanalReferenseId - Om flere journalføringer blir gjort på en sak er ikke denne unik...
            forsendelseMottatt = formatDate(mottatt),
            forsendelseInnsendt = formatDate(ZonedDateTime.now()),
            mottaksKanal = kanal.value,
            arkivSak = ArkivSak(arkivSakId = sakId.value, arkivSakSystem = fagSystem.kode)
        )

        var hovedDokument : JoarkDokument? = null
        val vedlegg = mutableListOf<JoarkDokument>()

        dokumenter.forEach { dokument ->
            if (hovedDokument == null) {
                hovedDokument = mapDokument(dokument, typeReferanse)
            } else {
                vedlegg.add(mapDokument(dokument, typeReferanse))
            }
        }

        return JournalPostRequest(
            forsokEndeligJF = true,
            forsendelseInformasjon = forsendelseInformasjon,
            dokumentInfoHoveddokument = hovedDokument!!,
            dokumentInfoVedlegg = vedlegg
        )
    }

    private fun formatDate(dateTime: ZonedDateTime) : String {
        val utc = ZonedDateTime.ofInstant(dateTime.toInstant(), ZoneOffset.UTC)
        return DATE_TIME_FORMATTER.format(utc)
    }

    private fun lagAktorStruktur(aktorId: AktoerId): Map<String, Map<String, Map<String, String>>> {
        return mapOf(
            Pair(
                AKTOR_ID_KEY, mapOf(
                Pair(
                    PERSON_KEY, mapOf(
                    Pair(IDENT_KEY, aktorId.value)
                ))
            )
        ))
    }

    private fun mapDokument(dokument : Dokument, typeReferanse: TypeReferanse) : JoarkDokument {
        val arkivFilType = getArkivFilType(dokument)
        val dokumentVariant = listOf(
            DokumentVariant(
                arkivFilType = arkivFilType,
                variantFormat = getVariantFormat(
                    arkivFilType
                ),
                dokument = dokument.content
            )
        )

        when (typeReferanse) {
            is DokumentType -> {
                return JoarkDokument(
                    tittel = dokument.title,
                    dokumentTypeId = typeReferanse.value,
                    dokumentVariant = dokumentVariant
                )
            }
            is BrevKode -> {
                return JoarkDokument(
                    tittel = dokument.title,
                    brevkode = typeReferanse.brevKode,
                    dokumentkategori = typeReferanse.dokumentKategori,
                    dokumentVariant = dokumentVariant
                )
            }
            else -> throw IllegalStateException("Ikke støtttet type referense ${typeReferanse.javaClass.simpleName}")
        }


    }

    private fun getArkivFilType(dokument: Dokument) : ArkivFilType {
        if (PDF_CONTENT_TYPE == dokument.contentType) return ArkivFilType.PDFA
        throw IllegalStateException("Ikke støttet Content-Type '${dokument.contentType}'")
    }

    private fun getVariantFormat(arkivFilType: ArkivFilType) : VariantFormat {
        return if (arkivFilType.equals(ArkivFilType.PDFA)) VariantFormat.ARKIV else VariantFormat.ORIGINAL
    }
}