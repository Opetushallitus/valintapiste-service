
-- name: find-valintapisteet-for-hakemukset
-- Returns valintapisteet for multiple hakemusoids
select hakemus_oid, tunniste, arvo, osallistuminen, tallettaja from valintapiste where hakemus_oid in (:hakemus-oids)

-- name: last-modified-for-hakemukset
-- Returns last-modified timestamp for multiple hakemusoids
select lower(system_time) from valintapiste where hakemus_oid in (:hakemus-oids) order by lower(system_time) desc limit 1

-- name: modified-since-hakemukset
-- Returns last-modified timestamp for multiple hakemusoids
select hakemus_oid from valintapiste where hakemus_oid in (:hakemus-oids) and not system_time @> :unmodified-since::timestamptz

-- name: upsert-valintapiste!
-- Upserts valintapiste for specific hakemus oid and tunnus
insert into valintapiste (hakemus_oid, tunniste, arvo, osallistuminen, tallettaja)
values (:hakemus-oid, :tunniste, :arvo, :osallistuminen, :tallettaja)
on conflict (hakemus_oid, tunniste) do update
set arvo = :arvo,
    osallistuminen = :osallistuminen,
    tallettaja = :tallettaja,
    system_time = tstzrange(now(), null, '[)')
where valintapiste.hakemus_oid = :hakemus-oid and valintapiste.tunniste = :tunniste

-- name: find-hakemus-oids-by-timerange
-- Returns hakemusoids created/modified between given timerange
select distinct(hakemus_oid) as hakemus_oid from valintapiste where lower(system_time) between :start::timestamptz and :end::timestamptz

-- name: find-hakemus-oids-by-timelimit
-- Returns hakemusoids created/modified before given timelimit
select distinct(hakemus_oid) as hakemus_oid from valintapiste where lower(system_time) < :end::timestampz


