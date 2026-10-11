package com.duoc.bancoxyz.clientes.cliente;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Registro de un cliente nuevo. */
public record ClienteRequest(
        @NotBlank(message = "es obligatorio") String rut,
        @NotBlank(message = "es obligatorio") @Size(min = 3, max = 150, message = "debe tener entre 3 y 150 caracteres") String nombre,
        @NotBlank(message = "es obligatorio") @Email(message = "no es un correo valido") @Size(max = 150, message = "maximo 150 caracteres") String email,
        @Pattern(regexp = "^\\+?\\d{8,12}$", message = "debe tener entre 8 y 12 digitos (ej. +56912345678)") String telefono,
        @Size(max = 200, message = "maximo 200 caracteres") String direccion) {

    @AssertTrue(message = "rut: debe tener formato 12345678-9 y un digito verificador valido")
    public boolean isRutValido() {
        return rut == null || rut.isBlank() || Rut.esValido(rut);
    }
}
