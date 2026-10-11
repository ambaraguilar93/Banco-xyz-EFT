package com.duoc.bancoxyz.cuentas.mensajeria.eventos;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.duoc.bancoxyz.cuentas.comun.CorrelationId;
import com.duoc.bancoxyz.cuentas.cuenta.Cuenta;
import com.duoc.bancoxyz.cuentas.mensajeria.Evento;

/** Cambio en el ciclo de vida de una cuenta: CuentaAbierta, CuentaBloqueada, CuentaDesbloqueada, CuentaCerrada. */
public record CuentaEvento(String eventId, String tipo, Long cuentaId, Long clienteId, String tipoCuenta,
        String estado, BigDecimal saldo, String motivo, String correlationId, OffsetDateTime fecha)
        implements Evento {

    public static CuentaEvento de(String tipo, Cuenta cuenta, String motivo) {
        return new CuentaEvento(UUID.randomUUID().toString(), tipo, cuenta.id(), cuenta.clienteId(), cuenta.tipo(),
                cuenta.estado(), cuenta.saldo(), motivo, CorrelationId.actual(), OffsetDateTime.now());
    }
}
