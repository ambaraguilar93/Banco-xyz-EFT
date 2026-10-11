package com.duoc.bancoxyz.pagos.operacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Operacion financiera. tipo: PAGO o RETIRO (debitan origen), TRANSFERENCIA (origen -> destino) o
 * DEPOSITO (acredita destino). estado: PENDIENTE -> COMPLETADA | RECHAZADA.
 * validacion: SINCRONA (ms-cuentas respondio al registrar) o DIFERIDA (ms-cuentas no
 * disponible: la valida ms-cuentas al procesarla, ver fallback de CuentasClient).
 */
public record Operacion(String id, String tipo, Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto,
        String descripcion, String estado, String motivo, String validacion, Long clienteOrigenId,
        Long clienteDestinoId, String idempotencyKey, int reenvios, String correlationId, OffsetDateTime creadaEn,
        OffsetDateTime actualizadaEn) {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String COMPLETADA = "COMPLETADA";
    public static final String RECHAZADA = "RECHAZADA";

    /** Cuenta que define el orden en Kafka: las operaciones de una misma cuenta se procesan en orden. */
    public String claveKafka() {
        return String.valueOf(cuentaOrigenId != null ? cuentaOrigenId : cuentaDestinoId);
    }
}
