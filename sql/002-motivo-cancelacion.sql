-- Migracion: motivo de cancelacion de citas.
-- Agrega citas.motivo_cancelacion (NULL permitido: las citas canceladas antes de esta
-- funcionalidad no tienen motivo). No borra tablas ni datos.
--
-- Es idempotente y tolera el orden de ejecucion: docker-entrypoint-initdb.d corre los .sql
-- en orden alfabetico, y "002-..." va ANTES de "medisalud.sql". En una instalacion nueva
-- la tabla aun no existe (y medisalud.sql ya trae la columna), asi que aqui no se hace nada.

CREATE DATABASE IF NOT EXISTS medisalud
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE medisalud;

SET @sentencia = (
    SELECT IF(
        EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'medisalud' AND table_name = 'citas')
        AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                        WHERE table_schema = 'medisalud' AND table_name = 'citas'
                          AND column_name = 'motivo_cancelacion'),
        'ALTER TABLE citas ADD COLUMN motivo_cancelacion VARCHAR(200) NULL',
        'SELECT 1'
    )
);
PREPARE aplicar FROM @sentencia;
EXECUTE aplicar;
DEALLOCATE PREPARE aplicar;
