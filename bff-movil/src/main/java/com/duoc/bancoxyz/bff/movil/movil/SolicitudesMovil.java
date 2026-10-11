package com.duoc.bancoxyz.bff.movil.movil;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Solicitudes del canal movil. El tope por operacion se valida en el controlador (configurable). */
public final class SolicitudesMovil {

    private SolicitudesMovil() {
    }

    public record Transferencia(
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaDestinoId,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
            @Size(max = 100, message = "maximo 100 caracteres") String descripcion) {

        @AssertTrue(message = "la cuenta de origen y destino no pueden ser la misma")
        public boolean isCuentasDistintas() {
            return cuentaOrigenId == null || !cuentaOrigenId.equals(cuentaDestinoId);
        }
    }

    public record Pago(
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
            @NotBlank(message = "indique a quien se paga") @Size(max = 100, message = "maximo 100 caracteres") String descripcion) {
    }
}
