package com.duoc.bancoxyz.clientes.cuentas;

import java.math.BigDecimal;

/** Cuenta tal como la entrega ms-cuentas (solo lo que el perfil necesita). */
public record CuentaResumen(Long id, String tipo, BigDecimal saldo, String estado) {
}
