-- GS-USR-002 - Roles de usuario (OWNER, ADMIN, STAFF).
-- Hibernate (enum EnumType.STRING) crea en "users" un CHECK sobre "role" que solo admite los valores
-- del enum vigentes al crear la tabla ('OWNER'). ddl-auto=update NO lo modifica, por lo que sin este
-- script crear un ADMIN/STAFF falla en producción con una violación de CHECK (HTTP 500).
--
-- Este script elimina cualquier CHECK de "users" que referencie "role" y crea uno nuevo con los tres
-- roles. Es idempotente y NO modifica filas: los usuarios existentes (todos OWNER) y sus business_id
-- quedan intactos.
--
-- Orden de despliegue: (1) aplicar este script, (2) desplegar la aplicación. Es compatible hacia
-- atrás: la versión anterior solo usa OWNER, que sigue permitido.

BEGIN;

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
                 JOIN pg_class rel ON rel.oid = con.conrelid
                 JOIN pg_namespace ns ON ns.oid = rel.relnamespace
        WHERE rel.relname = 'users'
          AND ns.nspname = current_schema()
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT %I', c.conname);
    END LOOP;
END
$$;

ALTER TABLE users
    ADD CONSTRAINT users_role_check CHECK (role IN ('OWNER', 'ADMIN', 'STAFF'));

COMMIT;
