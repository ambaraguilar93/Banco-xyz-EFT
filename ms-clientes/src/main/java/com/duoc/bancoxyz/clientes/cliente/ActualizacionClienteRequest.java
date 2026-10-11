package com.duoc.bancoxyz.clientes.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Actualizacion de datos personales (el RUT no se modifica). */
public record ActualizacionClienteRequest(
        @NotBlank(message = "es obligatorio") @Size(min = 3, max = 150, message = "debe tener entre 3 y 150 caracteres") String nombre,
        @NotBlank(message = "es obligatorio") @Email(message = "no es un correo valido") @Size(max = 150, message = "maximo 150 caracteres") String email,
        @Pattern(regexp = "^\\+?\\d{8,12}$", message = "debe tener entre 8 y 12 digitos (ej. +56912345678)") String telefono,
        @Size(max = 200, message = "maximo 200 caracteres") String direccion) {
}
