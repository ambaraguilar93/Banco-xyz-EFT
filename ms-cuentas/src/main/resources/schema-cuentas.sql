-- =====================================================================
-- ms-cuentas: tablas propias (idempotente, se ejecuta en cada arranque)
-- Misma base banco_xyz que el batch, pero cada microservicio usa SOLO sus tablas.
-- =====================================================================

CREATE SEQUENCE IF NOT EXISTS cuentas_id_seq START 100000;

CREATE TABLE IF NOT EXISTS cuentas (
    id              BIGINT        PRIMARY KEY,
    cliente_id      BIGINT        NOT NULL,
    tipo            VARCHAR(12)   NOT NULL CHECK (tipo IN ('AHORRO', 'CORRIENTE', 'PRESTAMO')),
    saldo           NUMERIC(15,2) NOT NULL CHECK (saldo >= 0),
    estado          VARCHAR(10)   NOT NULL CHECK (estado IN ('ACTIVA', 'BLOQUEADA', 'CERRADA')),
    motivo_estado   VARCHAR(200),
    abierta_en      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    cerrada_en      TIMESTAMPTZ,
    actualizada_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_cuentas_cliente ON cuentas (cliente_id);

-- Cartola: un registro por cada debito o credito aplicado
CREATE TABLE IF NOT EXISTS movimientos_cuenta (
    id               BIGSERIAL     PRIMARY KEY,
    operacion_id     VARCHAR(36)   NOT NULL,
    cuenta_id        BIGINT        NOT NULL REFERENCES cuentas (id),
    tipo_movimiento  VARCHAR(7)    NOT NULL CHECK (tipo_movimiento IN ('DEBITO', 'CREDITO')),
    tipo_operacion   VARCHAR(15)   NOT NULL,
    monto            NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    saldo_resultante NUMERIC(15,2) NOT NULL,
    descripcion      VARCHAR(200),
    fecha            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (operacion_id, cuenta_id, tipo_movimiento)
);
CREATE INDEX IF NOT EXISTS idx_movimientos_cuenta ON movimientos_cuenta (cuenta_id, id DESC);

-- Idempotencia: cada operacion de ms-pagos se aplica (o rechaza) una sola vez
CREATE TABLE IF NOT EXISTS operaciones_procesadas (
    operacion_id    VARCHAR(36)  PRIMARY KEY,
    tipo_operacion  VARCHAR(15)  NOT NULL,
    resultado       VARCHAR(10)  NOT NULL,
    motivo          VARCHAR(300),
    instancia       VARCHAR(80),
    resultado_json  TEXT         NOT NULL,
    procesada_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Replica local de clientes (fuente de verdad: ms-clientes, via banco.clientes.eventos)
CREATE TABLE IF NOT EXISTS clientes_replica (
    cliente_id      BIGINT       PRIMARY KEY,
    nombre          VARCHAR(150),
    estado          VARCHAR(10)  NOT NULL,
    actualizado_en  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Carga inicial desde los datos migrados por Spring Batch ----------
INSERT INTO clientes_replica (cliente_id, nombre, estado)
SELECT ROW_NUMBER() OVER (ORDER BY nombre), nombre, 'ACTIVO'
FROM (SELECT DISTINCT nombre FROM intereses_calculados WHERE nombre IS NOT NULL AND nombre <> 'Unknown') n
ON CONFLICT (cliente_id) DO NOTHING;

INSERT INTO cuentas (id, cliente_id, tipo, saldo, estado)
SELECT DISTINCT ON (i.cuenta_id) i.cuenta_id, c.cliente_id,
       CASE lower(i.tipo) WHEN 'prestamo' THEN 'PRESTAMO' ELSE 'AHORRO' END,
       GREATEST(ROUND(i.saldo_final, 0), 0), 'ACTIVA'
FROM intereses_calculados i
JOIN clientes_replica c ON c.nombre = i.nombre
ORDER BY i.cuenta_id, i.id
ON CONFLICT (id) DO NOTHING;

-- ---------- Tarjetas de debito (cajero automatico) ----------
CREATE TABLE IF NOT EXISTS tarjetas (
    numero             VARCHAR(16)  PRIMARY KEY,
    cuenta_id          BIGINT       NOT NULL REFERENCES cuentas (id),
    pin_hash           VARCHAR(80)  NOT NULL,
    estado             VARCHAR(10)  NOT NULL CHECK (estado IN ('ACTIVA', 'BLOQUEADA')),
    intentos_fallidos  INTEGER      NOT NULL DEFAULT 0,
    bloqueada_en       TIMESTAMPTZ,
    ultimo_uso         TIMESTAMPTZ,
    creada_en          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tarjetas_cuenta ON tarjetas (cuenta_id);

-- Datos de prueba: una tarjeta por cada cuenta de AHORRO migrada.
INSERT INTO tarjetas (numero, cuenta_id, pin_hash, estado)
SELECT '4532' || lpad(id::text, 12, '0'), id, '$2a$10$gCPDl5ouYMON.6Dgy8QW5.MOtWCSStM/mpd.FbjZGrBuA8LMRGg/W', 'ACTIVA'
FROM cuentas WHERE tipo = 'AHORRO'
ON CONFLICT (numero) DO NOTHING;

-- ---------- Transactional Outbox: eventos por publicar en Kafka ----------
CREATE TABLE IF NOT EXISTS outbox_cuentas (
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
CREATE INDEX IF NOT EXISTS idx_outbox_cuentas_pendientes ON outbox_cuentas (id) WHERE publicado_en IS NULL;

-- ---------- Consumidor idempotente: eventos ya procesados ----------
CREATE TABLE IF NOT EXISTS eventos_recibidos_cuentas (
    event_id     UUID        PRIMARY KEY,
    tipo         VARCHAR(60) NOT NULL,
    recibido_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
