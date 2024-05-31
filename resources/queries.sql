
-- name: find-valintapisteet-for-hakemukset
-- Returns valintapisteet for multiple hakemusoids
select hakemus_oid, tunniste, arvo, osallistuminen, tallettaja from valintapiste where hakemus_oid in (:hakemus-oids)

-- name: find-valintapiste-bulk-by-timerange
-- Returns valintapisteet by timerange
select hakemus_oid, tunniste, arvo, osallistuminen, tallettaja, lower(system_time) as last_modified from valintapiste
where lower(system_time) >= :start::timestamptz and lower(system_time) < :end::timestamptz limit :limit offset :offset

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

-- name: latest-siirtotiedosto-data
-- Returns latest siirtotiedosto-operation data
select id,  window_start, window_end, run_start, run_end, info, success, error_message from siirtotiedostot
where run_start = (select max(run_start) from siirtotiedostot)

-- name: upsert-siirtotiedosto-data!
-- Upserts siirtotiedosto-operation data
insert into siirtotiedostot (id, window_start, window_end, run_start, run_end, info, success, error_message)
values (:id::uuid, :window_start, :window_end, :run_start::timestamp, :run_end::timestamp,
    :info::jsonb, :success, :error_message)
on conflict on constraint siirtotiedostot_pkey do update
set window_start = :window_start,
    window_end = :window_end,
    run_start = :run_start,
    run_end = :run_end,
    info = :info,
    success = :success,
    error_message = :error_message

-- name: find-deleted
-- Returns hakemusOids deleted in given timerange
select distinct(h.hakemus_oid) hakemus_oid from valintapiste_history h
where upper(h.system_time) is not null
  and upper(h.system_time) >= :start::timestamptz
  and upper(h.system_time) < :end::timestamptz
  and not exists (select 1 from valintapiste where hakemus_oid = h.hakemus_oid)

