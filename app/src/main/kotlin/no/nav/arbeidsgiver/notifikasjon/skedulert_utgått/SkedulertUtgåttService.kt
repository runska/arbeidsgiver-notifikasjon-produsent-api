package no.nav.arbeidsgiver.notifikasjon.skedulert_utgått

import kotlinx.coroutines.time.delay
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseProdusent
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.NaisEnvironment
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.kafka.PartitionProcessor
import no.nav.arbeidsgiver.notifikasjon.tid.OsloTid
import no.nav.arbeidsgiver.notifikasjon.tid.atOslo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*


class SkedulertUtgåttService(
    private val hendelseProdusent: HendelseProdusent
) : PartitionProcessor {
    private val skedulerteUtgåttRepository = SkedulertUtgåttRepository()

    override suspend fun processHendelse(hendelse: HendelseModel.Hendelse) {
        skedulerteUtgåttRepository.processHendelse(hendelse)
    }

    override suspend fun processingLoopStep() {
        sendVedUtgåttFrist()
        delay(Duration.ofSeconds(1))
    }


    suspend fun sendVedUtgåttFrist(now: LocalDate = OsloTid.localDateNow()) {
        val utgåttFrist = skedulerteUtgåttRepository.hentOgFjernAlleMedFrist(now)
        utgåttFrist.forEach { utgått ->
            val fristLocalDateTime = LocalDateTime.of(utgått.frist, LocalTime.MAX)
            hendelseProdusent.send(HendelseModel.OppgaveUtgått(
                virksomhetsnummer = utgått.virksomhetsnummer,
                notifikasjonId = utgått.oppgaveId,
                hendelseId = UUID.randomUUID(),
                produsentId = utgått.produsentId,
                kildeAppNavn = NaisEnvironment.clientId,
                hardDelete = null,
                utgaattTidspunkt = fristLocalDateTime.atOslo().toOffsetDateTime(),
                nyLenke = null,
            ))
        }
    }

    override fun close() {
    }
}