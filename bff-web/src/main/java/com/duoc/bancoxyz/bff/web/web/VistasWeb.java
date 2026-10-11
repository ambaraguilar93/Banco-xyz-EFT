package com.duoc.bancoxyz.bff.web.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.duoc.bancoxyz.bff.web.backend.Modelos.Cliente;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Cuenta;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Movimiento;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Notificacion;

/**
 * Respuestas del canal WEB: datos COMPLETOS para una interfaz rica (pantallas grandes,
 * tablas, graficos). Una sola llamada arma todo el panel del cliente.
 */
public final class VistasWeb {

    private VistasWeb() {
    }

    /** Panel principal: cliente + cuentas con sus ultimos movimientos + notificaciones. */
    public record Inicio(Cliente cliente, List<CuentaConMovimientos> cuentas, BigDecimal saldoTotal,
            BigDecimal deudaPrestamos, List<Notificacion> notificaciones, long alertasSinRevisar,
            Disponibilidad disponibilidad, OffsetDateTime generadoEn) {
    }

    public record CuentaConMovimientos(Cuenta cuenta, List<Movimiento> ultimosMovimientos) {
    }

    /**
     * Degradacion controlada: si un microservicio no responde, el panel igual se entrega
     * y el frontend sabe que seccion mostrar como "no disponible".
     */
    public record Disponibilidad(boolean cliente, boolean cuentas, boolean movimientos, boolean notificaciones) {
    }
}
