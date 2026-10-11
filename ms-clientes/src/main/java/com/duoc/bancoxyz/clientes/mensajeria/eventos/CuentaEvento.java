package com.duoc.bancoxyz.clientes.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.duoc.bancoxyz.clientes.mensajeria.Evento;

/** De ms-cuentas: CuentaAbierta, CuentaBloqueada, CuentaDesbloqueada o CuentaCerrada. */
public record CuentaEvento(String eventId, String tipo, Long cuentaId, Long clienteId, String tipoCuenta,
        String estado, BigDecimal saldo, String motivo, String correlationId, OffsetDateTime fecha)
        implements Evento {
}
