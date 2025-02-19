package no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse

import no.nav.arbeidsgiver.notifikasjon.hendelse.HendelseModel
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.local_database.*
import no.nav.arbeidsgiver.notifikasjon.skedulert_påminnelse.Oppgavetilstand.*
import java.sql.Connection
import java.time.Instant
import java.time.LocalDate
import java.util.*

private typealias OppgaveId = UUID
private typealias BestillingHendelseId = UUID

enum class Oppgavetilstand {
    OPPGAVE_AKTIV, OPPGAVE_UTFØRT, OPPGAVE_SLETTET
}

class SkedulertPåminnelseRepository : AutoCloseable {
    data class SkedulertPåminnelse(
        val oppgaveId: OppgaveId,
        val fristOpprettetTidspunkt: Instant,
        val frist: LocalDate?,
        val tidspunkt: HendelseModel.PåminnelseTidspunkt,
        val eksterneVarsler: List<HendelseModel.EksterntVarsel>,
        val virksomhetsnummer: String,
        val produsentId: String,
        val bestillingHendelseId: BestillingHendelseId,
    )

    private val database = EphemeralDatabase("skedulert_paaminnelse",
        """
        create table oppgaver (
            oppgave_id text not null primary key,
            tilstand text not null,
            merkelapp text not null,
            grupperingsid text
        );
        
        create index oppgaver_koordinat_idx on oppgaver(merkelapp, grupperingsid);
        
        create table slettede_saker(
            merkelapp text not null,
            grupperingsid text not null,
            primary key (merkelapp, grupperingsid)
        );
        
        -- utførte (inkludert avbrutte) bestillinger
        create table utforte_bestillinger(
            bestilling_id text not null primary key
        );
        
        create table bestillinger(
            bestilling_id text not null primary key,
            paaminnelsestidspunkt text not null,
            oppgave_id text references oppgaver(oppgave_id),
            frist_opprettet_tidspunkt text not null,
            frist text null,
            tidspunkt_json text not null,
            eksterne_varsler_json text not null,
            virksomhetsnummer text not null,
            produsent_id text not null
        );
        
        create index bestillinger_utsendelse_idx on bestillinger(paaminnelsestidspunkt);
        create index bestillinger_oppgave_id_idx on bestillinger(oppgave_id); 
        """.trimIndent()
    )

    override fun close() = database.close()

    fun hentOgFjernAlleAktuellePåminnelser(now: Instant): Collection<SkedulertPåminnelse> {
        return database.useTransaction {
            val bestillinger = executeQuery(
                """
                    delete from bestillinger
                    where 
                        paaminnelsestidspunkt <= ?
                        and oppgave_id in (
                            select oppgave_id
                            from oppgaver
                            left join slettede_saker on (
                                oppgaver.merkelapp = slettede_saker.merkelapp
                                and oppgaver.grupperingsid = slettede_saker.grupperingsid
                            )
                            where slettede_saker.grupperingsid is null and tilstand = '$OPPGAVE_AKTIV' 
                        )
                    returning *
                """.trimIndent(),
                setup = {
                    setInstant(now)
                },
                result = {
                    val resultat = mutableListOf<SkedulertPåminnelse>()
                    while (next()) {
                        resultat.add(
                            SkedulertPåminnelse(
                                oppgaveId = getUUID("oppgave_id"),
                                fristOpprettetTidspunkt = getInstant("frist_opprettet_tidspunkt"),
                                frist = getLocalDateOrNull("frist"),
                                tidspunkt = getJson("tidspunkt_json"),
                                eksterneVarsler = getJson("eksterne_varsler_json"),
                                virksomhetsnummer = getString("virksomhetsnummer"),
                                produsentId = getString("produsent_id"),
                                bestillingHendelseId = getUUID("bestilling_id"),
                            )
                        )
                    }
                    resultat
                }
            )

            executeBatch(
                """
                    insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
                """.trimIndent(),
                bestillinger,
            ) {
                setUUID(it.bestillingHendelseId)
            }

            return@useTransaction bestillinger
        }
    }

    fun processHendelse(hendelse: HendelseModel.Hendelse) {
        when (hendelse) {
            is HendelseModel.OppgaveOpprettet -> database.useTransaction {
                oppgaveOpprettet(hendelse)

                bestillPåminnelse(
                    hendelse = hendelse,
                    påminnelse = hendelse.påminnelse,
                    fristOpprettetTidspunkt = hendelse.opprettetTidspunkt.toInstant(),
                    frist = hendelse.frist,
                )
            }

            is HendelseModel.FristUtsatt -> database.useTransaction {
                markerPåminnelserLukket(oppgaveId = hendelse.notifikasjonId)
                when (hentOppgavetilstand(hendelse.notifikasjonId)) {
                    OPPGAVE_AKTIV -> {
                        bestillPåminnelse(
                            hendelse = hendelse,
                            påminnelse = hendelse.påminnelse,
                            frist = hendelse.frist,
                            fristOpprettetTidspunkt = hendelse.fristEndretTidspunkt,
                        )
                    }

                    OPPGAVE_UTFØRT,
                    OPPGAVE_SLETTET,
                    null -> {
                        /* noop */
                    }
                }
            }

            is HendelseModel.PåminnelseOpprettet -> database.useTransaction {
                markerBestillingLukket(bestillingId = hendelse.bestillingHendelseId)
            }

            is HendelseModel.OppgaveUtført -> database.useTransaction {
                settOppgavetilstand(hendelse.notifikasjonId, OPPGAVE_UTFØRT)
            }

            is HendelseModel.OppgaveUtgått -> database.useTransaction {
                markerPåminnelserLukket(oppgaveId = hendelse.notifikasjonId)
            }

            is HendelseModel.SoftDelete -> database.useTransaction {
                processDelete(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.HardDelete -> database.useTransaction {
                processDelete(
                    aggregateId = hendelse.aggregateId,
                    merkelapp = hendelse.merkelapp,
                    grupperingsid = hendelse.grupperingsid,
                )
            }

            is HendelseModel.BeskjedOpprettet,
            is HendelseModel.BrukerKlikket,
            is HendelseModel.SakOpprettet,
            is HendelseModel.NyStatusSak,
            is HendelseModel.EksterntVarselFeilet,
            is HendelseModel.EksterntVarselVellykket -> Unit
        }
    }

    private fun Connection.processDelete(
        aggregateId: UUID,
        merkelapp: String?,
        grupperingsid: String?,
    ) {
        if (merkelapp != null && grupperingsid != null) {
            processSakSlettet(merkelapp, grupperingsid)
        } else {
            settOppgavetilstand(aggregateId, OPPGAVE_SLETTET)
        }
    }

    private fun Connection.markerPåminnelserLukket(oppgaveId: UUID) {
        val slettedeBestillingsIder = executeQuery(
            """
                delete from bestillinger
                where oppgave_id = ?
                returning bestilling_id
            """.trimIndent(),
            setup = {
                setUUID(oppgaveId)
            },
            result = {
                resultAsList {
                    getUUID("bestilling_id")
                }
            }
        )

        executeBatch(
            """
                insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
            """.trimIndent(),
            slettedeBestillingsIder,
        ) {
            setUUID(it)
        }
    }

    private fun Connection.markerBestillingLukket(bestillingId: UUID) {
        executeUpdate(
            """
                delete from bestillinger
                where bestilling_id = ?
            """.trimIndent()
        ) {
            setUUID(bestillingId)
        }

        executeUpdate(
            """
                insert into utforte_bestillinger(bestilling_id) values (?) on conflict do nothing
            """.trimIndent(),
        ) {
            setUUID(bestillingId)
        }
    }

    private fun Connection.processSakSlettet(merkelapp: String, grupperingsid: String) {
        executeUpdate(
            """
                update oppgaver
                set tilstand = ?
                where merkelapp = ? and grupperingsid = ?
            """.trimIndent()
        ) {
            setEnum(OPPGAVE_SLETTET)
            setText(merkelapp)
            setText(grupperingsid)
        }

        executeUpdate(
            """
                insert into slettede_saker(merkelapp, grupperingsid)
                values (?, ?)
                on conflict do nothing
            """.trimIndent()
        ) {
            setText(merkelapp)
            setText(grupperingsid)
        }
    }

    private fun Connection.oppgaveOpprettet(oppgaveOpprettet: HendelseModel.OppgaveOpprettet) {
        executeUpdate(
            """
                insert into oppgaver (oppgave_id, merkelapp, tilstand, grupperingsid)
                values (?, ?, ?, ?)
                on conflict (oppgave_id) do nothing
            """.trimIndent()
        ) {
            setUUID(oppgaveOpprettet.aggregateId)
            setText(oppgaveOpprettet.merkelapp)
            setEnum(OPPGAVE_AKTIV)
            setTextOrNull(oppgaveOpprettet.grupperingsid)
        }
    }

    private fun Connection.settOppgavetilstand(oppgaveId: OppgaveId, tilstand: Oppgavetilstand) {
        executeUpdate(
            """
                update oppgaver
                set tilstand = case when tilstand = ? then tilstand else ? end
                where oppgave_id = ?
            """.trimIndent()
        ) {
            setEnum(OPPGAVE_SLETTET)
            setEnum(tilstand)
            setUUID(oppgaveId)
        }
    }

    private fun Connection.hentOppgavetilstand(oppgaveId: UUID): Oppgavetilstand? {
        return executeQuery(
            """
            select tilstand from oppgaver where oppgave_id = ?
            """.trimIndent(),
            setup = {
                setUUID(oppgaveId)
            },
            result = {
                if (next()) getEnum<Oppgavetilstand>("tilstand") else null
            }
        )
    }

    private fun Connection.bestillPåminnelse(
        hendelse: HendelseModel.Hendelse,
        påminnelse: HendelseModel.Påminnelse?,
        fristOpprettetTidspunkt: Instant,
        frist: LocalDate?,
    ) {
        if (påminnelse == null) {
            return
        }

        executeUpdate(
            """
            insert into bestillinger (
                oppgave_id,
                frist_opprettet_tidspunkt,
                frist,
                tidspunkt_json,
                eksterne_varsler_json,
                virksomhetsnummer,
                produsent_id,
                bestilling_id,
                paaminnelsestidspunkt
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (bestilling_id) do nothing
            """.trimIndent()
        ) {
            setUUID(hendelse.aggregateId)
            setInstant(fristOpprettetTidspunkt)
            setLocalDateOrNull(frist)
            setJson(påminnelse.tidspunkt)
            setJson(påminnelse.eksterneVarsler)
            setText(hendelse.virksomhetsnummer)
            setTextOrNull(hendelse.produsentId)
            setUUID(hendelse.hendelseId)
            setInstant(påminnelse.tidspunkt.påminnelseTidspunkt)
        }
    }
}

