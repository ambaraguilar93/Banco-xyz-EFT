package com.duoc.bancoxyz.bff.web.web;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Cuerpos de las solicitudes del canal web (se validan aqui antes de llamar al backend). */
public final class SolicitudesWeb {

    private SolicitudesWeb() {
    }

    public record Pago(
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
            @NotBlank(message = "indique a quien se paga") @Size(max = 200, message = "maximo 200 caracteres") String descripcion) {
    }

    public record Transferencia(
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaOrigenId,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long cuentaDestinoId,
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal monto,
            @Size(max = 200, message = "maximo 200 caracteres") String descripcion) {

        @AssertTrue(message = "la cuenta de origen y destino no pueden ser la misma")
        public boolean isCuentasDistintas() {
            return cuentaOrigenId == null || !cuentaOrigenId.equals(cuentaDestinoId);
        }
    }

    public record AperturaCuenta(
            @NotBlank(message = "es obligatorio") @Pattern(regexp = "AHORRO|CORRIENTE", message = "debe ser AHORRO o CORRIENTE") String tipo,
            @PositiveOrZero(message = "no puede ser negativo") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal saldoInicial) {
    }

    public record DatosPersonales(
            @NotBlank(message = "es obligatorio") @Size(min = 3, max = 150, message = "debe tener entre 3 y 150 caracteres") String nombre,
            @NotBlank(message = "es obligatorio") @Email(message = "no es un correo valido") @Size(max = 150, message = "maximo 150 caracteres") String email,
            @Pattern(regexp = "^\\+?\\d{8,12}$", message = "debe tener entre 8 y 12 digitos (ej. +56912345678)") String telefono,
            @Size(max = 200, message = "maximo 200 caracteres") String direccion) {
    }
}
