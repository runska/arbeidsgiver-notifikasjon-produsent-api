package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.kafka_reaper.typeNavn
import no.nav.arbeidsgiver.notifikasjon.util.EksempelHendelse
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase

class BrukerModelIdempotensTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    describe("BrukerModel Idempotent oppførsel") {
        withData(EksempelHendelse.Alle) { hendelse ->
            brukerRepository.oppdaterModellEtterHendelse(hendelse)
            brukerRepository.oppdaterModellEtterHendelse(hendelse)
        }
        context("NyBeskjed to ganger") {
            brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)
            brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.BeskjedOpprettet)

            it("ingen duplikat mottaker") {
                val antallMottakere = database.nonTransactionalExecuteQuery("""
                select * from mottaker_altinn_enkeltrettighet
                where notifikasjon_id = '${EksempelHendelse.BeskjedOpprettet.notifikasjonId}'
            """
                ) {
                }.size
                antallMottakere shouldBe 1
            }
        }

    }

    describe("Håndterer partial replay hvor midt i hendelsesforløp etter harddelete") {
        EksempelHendelse.Alle.forEachIndexed { i, hendelse ->
            context("$i - ${hendelse.typeNavn}") {
                brukerRepository.oppdaterModellEtterHendelse(EksempelHendelse.HardDelete.copy(
                    virksomhetsnummer = hendelse.virksomhetsnummer,
                    aggregateId = hendelse.aggregateId,
                ))
                brukerRepository.oppdaterModellEtterHendelse(hendelse)
            }
        }
    }
})