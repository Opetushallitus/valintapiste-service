-- name: find-valintapisteet-for-hakemus
-- Returns valintapisteet for single hakemusoid
select * from valintapiste where hakemus_oid = :hakemus_oid

-- name: find-valintapisteet-for-hakemukset
-- Returns valintapisteet for multiple hakemusoids
select * from valintapiste where hakemus_oid in (:hakemus_oids)

