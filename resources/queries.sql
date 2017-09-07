-- name: find-valintapisteet-for-hakemus
-- row-fn: to-pistetieto
-- Returns valintapisteet for single hakemusoid
select tunniste, arvo from valintapiste where hakemus_oid = :hakemus-oid

-- name: find-valintapisteet-for-hakemukset
-- Returns valintapisteet for multiple hakemusoids
select tunniste, arvo from valintapiste where hakemus_oid in (:hakemus-oids)

