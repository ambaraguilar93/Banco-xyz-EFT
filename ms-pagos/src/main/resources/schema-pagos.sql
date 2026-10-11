-- =====================================================================
-- ms-pagos: tablas propias (idempotente, se ejecuta en cada arranque)
-- =====================================================================

CREATE TABLE IF NOT EXISTS operaciones (
    id                  VARCHAR(36)   PRIMARY KEY,
    tipo                VARCHAR(15)   NOT NULL,
    cuenta_origen_id    BIGINT,
    cuenta_destino_id   BIGINT,
    monto               NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    descripcion         VARCHAR(200),
    estado              VARCHAR(10)   NOT NULL CHECK (estado IN ('PENDIENTE', 'COMPLETADA', 'RECHAZADA')),
    motivo              VARCHAR(300),
    validacion          VARCHAR(8)    NOT NULL CHECK (validacion IN ('SINCRONA', 'DIFERIDA')),
    cliente_origen_id   BIGINT,
    cliente_destino_id  BIGINT,
    idempotency_key     VARCHAR(100)  NOT NULL UNIQUE,
    reenvios            INTEGER       NOT NULL DEFAULT 0,
    correlation_id      VARCHAR(80),
    creada_en           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizada_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (cuenta_origen_id IS NOT NULL OR cuenta_destino_id IS NOT NULL)
);
-- Tipo RETIRO (cajero). La restriccion se recrea para que tambien se actualice
-- en una base que ya existia (CREATE TABLE IF NOT EXISTS no modifica tablas existentes).
ALTER TABLE operaciones DROP CONSTRAINT IF EXISTS operaciones_tipo_check;
ALTER TABLE operaciones ADD CONSTRAINT operaciones_tipo_check CHECK (tipo IN ('PAGO', 'RETIRO', 'TRANSFERENCIA', 'DEPOSITO'));

CREATE INDEX IF NOT EXISTS idx_operaciones_origen ON operaciones (cuenta_origen_id, creada_en DESC);
CREATE INDEX IF NOT EXISTS idx_operaciones_destino ON operaciones (cuenta_destino_id, creada_en DESC);
CREATE INDEX IF NOT EXISTS idx_operaciones_pendientes ON operaciones (actualizada_en) WHERE estado = 'PENDIENTE';

-- ---------- Transactional Outbox: eventos por publicar en Kafka ----------
CREATE TABLE IF NOT EXISTS outbox_pagos (
    id             BIGSERIAL    PRIMARY KEY,
    event_id       UUID         NOT NULL UNIQUE,
    topico         VARCHAR(80)  NOT NULL,
    clave          VARCHAR(80)  NOT NULL,
    tipo           VARCHAR(60)  NOT NULL,
    correlation_id VARCHAR(80)  NOT NULL,
    payload        TEXT         NOT NULL,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en   TIMESTAMPTZ,
    intentos       INTEGER      NOT NULL DEFAULT 0,
    ultimo_error   VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS idx_outbox_pagos_pendientes ON outbox_pagos (id) WHERE publicado_en IS NULL;

-- ---------- Consumidor idempotente: eventos ya procesados ----------
CREATE TABLE IF NOT EXISTS eventos_recibidos_pagos (
    event_id     UUID        PRIMARY KEY,
    tipo         VARCHAR(60) NOT NULL,
    recibido_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
