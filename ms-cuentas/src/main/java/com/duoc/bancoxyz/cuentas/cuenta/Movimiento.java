package com.duoc.bancoxyz.cuentas.cuenta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Linea de la cartola: un debito o credito aplicado a una cuenta. */
public record Movimiento(Long id, String operacionId, Long cuentaId, String tipoMovimiento, String tipoOperacion,
        BigDecimal monto, BigDecimal saldoResultante, String descripcion, OffsetDateTime fecha) {
}
