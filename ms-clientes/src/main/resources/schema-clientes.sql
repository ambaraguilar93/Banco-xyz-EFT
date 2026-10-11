-- =====================================================================
-- ms-clientes: tablas propias (idempotente, se ejecuta en cada arranque)
-- Misma base banco_xyz que el batch, pero cada microservicio usa solo sus tablas.
-- =====================================================================

CREATE TABLE IF NOT EXISTS clientes (
    id              BIGSERIAL     PRIMARY KEY,
    rut             VARCHAR(12)   UNIQUE,
    nombre          VARCHAR(150)  NOT NULL,
    email           VARCHAR(150)  NOT NULL,
    telefono        VARCHAR(15),
    direccion       VARCHAR(200),
    estado          VARCHAR(10)   NOT NULL CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    motivo_estado   VARCHAR(200),
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ---------- Carga inicial desde los datos migrados por Spring Batch ----------
INSERT INTO clientes (id, nombre, email, estado)
SELECT ROW_NUMBER() OVER (ORDER BY nombre), nombre,
       lower(regexp_replace(translate(nombre, 'ÁÉÍÓÚáéíóúÑñÜü', 'AEIOUaeiouNnUu'), '[^A-Za-z]+', '.', 'g')) || '@cliente.bancoxyz.cl',
       'ACTIVO'
FROM (SELECT DISTINCT nombre FROM intereses_calculados WHERE nombre IS NOT NULL AND nombre <> 'Unknown') n
ON CONFLICT (id) DO NOTHING;

-- Los clientes nuevos (POST /api/clientes) parten desde el 1001 para no chocar con los migrados
SELECT setval('clientes_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM clientes), 1000));

-- ---------- Historial de notificaciones (eventos de ms-cuentas y ms-pagos) ----------
CREATE TABLE IF NOT EXISTS notificaciones_cliente (
    id          BIGSERIAL     PRIMARY KEY,
    event_id    UUID          NOT NULL,
    cliente_id  BIGINT        NOT NULL,
    categoria   VARCHAR(12)   NOT NULL CHECK (categoria IN ('TRANSACCION', 'CUENTA', 'ALERTA')),
    nivel       VARCHAR(5)    NOT NULL,
    titulo      VARCHAR(200)  NOT NULL,
    detalle     VARCHAR(500),
    referencia  VARCHAR(40),
    origen      VARCHAR(30),
    fecha       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (event_id, cliente_id)
);
CREATE INDEX IF NOT EXISTS idx_notificaciones_cliente ON notificaciones_cliente (cliente_id, id DESC);

-- ---------- Transactional Outbox: eventos por publicar en Kafka ----------
CREATE TABLE IF NOT EXISTS outbox_clientes (
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
CREATE INDEX IF NOT EXISTS idx_outbox_clientes_pendientes ON outbox_clientes (id) WHERE publicado_en IS NULL;

-- ---------- Consumidor idempotente: eventos ya procesados ----------
CREATE TABLE IF NOT EXISTS eventos_recibidos_clientes (
    event_id     UUID        PRIMARY KEY,
    tipo         VARCHAR(60) NOT NULL,
    recibido_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);
