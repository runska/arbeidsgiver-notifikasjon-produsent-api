package no.nav.arbeidsgiver.notifikasjon.bruker

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import no.nav.arbeidsgiver.notifikasjon.produsent.api.IdempotenceKey
import no.nav.arbeidsgiver.notifikasjon.util.AltinnStub
import no.nav.arbeidsgiver.notifikasjon.util.getTypedContent
import no.nav.arbeidsgiver.notifikasjon.util.ktorBrukerTestServer
import no.nav.arbeidsgiver.notifikasjon.util.testDatabase
import java.time.OffsetDateTime

class NyLenkeTilSakTests : DescribeSpec({
    val database = testDatabase(Bruker.databaseConfig)
    val brukerRepository = BrukerRepositoryImpl(database)

    val engine = ktorBrukerTestServer(
        brukerRepository = brukerRepository,
        altinn = AltinnStub { _, _ ->
            BrukerModel.Tilganger(listOf(TEST_TILGANG_1))
        }
    )

    fun hentLenke() = engine.querySakerJson(
        virksomhetsnummer = TEST_VIRKSOMHET_1,
        offset = 0,
        limit = 1,
    ).getTypedContent<String>("saker/saker/0/lenke")

    fun hentSisteStatus() = engine.querySakerJson(
        virksomhetsnummer = TEST_VIRKSOMHET_1,
        offset = 0,
        limit = 1,
    ).getTypedContent<BrukerAPI.SakStatus>("saker/saker/0/sisteStatus")


    describe("endring av lenke i sak") {
        val sakOpprettet = brukerRepository.sakOpprettet(
            lenke = "#foo",
        )

        brukerRepository.nyStatusSak(
            sak = sakOpprettet,
            idempotensKey = IdempotenceKey.initial(),
        )

        it("Får opprinnelig lenke") {
            hentLenke() shouldBe "#foo"
        }

        brukerRepository.nyStatusSak(
            sak = sakOpprettet,
            idempotensKey = IdempotenceKey.userSupplied("20202021"),
            nyLenkeTilSak = "#bar",
            oppgittTidspunkt = OffsetDateTime.parse("2020-01-01T12:00:00Z"),
        )

        it("Får ny lenke ") {
            hentLenke() shouldBe "#bar"
        }

        it("Får ny status tidspunkt") {
            hentSisteStatus().tidspunkt shouldBe OffsetDateTime.parse("2020-01-01T12:00:00Z")
        }

        brukerRepository.nyStatusSak(
            sak = sakOpprettet,
            idempotensKey = IdempotenceKey.userSupplied("123"),
            nyLenkeTilSak = null,
        )

        it("status-oppdatering uten endret lenke") {
            hentLenke() shouldBe "#bar"
        }
    }
})