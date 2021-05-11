package no.nav.arbeidsgiver.notifikasjon

import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import no.nav.arbeidsgiver.notifikasjon.infrastruktur.*
import org.apache.kafka.clients.producer.Producer
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource

object BrukerAPI {
    private val log = logger()

    data class Context(
        val fnr: String,
        val token: String
    )

    sealed class Notifikasjon {
        data class Beskjed(
            val merkelapp: String,
            val tekst: String,
            val lenke: String,
            val opprettetTidspunkt: OffsetDateTime,
            val id: String
        ) : Notifikasjon()
    }

    data class NotifikasjonKlikketPaaResultat(
        val errors: List<Nothing>
    )

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
    sealed class MutationError {
        abstract val feilmelding: String

        data class UgyldigId(
            override val feilmelding: String
        ) : MutationError()
    }

    fun createBrukerGraphQL(
        altinn: Altinn,
        dataSourceAsync: CompletableFuture<DataSource>,
        kafkaProducer: Producer<KafkaKey, Hendelse>
    ) = TypedGraphQL<Context>(
        createGraphQL("/bruker.graphqls") {
            scalar(Scalars.ISO8601DateTime)

            subtypes<Notifikasjon>("Notifikasjon") {
                when (it) {
                    is Notifikasjon.Beskjed -> "Beskjed"
                }
            }

            subtypes<MutationError>("MutationError") {
                when (it) {
                    is MutationError.UgyldigId -> "UgyldigId"
                }
            }

            wire("Query") {
                dataFetcher("ping") {
                    "pong"
                }

                dataFetcher("notifikasjoner") {
                    val tilganger = altinn.hentAlleTilganger(
                        it.getContext<Context>().fnr,
                        it.getContext<Context>().token
                    )
                    // TODO: er det riktig med GlobalScope her eller finnes en bedre måte?
                    GlobalScope.future(brukerGraphQLDispatcher) {
                        QueryModel.hentNotifikasjoner(
                            dataSourceAsync.await(),
                            it.getContext<Context>().fnr,
                            tilganger
                        ).map { queryBeskjed ->
                            Notifikasjon.Beskjed(
                                merkelapp = queryBeskjed.merkelapp,
                                tekst = queryBeskjed.tekst,
                                lenke = queryBeskjed.lenke,
                                opprettetTidspunkt = queryBeskjed.opprettetTidspunkt,
                                id = queryBeskjed.id
                            )
                        }
                    }
                }

                dataFetcher("whoami") {
                    it.getContext<Context>().fnr
                }
            }

            wire("Mutation") {
                dataFetcher("notifikasjonKlikketPaa") {
                    val id = it.getTypedArgument<String>("id")

                    /* do something */

                    NotifikasjonKlikketPaaResultat(
                        errors = listOf()
                    )
                }
            }
        }
    )
}