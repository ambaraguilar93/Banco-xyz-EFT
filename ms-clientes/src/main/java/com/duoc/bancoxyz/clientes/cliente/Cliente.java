package com.duoc.bancoxyz.clientes.cliente;

import java.time.OffsetDateTime;

/** Cliente del banco. estado: ACTIVO o INACTIVO. Los clientes migrados del legacy no traen RUT. */
public record Cliente(Long id, String rut, String nombre, String email, String telefono, String direccion,
        String estado, String motivoEstado, OffsetDateTime creadoEn, OffsetDateTime actualizadoEn) {

    public static final String ACTIVO = "ACTIVO";
    public static final String INACTIVO = "INACTIVO";
}
