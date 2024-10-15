create table if not exists siirtotiedostot (
    id varchar not null,
    window_start varchar,
    window_end varchar not null,
    run_start timestamp with time zone not null default now(),
    run_end timestamp with time zone,
    info jsonb,
    success boolean,
    error_message varchar,
    PRIMARY KEY (id)
    );

insert into siirtotiedostot (id, window_start, window_end, run_start, run_end, info, success, error_message)
values ('5ff087d9-6ec9-466e-9115-e04c942083a2',
        '1970-01-01T00:00:00',
        '2024-08-01T00:00:00',
        now(),
        now(),
        '{"count": 1}'::jsonb,
        true,
        null) on conflict do nothing;

COMMENT ON column siirtotiedostot.run_start IS 'Siirtotiedosto-operaation suorituksen alkuaika';
COMMENT ON column siirtotiedostot.run_end IS 'Siirtotiedosto-operaation suorituksen loppuaika';
COMMENT ON column siirtotiedostot.info IS 'Tietoja tallennetuista entiteeteistä, mm. lukumäärät';
COMMENT ON column siirtotiedostot.error_message IS 'null, jos mikään ei mennyt vikaan';