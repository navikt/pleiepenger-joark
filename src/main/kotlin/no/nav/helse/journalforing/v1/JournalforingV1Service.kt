package no.nav.helse.journalforing.v1

import no.nav.helse.CorrelationId
import no.nav.helse.dokument.DokumentService
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

private val ONLY_DIGITS = Regex("\\d+")

class JournalforingV1Service(
    private val journalforingGateway : JournalforingGateway,
    private val dokumentService: DokumentService
) {
    suspend fun journalfor(
        melding: MeldingV1,
        metaData: MetadataV1) : JournalPostId {

        val correlationId = CorrelationId(metaData.correlationId)

        logger.info(metaData.toString())
        validerMelding(melding)

        val aktoerId = AktoerId(melding.aktoerId)
        logger.trace("Journalfører for AktørID $aktoerId")

        logger.trace("Henter dokumenter")
        val dokumenter = dokumentService.hentDokumenter(
            urls = melding.dokumenter,
            correlationId = correlationId
        )

        logger.trace("Genrerer request til Joark")
        val request = JournalPostRequestV1Factory.instance(
            tittel = JOURNALFORING_TITTEL,
            mottaker = aktoerId,
            tema = OMSORG_TEMA,
            kanal = NAV_NO_KANAL,
            sakId = SakId(melding.sakId),
            fagSystem = GOSYS_FAGSYSTEM,
            dokumenter = dokumenter,
            mottatt = melding.mottatt,
            typeReferanse = PLEIEPENGER_SOKNAD_BREV_KODE
        )

        logger.trace("Sender melding til Joark")

        val response = journalforingGateway.jorunalfor(request)

        logger.trace("JournalPost med ID ${response.journalpostId} opprettet")
        return JournalPostId(response.journalpostId)
    }

    private fun validerMelding(melding: MeldingV1) {
        val brudd = mutableListOf<Brudd>()
        if (melding.dokumenter.isEmpty()) {
            brudd.add(Brudd(parameter = "dokument", error = "Det må sendes minst ett dokument"))
        }
        if (!melding.aktoerId.matches(ONLY_DIGITS)) {
            brudd.add(Brudd("aktoer_id", error = "${melding.aktoerId} er ikke en gyldig AktørID. Kan kun være siffer."))
        }
        if (brudd.isNotEmpty()) {
            throw Valideringsfeil(brudd)
        }
    }
}