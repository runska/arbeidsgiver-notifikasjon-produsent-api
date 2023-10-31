create table merkelapp_grupperingsid_notifikasjon
(
    notifikasjon_id uuid primary key not null,
    merkelapp text not null,
    grupperingsid text
);

create index merkelapp_grupperingsid_notifikasjon_merkelapp_idx on merkelapp_grupperingsid_notifikasjon (merkelapp);
create index merkelapp_grupperingsid_notifikasjon_grupperingsid_idx on merkelapp_grupperingsid_notifikasjon (grupperingsid);

