-- =====================================================================
-- Banco XYZ - Esquema de la migracion batch
-- =====================================================================

-- JOB 1: Reporte de transacciones diarias

CREATE TABLE IF NOT EXISTS transacciones (
    id     BIGINT         PRIMARY KEY,
    fecha  DATE           NOT NULL,
    monto  NUMERIC(15, 2) NOT NULL,
    tipo   VARCHAR(20)    NOT NULL
);

CREATE TABLE IF NOT EXISTS resumen_transacciones_diarias (
    fecha                  DATE           PRIMARY KEY,
    cantidad_transacciones INTEGER        NOT NULL,
    total_debitos          NUMERIC(15, 2) NOT NULL,
    total_creditos         NUMERIC(15, 2) NOT NULL,
    monto_mayor            NUMERIC(15, 2) NOT NULL
);

-- JOB 2: Calculo de intereses mensuales

CREATE TABLE IF NOT EXISTS intereses_calculados (
    id              BIGSERIAL      PRIMARY KEY,
    cuenta_id       BIGINT         NOT NULL,
    nombre          VARCHAR(150)   NOT NULL,
    tipo            VARCHAR(20)    NOT NULL,
    saldo_inicial   NUMERIC(15, 2) NOT NULL,
    tasa_aplicada   NUMERIC(6, 4)  NOT NULL,
    interes_mensual NUMERIC(15, 2) NOT NULL,
    saldo_final     NUMERIC(15, 2) NOT NULL
);

ALTER TABLE intereses_calculados ADD COLUMN IF NOT EXISTS linea_origen INTEGER;

-- JOB 3: Generacion de Estados de Cuenta Anuales

CREATE TABLE IF NOT EXISTS cuentas_anuales (
    id          BIGSERIAL      PRIMARY KEY,
    cuenta_id   BIGINT         NOT NULL,
    fecha       DATE           NOT NULL,
    transaccion VARCHAR(20)    NOT NULL,
    monto       NUMERIC(15, 2) NOT NULL,
    descripcion VARCHAR(250)
);

CREATE TABLE IF NOT EXISTS estados_cuenta_anuales (
    cuenta_id            BIGINT         NOT NULL,
    anio                 INTEGER        NOT NULL,
    cantidad_movimientos INTEGER        NOT NULL,
    total_depositos      NUMERIC(15, 2) NOT NULL,
    total_retiros        NUMERIC(15, 2) NOT NULL,
    total_compras        NUMERIC(15, 2) NOT NULL,
    saldo_neto           NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (cuenta_id, anio)
);

ALTER TABLE estados_cuenta_anuales ADD COLUMN IF NOT EXISTS total_pagos NUMERIC(15, 2) NOT NULL DEFAULT 0;

-- Auditoria de errores (compartida por los tres Jobs)

CREATE TABLE IF NOT EXISTS registros_rechazados (
    id             BIGSERIAL    PRIMARY KEY,
    job_name       VARCHAR(60)  NOT NULL,
    archivo_origen VARCHAR(60)  NOT NULL,
    identificador  VARCHAR(60),
    motivo         VARCHAR(250) NOT NULL,
    dato_original  VARCHAR(500),
    fecha_registro TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE registros_rechazados ADD COLUMN IF NOT EXISTS job_instance_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_rechazos_instancia ON registros_rechazados (job_instance_id, archivo_origen);

CREATE TABLE IF NOT EXISTS auditoria_integridad (
    id                   BIGSERIAL   PRIMARY KEY,
    job_instance_id      BIGINT      NOT NULL,
    job_execution_id     BIGINT      NOT NULL,
    job_name             VARCHAR(60) NOT NULL,
    archivo_origen       VARCHAR(60) NOT NULL,
    lineas_archivo       INTEGER     NOT NULL,
    registros_validos    INTEGER     NOT NULL,
    registros_rechazados INTEGER     NOT NULL,
    consistente          BOOLEAN     NOT NULL,
    fecha_registro       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS escalamiento_transacciones (
    id     BIGINT         PRIMARY KEY,
    fecha  DATE           NOT NULL,
    monto  NUMERIC(15, 2) NOT NULL,
    tipo   VARCHAR(20)    NOT NULL
);
