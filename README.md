# pleiepenger-joark

[![CircleCI](https://circleci.com/gh/navikt/pleiepenger-joark/tree/master.svg?style=svg)](https://circleci.com/gh/navikt/pleiepenger-joark/tree/master)

Inneholder integrasjon mot joark for å opprette jornalpost i forbindelse med søknad om Pleiepenger.

## Versjon 1
### Meldingsformat
- aktoer_id : AtkørID for personen dokumentene skal journalføres på
- mottatt : tidspunkt for når dokumentene er mottatt på ISO8601 format
- dokumenter : En liste med lister av URL'er til dokumenter som skal journalføres som må peke på "pleiepenger-dokument"
- dokumenter : Må inneholde minst en liste, og hvert liste må inneholde minst en entry.
- dokumenter[0] : Vil bli "Hoveddokument" i Joark
- En liste med URL'er skal være > 1 om man ønsker å journalføre samme dokument på forskjellige format. For eksempel PDF & JSON

```json
{
	"aktoer_id": "123561458",
	"mottatt": "2018-12-18T20:43:32Z",
	"dokumenter": [
		[
			"https://pleiepenger-dokument.nav.no/dokument/c049520b-eed9-42d0-8d48-b7c8e6e1467e",
			"https://pleiepenger-dokument.nav.no/dokument/c049520b-eed9-42d0-8d48-b7c8e6e1467f"
		],
		[
			"https://pleiepenger-dokument.nav.no/dokument/c049520b-eed9-42d0-8d48-b7c8e6e1467g"
		]
	]
}
```

### Metadata
#### Correlation ID vs Request ID
Correlation ID blir propagert videre, og har ikke nødvendigvis sitt opphav hos konsumenten
Request ID blir ikke propagert videre, og skal ha sitt opphav hos konsumenten

#### REST API
- Correlation ID må sendes som header 'X-Correlation-ID'
- Request ID kan sendes som heder 'X-Request-ID'
- Versjon på meldingen avledes fra pathen '/v1/journalforing' -> 1

## Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

Interne henvendelser kan sendes via Slack i kanalen #team-düsseldorf.