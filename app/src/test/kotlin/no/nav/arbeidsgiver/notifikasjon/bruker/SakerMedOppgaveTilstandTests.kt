package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.Notifikasjon.Oppgave.Tilstand.NY
import no.nav.arbeidsgiver.notifikasjon.bruker.BrukerAPI.SakSortering.FRIST
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

class SakerMedOppgaveTilstandTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val queryModel = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = queryModel,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(
                listOf(
                    BrukerModel.Tilgang.Altinn(
                        virksomhet = "1",
                        servicecode = "1",
                        serviceedition = "1",
                    )
                )
            )
        }
    )

    suspend fun opprettOppgave(
        grupperingsid: String,
        frist: LocalDate?,
    ): UUID? {
        val oppgaveId = UUID.randomUUID()
        HendelseModel.OppgaveOpprettet(
            hendelseId = oppgaveId,
            notifikasjonId = oppgaveId,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            grupperingsid = grupperingsid,
            eksternId = "1",
            eksterneVarsler = listOf(),
            opprettetTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            merkelapp = "tag",
            tekst = "tjohei",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            lenke = "#foo",
            hardDelete = null,
            frist = frist,
            påminnelse = null,
        ).also { queryModel.oppdaterModellEtterHendelse(it) }
        return oppgaveId
    }

    fun opprettStatus(id: UUID) = HendelseModel.NyStatusSak(
        hendelseId = UUID.randomUUID(),
        virksomhetsnummer = "1",
        produsentId = "1",
        kildeAppNavn = "1",
        sakId = id,
        status = HendelseModel.SakStatus.MOTTATT,
        overstyrStatustekstMed = null,
        oppgittTidspunkt = null,
        mottattTidspunkt = OffsetDateTime.now(),
        idempotensKey = IdempotenceKey.initial(),
        hardDelete = null,
        nyLenkeTilSak = null,

        )

    suspend fun opprettSak(
        id: String,
    ): String {
        val uuid = uuid(id)
        val sakOpprettet = HendelseModel.SakOpprettet(
            hendelseId = uuid,
            virksomhetsnummer = "1",
            produsentId = "1",
            kildeAppNavn = "1",
            sakId = uuid,
            grupperingsid = uuid.toString(),
            merkelapp = "tag",
            mottakere = listOf(
                HendelseModel.AltinnMottaker(
                    virksomhetsnummer = "1",
                    serviceCode = "1",
                    serviceEdition = "1"
                )
            ),
            tittel = "tjohei",
            lenke = "#foo",
            oppgittTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            mottattTidspunkt = OffsetDateTime.parse("2017-12-03T10:15:30+01:00"),
            hardDelete = null,
        )
        queryModel.oppdaterModellEtterHendelse(sakOpprettet)
        queryModel.oppdaterModellEtterHendelse(opprettStatus(uuid))

        return uuid.toString()
    }

    suspend fun oppgaveTilstandUtført(id: UUID) {
        val hendelse = HendelseModel.OppgaveUtført(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = UUID.randomUUID(),
            produsentId = "1",
            kildeAppNavn = "1",
            hardDelete = null,
            nyLenke = null,
            utfoertTidspunkt = OffsetDateTime.parse("2023-01-05T00:00:00+01")
        )
        queryModel.oppdaterModellEtterHendelse(hendelse)
    }

    suspend fun oppgaveTilstandUtgått(id: UUID) {
        val hendelse = HendelseModel.OppgaveUtgått(
            virksomhetsnummer = "1",
            notifikasjonId = id,
            hendelseId = UUID.randomUUID(),
            produsentId = "1",
            kildeAppNavn = "1",
            hardDelete = null,
            utgaattTidspunkt = OffsetDateTime.now(),
            nyLenke = null,
        )
        queryModel.oppdaterModellEtterHendelse(hendelse)
    }

    describe("Sak med oppgave med frist og påminnelse") {
        val sak = opprettSak("1")
        opprettOppgave(sak, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtført(it!!) }
        opprettOppgave(sak, LocalDate.parse("2023-05-15"))
        opprettOppgave(sak, LocalDate.parse("2023-05-15"))
        opprettOppgave(sak, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtgått(it!!) }

        val sak2 = opprettSak("2")
        opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtført(it!!) }
        opprettOppgave(sak2, LocalDate.parse("2023-05-15"))

        val sak3 = opprettSak("3")
        opprettOppgave(sak3, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtført(it!!) }

        val res = engine.querySakerJson(
            virksomhetsnummer = "1",
            limit = 10,
            sortering = FRIST,
        )

        it("Teller kun saken en gang for hver tilstand") {
            res.getTypedContent<List<Any>>("$.saker.oppgaveTilstandInfo") shouldContainExactlyInAnyOrder listOf(
                mapOf(
                    "tilstand" to "NY",
                    "antall" to 2
                ),
                mapOf(
                    "tilstand" to "UTGAATT",
                    "antall" to 1
                ),
                mapOf(
                    "tilstand" to "UTFOERT",
                    "antall" to 3
                )
            )
        }

        it("totaltAntallSaker teller saker og ikke oppgaver") {
            res.getTypedContent<Int>("$.saker.totaltAntallSaker") shouldBe 3
        }
    }

    describe("Sak med oppgave med frist med filter") {
        val sak1 = opprettSak("1")
        opprettOppgave(sak1, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtført(it!!) }
        opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
        opprettOppgave(sak1, LocalDate.parse("2023-05-15"))
        opprettOppgave(sak1, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtgått(it!!) }

        val sak2 = opprettSak("2")
        opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtført(it!!) }
        opprettOppgave(sak2, LocalDate.parse("2023-05-15")).also { oppgaveTilstandUtført(it!!) }
        opprettOppgave(sak2, LocalDate.parse("2023-05-15")).also { oppgaveTilstandUtgått(it!!) }

        opprettSak("3")

        val res =
            engine.querySakerJson(
                virksomhetsnummer = "1",
                limit = 10,
                sortering = FRIST,
                oppgaveTilstand = listOf(NY)
            ).getTypedContent<List<String>>("$.saker.saker.*.id")


        res shouldBe listOf(uuid(sak1).toString())
    }

    describe("Saker med og uten oppgaver") {
        val sak1 = opprettSak("1")
        val sak2 = opprettSak("2")
        opprettOppgave(sak2, LocalDate.parse("2023-01-15")).also { oppgaveTilstandUtført(it!!) }

        val res = engine.querySakerJson(
            virksomhetsnummer = "1",
            limit = 10,
            sortering = FRIST,
        ).getTypedContent<List<String>>("$.saker.saker.*.id")

        it("skal returnere saker med og uten oppgaver") {
            res shouldContainExactlyInAnyOrder listOf(sak1, sak2)
        }

    }
})


