package com.duoc.bancoxyz.clientes.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MotivoRequest(@NotBlank(message = "es obligatorio") @Size(max = 200, message = "maximo 200 caracteres") String motivo) {
}
