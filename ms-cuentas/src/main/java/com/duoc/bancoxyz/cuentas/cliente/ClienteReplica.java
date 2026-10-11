package com.duoc.bancoxyz.cuentas.cliente;

/** Copia local de los datos minimos de un cliente. */
public record ClienteReplica(Long clienteId, String nombre, String estado) {

    public boolean activo() {
        return "ACTIVO".equals(estado);
    }
}
