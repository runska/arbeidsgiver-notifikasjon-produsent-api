package no.nav.arbeidsgiver.notifikasjon.produsent.api

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.core.spec.style.scopes.DescribeSpecContainerScope
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beOfType
import io.kotest.matchers.types.instanceOf
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.NærmesteLederMottaker
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel.OppgaveOpprettet
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.produsent.Produsent
import no.nav.arbeidsgiver.notifikasjon.produsent.ProdusentRepositoryImpl
import no.nav.arbeidsgiver.notifikasjon.util.*
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.time.ExperimentalTime

@ExperimentalTime
class NyOppgaveTests : DescribeSpec({
    val database = testDatabase(Produsent.databaseConfig)
    val produsentRepository = ProdusentRepositoryImpl(database)

    val kafkaProducer = mockk<HendelseProdusent>()
    coEvery { kafkaProducer.sendOgHentMetadata(any()) } returns HendelseModel.HendelseMetadata(Instant.parse("1970-01-01T00:00:00Z"))

    val engine = ktorProdusentTestServer(
        kafkaProducer = kafkaProducer,
        produsentRepository = produsentRepository,
    )

    describe("produsent-api happy path") {
        val nyOppgave = opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(engine)

        it("sends message to kafka") {
            coVerify {
                kafkaProducer.sendOgHentMetadata(withArg { oppgaveOpprettet: OppgaveOpprettet ->
                    oppgaveOpprettet.notifikasjonId shouldBe nyOppgave.id
                    oppgaveOpprettet.lenke shouldBe "https://foo.bar"
                    oppgaveOpprettet.tekst shouldBe "hello world"
                    oppgaveOpprettet.merkelapp shouldBe "tag"
                    oppgaveOpprettet.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    oppgaveOpprettet.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                    oppgaveOpprettet.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                    oppgaveOpprettet.frist shouldBe null
                })
            }
        }

        it("updates produsent modell") {
            val id = nyOppgave.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }

        val nyOppgave2 = opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(engine)
        it("idempotent oppførsel ved opprettelse av identisk sak") {
            nyOppgave2.id shouldBe nyOppgave.id
        }
    }

    describe("produsent-api happy path med frist") {
        val nyOppgave = opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(engine, frist = """frist: "2020-01-02"  """)

        it("sends message to kafka") {
            coVerify {
                kafkaProducer.sendOgHentMetadata(withArg { oppgaveOpprettet: OppgaveOpprettet ->
                    oppgaveOpprettet.notifikasjonId shouldBe nyOppgave.id
                    oppgaveOpprettet.lenke shouldBe "https://foo.bar"
                    oppgaveOpprettet.tekst shouldBe "hello world"
                    oppgaveOpprettet.merkelapp shouldBe "tag"
                    oppgaveOpprettet.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    oppgaveOpprettet.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                    oppgaveOpprettet.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                    oppgaveOpprettet.frist shouldBe LocalDate.parse("2020-01-02")
                })
            }
        }

        it("updates produsent modell") {
            val id = nyOppgave.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }

        val nyOppgave2 = opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(engine, frist = """frist: "2020-01-02"  """)
        it("idempotent oppførsel ved opprettelse av identisk sak") {
            nyOppgave2.id shouldBe nyOppgave.id
        }

        opprettOgTestNyOppgave<Error.DuplikatEksternIdOgMerkelapp>(engine, frist = """frist: "2020-01-01"  """)
    }

    describe("produsent-api happy path med grupperingsid for sak") {
        val sakOpprettet = HendelseModel.SakOpprettet(
            virksomhetsnummer = "1",
            merkelapp = "tag",
            grupperingsid = "g42",
            mottakere = listOf(
                NærmesteLederMottaker(
                    naermesteLederFnr = "12345678910",
                    ansattFnr = "321",
                    virksomhetsnummer = "42"
                )
            ),
            hendelseId = uuid("11"),
            sakId = uuid("11"),
            tittel = "test",
            lenke = "https://nav.no",
            oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
            mottattTidspunkt = OffsetDateTime.parse("2020-01-01T01:01Z"),
            kildeAppNavn = "",
            produsentId = "",
            hardDelete = null,
        ).also {
            produsentRepository.oppdaterModellEtterHendelse(it)
        }
        val nyOppgave = opprettOgTestNyOppgave<MutationNyOppgave.NyOppgaveVellykket>(
            engine,
            grupperingsid = """grupperingsid: "g42"  """
        )

        it("sends message to kafka") {
            coVerify {
                kafkaProducer.sendOgHentMetadata(withArg { oppgaveOpprettet: OppgaveOpprettet ->
                    oppgaveOpprettet.notifikasjonId shouldBe nyOppgave.id
                    oppgaveOpprettet.lenke shouldBe "https://foo.bar"
                    oppgaveOpprettet.tekst shouldBe "hello world"
                    oppgaveOpprettet.grupperingsid shouldBe sakOpprettet.grupperingsid
                    oppgaveOpprettet.sakId shouldBe sakOpprettet.sakId
                    oppgaveOpprettet.merkelapp shouldBe "tag"
                    oppgaveOpprettet.mottakere.single() shouldBe NærmesteLederMottaker(
                        naermesteLederFnr = "12345678910",
                        ansattFnr = "321",
                        virksomhetsnummer = "42"
                    )
                    oppgaveOpprettet.opprettetTidspunkt shouldBe OffsetDateTime.parse("2019-10-12T07:20:50.52Z")
                    oppgaveOpprettet.hardDelete shouldBe instanceOf(HendelseModel.LocalDateTimeOrDuration.LocalDateTime::class)
                })
            }
        }

        it("updates produsent modell") {
            val id = nyOppgave.id
            produsentRepository.hentNotifikasjon(id) shouldNot beNull()
        }
    }
})

private suspend inline fun <reified T: MutationNyOppgave.NyOppgaveResultat> DescribeSpecContainerScope.opprettOgTestNyOppgave(
    engine: TestApplicationEngine,
    frist: String = "",
    grupperingsid: String = "",
): T {
    val response = engine.produsentApi(
        """
        mutation {
            nyOppgave(nyOppgave: {
                mottaker: {
                    naermesteLeder: {
                        naermesteLederFnr: "12345678910",
                        ansattFnr: "321"
                    } 
                }
                notifikasjon: {
                    lenke: "https://foo.bar",
                    tekst: "hello world",
                    merkelapp: "tag",
                }
                metadata: {
                    eksternId: "heu",
                    opprettetTidspunkt: "2019-10-12T07:20:50.52Z"
                    virksomhetsnummer: "42"
                    hardDelete: {
                      den: "2019-10-13T07:20:50.52"
                    }
                    $grupperingsid
                }
                $frist
            }) {
                __typename
                ... on NyOppgaveVellykket {
                    id
                    eksterneVarsler {
                        id
                    }
                }
                ... on Error {
                    feilmelding
                }
            }
        }
    """.trimIndent()
    )
    it("status is 200 OK") {
        response.status() shouldBe HttpStatusCode.OK
    }

    it("response inneholder ikke feil") {
        response.getGraphqlErrors() should beEmpty()
    }

    lateinit var nyOppgave: MutationNyOppgave.NyOppgaveResultat
    it("respons inneholder forventet data") {
        nyOppgave = response.getTypedContent<MutationNyOppgave.NyOppgaveResultat>("nyOppgave")
        nyOppgave should beOfType<T>()
    }
    return nyOppgave as T
}

