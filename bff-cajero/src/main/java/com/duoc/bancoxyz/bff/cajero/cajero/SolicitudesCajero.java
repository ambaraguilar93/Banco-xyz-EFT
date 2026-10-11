package com.duoc.bancoxyz.bff.cajero.cajero;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.duoc.bancoxyz.bff.cajero.validation.MultiploDe;

public final class SolicitudesCajero {

    private SolicitudesCajero() {
    }

    /** Lectura de la tarjeta + PIN digitado. toString no muestra el PIN (no queda en logs). */
    public record InicioSesion(
            @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{16}$", message = "debe tener 16 digitos") String numeroTarjeta,
            @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{4}$", message = "debe tener 4 digitos") String pin) {

        @Override
        public String toString() {
            return "InicioSesion[tarjeta=****" + (numeroTarjeta == null || numeroTarjeta.length() < 4 ? ""
                    : numeroTarjeta.substring(numeroTarjeta.length() - 4)) + ", pin=****]";
        }
    }

    public record Retiro(
            @NotNull(message = "es obligatorio")
            @Digits(integer = ReglasCajero.DIGITOS_ENTEROS_MONTO, fraction = 0, message = "debe ser un monto en pesos sin decimales")
            @DecimalMin(value = ReglasCajero.MONTO_MINIMO, message = "el minimo por operacion es $5.000")
            @DecimalMax(value = ReglasCajero.MONTO_MAXIMO, message = "el maximo por operacion es $200.000")
            @MultiploDe(value = ReglasCajero.MULTIPLO_MONTO, message = "debe ser multiplo de $5.000 (montos que puede entregar el cajero)")
            BigDecimal monto) {
    }
}
