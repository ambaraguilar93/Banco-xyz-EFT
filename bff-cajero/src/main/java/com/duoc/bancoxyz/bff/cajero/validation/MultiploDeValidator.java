package com.duoc.bancoxyz.bff.cajero.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

/** Logica de {@link MultiploDe} para montos BigDecimal. */
public class MultiploDeValidator implements ConstraintValidator<MultiploDe, BigDecimal> {

    private BigDecimal multiplo;

    @Override
    public void initialize(MultiploDe anotacion) {
        if (anotacion.value() <= 0) {
            throw new IllegalArgumentException("@MultiploDe requiere un valor mayor que 0");
        }
        this.multiplo = BigDecimal.valueOf(anotacion.value());
    }

    @Override
    public boolean isValid(BigDecimal valor, ConstraintValidatorContext context) {
        if (valor == null) {
            return true;
        }
        // remainder funciona con decimales: 7500.50 % 5000 = 2500.50 -> invalido
        return valor.remainder(multiplo).signum() == 0;
    }
}
