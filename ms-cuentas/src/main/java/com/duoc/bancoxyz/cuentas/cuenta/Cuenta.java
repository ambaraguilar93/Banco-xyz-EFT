package com.duoc.bancoxyz.cuentas.cuenta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Cuenta bancaria. tipo: AHORRO, CORRIENTE o PRESTAMO. estado: ACTIVA, BLOQUEADA o CERRADA.
 * En una cuenta PRESTAMO el saldo es la deuda: no se puede debitar desde ella.
 */
public record Cuenta(Long id, Long clienteId, String tipo, BigDecimal saldo, String estado, String motivoEstado,
        OffsetDateTime abiertaEn, OffsetDateTime cerradaEn, OffsetDateTime actualizadaEn) {

    public static final String ACTIVA = "ACTIVA";
    public static final String BLOQUEADA = "BLOQUEADA";
    public static final String CERRADA = "CERRADA";
    public static final String PRESTAMO = "PRESTAMO";

    public boolean activa() {
        return ACTIVA.equals(estado);
    }
}
