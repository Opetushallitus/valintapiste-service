-- name: find-valintapisteet-for-hakemus
-- row-fn: to-pistetieto
-- Returns valintapisteet for single hakemusoid
select tunnus, arvo from valintapiste where hakemus_oid = :hakemus-oid

-- name: find-valintapisteet-for-hakemukset
-- Returns valintapisteet for multiple hakemusoids
select tunnus, arvo from valintapiste where hakemus_oid in (:hakemus-oids)

