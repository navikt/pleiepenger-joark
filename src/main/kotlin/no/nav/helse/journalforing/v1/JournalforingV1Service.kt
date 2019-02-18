package no.nav.helse.journalforing.v1

import io.ktor.http.ContentType
import no.nav.helse.journalforing.*
import no.nav.helse.journalforing.gateway.JournalforingGateway
import no.nav.helse.validering.Brudd
import no.nav.helse.validering.Valideringsfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.JournalforingV1Service")

private val OMSORG_TEMA = Tema("OMS")
private val NAV_NO_KANAL = Kanal("NAV_NO")
private val PLEIEPENGER_SOKNAD_BREV_KODE = BrevKode(brevKode = "NAV 09-11.05", dokumentKategori = "SOK")
private val GOSYS_FAGSYSTEM = FagSystem("GOSYS","FS22")
private val JOURNALFORING_TITTEL = "Søknad om pleiepenger – sykt barn - NAV 09-11.05"

private val SUPPORTERTE_CONTENT_TYPES = listOf(ContentType("application","pdf"))
private val ONLY_DIGITS = Regex("\\d+")

class JournalforingV1Service(
    private val journalforingGateway : JournalforingGateway
) {
    suspend fun journalfor(
        melding: MeldingV1,
        metaData: MetadataV1) : JournalPostId {

        logger.info(metaData.toString())
        validerMelding(melding)

        val request = JournalPostRequestV1Factory.instance(
            tittel = JOURNALFORING_TITTEL,
            mottaker = AktoerId(melding.aktoerId),
            tema = OMSORG_TEMA,
            kanal = NAV_NO_KANAL,
            sakId = SakId(melding.sakId),
            fagSystem = GOSYS_FAGSYSTEM,
            dokumenter = melding.dokumenter,
            mottatt = melding.mottatt,
            typeReferanse = PLEIEPENGER_SOKNAD_BREV_KODE
        )

        val response = journalforingGateway.jorunalfor(request)

        return JournalPostId(response.journalpostId)
    }

    private fun validerMelding(melding: MeldingV1) {
        val brudd = mutableListOf<Brudd>()
        if (melding.dokumenter.isEmpty()) {
            brudd.add(Brudd(parameter = "dokumenter", error = "Det må sendes minst ett dokument"))
        }
        melding.dokumenter.forEach { dokument ->
            if (!SUPPORTERTE_CONTENT_TYPES.contains(dokument.contentTypeObject)) {
                brudd.add(Brudd(parameter = "dokument", error = "Content-Type '${dokument.contentType}' støttes ikke."))
            }
        }
        if (!melding.aktoerId.matches(ONLY_DIGITS)) {
            brudd.add(Brudd("aktoer_id", error = "${melding.aktoerId} er ikke en gyldig AktørID. Kan kun være siffer."))
        }
        if (brudd.isNotEmpty()) {
            throw Valideringsfeil(brudd)
        }
    }
}