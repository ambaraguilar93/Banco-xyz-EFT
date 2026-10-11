package com.duoc.bancoxyz.bff.movil.movil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Respuestas del canal MOVIL: solo lo que la pantalla del telefono muestra.
 * Comparado con la web: sin datos personales completos, sin fechas con hora,
 * sin campos internos, listas paginadas de a 10 y campos nulos omitidos
 * (@JsonInclude NON_NULL).
 */
public final class VistasMovil {

    private VistasMovil() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Inicio(String saludo, BigDecimal saldoDisponible, List<CuentaBreve> cuentas, Integer alertas,
            String aviso) {
    }

    /** Numero enmascarado: en el telefono no hace falta el numero completo. */
    public record CuentaBreve(Long id, String numero, String tipo, BigDecimal saldo) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaginaMovimientos(int pagina, boolean hayMas, List<MovimientoBreve> items) {
    }

    /**
     * monto con signo: negativo = cargo, positivo = abono (la app no necesita mas).
     */
    public record MovimientoBreve(LocalDate fecha, BigDecimal monto, String detalle) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EstadoOperacion(String id, String estado, String motivo) {
    }
}
