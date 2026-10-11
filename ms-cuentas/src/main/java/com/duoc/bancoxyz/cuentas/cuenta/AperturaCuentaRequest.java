package com.duoc.bancoxyz.cuentas.cuenta;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Apertura de cuenta. Solo AHORRO o CORRIENTE (los prestamos los otorga otro proceso). */
public record AperturaCuentaRequest(
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Long clienteId,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "AHORRO|CORRIENTE", message = "debe ser AHORRO o CORRIENTE") String tipo,
        @PositiveOrZero(message = "no puede ser negativo") @DecimalMax(value = "50000000", message = "no puede superar $50.000.000") @Digits(integer = 12, fraction = 0, message = "debe ser un monto en pesos sin decimales") BigDecimal saldoInicial) {
}
