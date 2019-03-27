package no.nav.helse.journalforing.v1

import no.nav.helse.CorrelationId
import no.nav.helse.dokument.Dokument
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dusseldorf.ktor.core.ParameterType
import no.nav.helse.dusseldorf.ktor.core.Throwblem
import no.nav.helse.dusseldorf.ktor.core.ValidationProblemDetails
import no.nav.helse.dusseldorf.ktor.core.Violation
import no.nav.helse.journalforing.*
import no.nav.helse.journalforing.gateway.JournalforingGateway
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.JournalforingV1Service")

private val OMSORG_TEMA = Tema("OMS")
private val NAV_NO_KANAL = Kanal("NAV_NO")
private val PLEIEPENGER_SOKNAD_BREV_KODE = BrevKode(brevKode = "NAV 09-11.05", dokumentKategori = "SOK")
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
        val alleDokumenter = mutableListOf<List<Dokument>>()
        melding.dokumenter.forEach {
            alleDokumenter.add(
                dokumentService.hentDokumenter(
                    urls = it,
                    correlationId = correlationId,
                    aktoerId = aktoerId
                )
            )
        }


        logger.trace("Genrerer request til Joark")
        val request = JournalPostRequestV1Factory.instance(
            tittel = JOURNALFORING_TITTEL,
            mottaker = aktoerId,
            tema = OMSORG_TEMA,
            kanal = NAV_NO_KANAL,
            dokumenter = alleDokumenter.toList(),
            mottatt = melding.mottatt,
            typeReferanse = PLEIEPENGER_SOKNAD_BREV_KODE
        )

        logger.trace("Sender melding til Joark")

        val response = journalforingGateway.jorunalfor(request)

        logger.trace("JournalPost med ID ${response.journalpostId} opprettet")
        return JournalPostId(response.journalpostId)
    }

    private fun validerMelding(melding: MeldingV1) {
        val violations = mutableSetOf<Violation>()
        if (melding.dokumenter.isEmpty()) {
            violations.add(Violation(parameterName = "dokument", reason = "Det må sendes minst ett dokument", parameterType = ParameterType.ENTITY, invalidValue = melding.dokumenter))
        }

        melding.dokumenter.forEach {
            if (it.isEmpty()) {
                violations.add(Violation(parameterName = "dokument_bolk", reason = "Det må være minst et dokument i en dokument bolk.", parameterType = ParameterType.ENTITY, invalidValue = it))
            }
        }

        if (!melding.aktoerId.matches(ONLY_DIGITS)) {
            violations.add(Violation(parameterName = "aktoer_id", reason = "Ugyldig AktørID. Kan kun være siffer.", parameterType = ParameterType.ENTITY, invalidValue = melding.aktoerId))
        }
        if (violations.isNotEmpty()) {
            throw Throwblem(ValidationProblemDetails(violations))
        }
    }
}