package com.duoc.bancoxyz.bff.cajero.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restriccion declarativa propia: el valor debe ser multiplo exacto de
 * {@link #value()}.
 *
 * Se usa en el monto del retiro porque un cajero solo puede entregar
 * montos que se formen con sus billetes.
 * Un valor null se considera valido: la obligatoriedad la controla @NotNull.
 */
@Documented
@Constraint(validatedBy = MultiploDeValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface MultiploDe {

    long value();

    String message() default "debe ser multiplo de {value}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
