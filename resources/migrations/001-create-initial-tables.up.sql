create type osallistumistieto as enum (
    'EI_OSALLISTUNUT',
    'OSALLISTUI'
);

create table valintapiste (
    hakemus_oid varchar not null,
    tunniste varchar not null,
    arvo varchar,
    osallistuminen osallistumistieto,
    tallettaja integer not null,
    system_time tstzrange not null,
    transaction_id bigint not null,
    primary key (hakemus_oid, tunniste)
);

create table valintapiste_history (like valintapiste);

create function update_valintapiste_history() returns trigger
language plpgsql
as $$
begin
    insert into valintapiste_history (
        hakemus_oid,
        tunniste,
        arvo,
        osallistuminen,
        tallettaja,
        system_time,
        transaction_id
    ) values (
        old.hakemus_oid,
        old.tunniste,
        old.arvo,
        old.osallistuminen,
        old.tallettaja,
        tstzrange(lower(old.system_time), now(), '[)'),
        old.transaction_id
    );
    return null;
end;
$$;

create trigger delete_valintapiste_history
after delete
on valintapiste
for each row
    execute procedure update_valintapiste_history();

create trigger update_valintapiste_history
after update
on valintapiste
for each row
    execute procedure update_valintapiste_history();
