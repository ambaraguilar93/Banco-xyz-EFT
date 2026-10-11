package com.duoc.bancoxyz.cuentas.cuenta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de un bloqueo o cierre (queda registrado en la cuenta y en el evento). */
public record MotivoRequest(@NotBlank(message = "es obligatorio") @Size(max = 200, message = "maximo 200 caracteres") String motivo) {
}
