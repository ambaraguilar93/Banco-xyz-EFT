package com.duoc.bancoxyz.pagos.cuentas;

import java.math.BigDecimal;

/** Lo que ms-pagos necesita saber de una cuenta (respuesta de GET /api/cuentas/{id} de ms-cuentas). */
public record CuentaResumen(Long id, Long clienteId, String tipo, BigDecimal saldo, String estado) {

    public boolean activa() {
        return "ACTIVA".equals(estado);
    }

    public boolean esPrestamo() {
        return "PRESTAMO".equals(tipo);
    }
}
