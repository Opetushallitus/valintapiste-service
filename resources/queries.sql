-- name: find-valintapisteet-for-hakemus
-- row-fn: to-pistetieto
-- Returns valintapisteet for single hakemusoid
select tunniste, arvo, osallistuminen, tallettaja from valintapiste where hakemus_oid = :hakemus-oid

-- name: find-valintapisteet-for-hakemukset
-- Returns valintapisteet for multiple hakemusoids
select tunniste, arvo, osallistuminen, tallettaja from valintapiste where hakemus_oid in (:hakemus-oids)

-- name: upsert-valintapiste!
-- Upserts valintapiste for specific hakemus oid and tunnus
insert into valintapiste (hakemus_oid, tunniste, arvo, osallistuminen, tallettaja, system_time, transaction_id)
values (:hakemus-oid, :tunniste, :arvo, :osallistuminen, :tallettaja, :system_time, :transaction_id)
on conflict (hakemus_oid, tunniste) do update valintapiste
set arvo = :arvo, osallistuminen = :osallistuminen
where hakemus_oid = :hakemus_oid and tunniste = :tunniste
