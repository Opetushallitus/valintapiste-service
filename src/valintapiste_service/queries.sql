-- name: pistetiedot-for-hakemus
-- Returns pistetiedot for specific hakemus oids
select * from valintapiste where hakemus_oid in :hakemus_oids

