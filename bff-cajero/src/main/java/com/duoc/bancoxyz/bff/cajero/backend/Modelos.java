package com.duoc.bancoxyz.bff.cajero.backend;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Datos tal como los entregan los microservicios. Cada BFF tiene su propia
 * copia y luego arma la respuesta que necesita su canal.
 */
public final class Modelos {

    private Modelos() {
    }

    public record Cliente(Long id, String rut, String nombre, String email, String telefono, String direccion,
            String estado, String motivoEstado, OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {
    }

    public record Cuenta(Long id, Long clienteId, String tipo, BigDecimal saldo, String estado, String motivoEstado,
            OffsetDateTime abiertaEn, OffsetDateTime cerradaEn, OffsetDateTime actualizadaEn) {

        public boolean activa() {
            return "ACTIVA".equals(estado);
        }
    }

    public record Movimiento(Long id, String operacionId, Long cuentaId, String tipoMovimiento, String tipoOperacion,
            BigDecimal monto, BigDecimal saldoResultante, String descripcion, OffsetDateTime fecha) {
    }

    public record Operacion(String id, String tipo, Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal monto,
            String descripcion, String estado, String motivo, String validacion, Long clienteOrigenId,
            Long clienteDestinoId, String idempotencyKey, Integer reenvios, String correlationId,
            OffsetDateTime creadaEn, OffsetDateTime actualizadaEn) {

        public boolean involucraA(Long clienteId) {
            return clienteId != null && (clienteId.equals(clienteOrigenId) || clienteId.equals(clienteDestinoId));
        }
    }

    public record Notificacion(Long id, Long clienteId, String categoria, String nivel, String titulo,
            String detalle, String referencia, String origen, OffsetDateTime fecha) {
    }
}
