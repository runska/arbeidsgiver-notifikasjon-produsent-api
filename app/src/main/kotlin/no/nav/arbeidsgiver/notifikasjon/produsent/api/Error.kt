package no.nav.arbeidsgiver.notifikasjon.produsent.api

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "__typename")
internal sealed class Error {
    abstract val feilmelding: String

    sealed interface TilgangsstyringError :
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat,
        MutationNySak.NySakResultat,
        MutationHardDeleteNotifikasjon.HardDeleteNotifikasjonResultat,
        MutationSoftDeleteNotifikasjon.SoftDeleteNotifikasjonResultat,
        MutationHardDeleteSak.HardDeleteSakResultat,
        MutationSoftDeleteSak.SoftDeleteSakResultat

    @JsonTypeName("UgyldigMerkelapp")
    data class UgyldigMerkelapp(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        QueryNotifikasjoner.MineNotifikasjonerResultat,
        QueryNotifikasjoner.HentNotifikasjonResultat,
        MutationSoftDeleteSak.SoftDeleteSakResultat,
        MutationHardDeleteSak.HardDeleteSakResultat,
        MutationSoftDeleteNotifikasjon.SoftDeleteNotifikasjonResultat,
        MutationHardDeleteNotifikasjon.HardDeleteNotifikasjonResultat,
        MutationNyStatusSak.NyStatusSakResultat

    @JsonTypeName("UkjentProdusent")
    data class UkjentProdusent(
        override val feilmelding: String
    ) : Error(),
        TilgangsstyringError,
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        QueryNotifikasjoner.MineNotifikasjonerResultat,
        QueryNotifikasjoner.HentNotifikasjonResultat,
        MutationSoftDeleteSak.SoftDeleteSakResultat,
        MutationHardDeleteSak.HardDeleteSakResultat,
        MutationSoftDeleteNotifikasjon.SoftDeleteNotifikasjonResultat,
        MutationHardDeleteNotifikasjon.HardDeleteNotifikasjonResultat,
        MutationNySak.NySakResultat,
        MutationNyStatusSak.NyStatusSakResultat

    @JsonTypeName("UgyldigMottaker")
    data class UgyldigMottaker(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError

    @JsonTypeName("Konflikt")
    data class Konflikt(
        override val feilmelding: String
    ) :
        Error(),
        MutationNyStatusSak.NyStatusSakResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat

    @JsonTypeName("DuplikatEksternIdOgMerkelapp")
    data class DuplikatEksternIdOgMerkelapp(
        override val feilmelding: String,
        val idTilEksisterende: UUID
    ) : Error(),
        MutationNyBeskjed.NyBeskjedResultat,
        MutationNyOppgave.NyOppgaveResultat

    @JsonTypeName("DuplikatGrupperingsid")
    data class DuplikatGrupperingsid(
        override val feilmelding: String,
        val idTilEksisterende: UUID
    ) : Error(),
        MutationNySak.NySakResultat

    @JsonTypeName("DuplikatGrupperingsidEtterDelete")
    data class DuplikatGrupperingsidEtterDelete(
        override val feilmelding: String
    ) : Error(),
        MutationNySak.NySakResultat

    @JsonTypeName("NotifikasjonFinnesIkke")
    data class NotifikasjonFinnesIkke(
        override val feilmelding: String
    ) :
        Error(),
        MutationOppgaveUtfoert.OppgaveUtfoertResultat,
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat,
        MutationSoftDeleteNotifikasjon.SoftDeleteNotifikasjonResultat,
        MutationHardDeleteNotifikasjon.HardDeleteNotifikasjonResultat,
        QueryNotifikasjoner.HentNotifikasjonResultat

    @JsonTypeName("UkjentRolle")
    data class UkjentRolle(
        override val feilmelding: String
    ) :
        Error(),
        TilgangsstyringError,
        MutationNySak.NySakResultat

    @JsonTypeName("UgyldigPaaminnelseTidspunkt")
    data class UgyldigPåminnelseTidspunkt(
        override val feilmelding: String
    ) :
        Error(),
        MutationNyOppgave.NyOppgaveResultat,
        MutationOppgaveUtsettFrist.OppgaveUtsettFristResultat

    @JsonTypeName("SakFinnesIkke")
    data class SakFinnesIkke(
        override val feilmelding: String,
    ):  Error(),
        MutationNyStatusSak.NyStatusSakResultat,
        MutationSoftDeleteSak.SoftDeleteSakResultat,
        MutationHardDeleteSak.HardDeleteSakResultat

    @JsonTypeName("OppgavenErAlleredeUtfoert")
    data class OppgavenErAlleredeUtfoert(
        override val feilmelding: String,
    ):  Error(),
        MutationOppgaveUtgaatt.OppgaveUtgaattResultat

}

