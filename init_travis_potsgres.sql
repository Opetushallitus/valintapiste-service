CREATE ROLE test;
ALTER ROLE test WITH login;
ALTER ROLE test WITH PASSWORD 'test';
GRANT ALL ON SCHEMA public TO test;