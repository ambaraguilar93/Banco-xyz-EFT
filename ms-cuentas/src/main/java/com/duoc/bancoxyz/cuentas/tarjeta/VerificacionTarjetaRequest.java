package com.duoc.bancoxyz.cuentas.tarjeta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Tarjeta de 16 digitos + PIN de 4. El PIN nunca se guarda ni se registra en el log. */
public record VerificacionTarjetaRequest(
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{16}$", message = "debe tener 16 digitos") String numeroTarjeta,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{4}$", message = "debe tener 4 digitos") String pin) {

    @Override
    public String toString() {
        return "VerificacionTarjetaRequest[tarjeta=****" + (numeroTarjeta == null || numeroTarjeta.length() < 4 ? ""
                : numeroTarjeta.substring(numeroTarjeta.length() - 4)) + ", pin=****]";
    }
}
