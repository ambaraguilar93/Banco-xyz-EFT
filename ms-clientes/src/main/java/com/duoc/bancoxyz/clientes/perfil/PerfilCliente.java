package com.duoc.bancoxyz.clientes.perfil;

import java.math.BigDecimal;
import java.util.List;

import com.duoc.bancoxyz.clientes.cliente.Cliente;
import com.duoc.bancoxyz.clientes.cuentas.CuentaResumen;
import com.duoc.bancoxyz.clientes.notificacion.Notificacion;

/**
 * Vista 360 del cliente: datos personales + cuentas (de ms-cuentas) + historial reciente y
 * alertas. Si ms-cuentas no responde, cuentasDisponibles=false y la lista viene vacia.
 */
public record PerfilCliente(Cliente cliente, boolean cuentasDisponibles, List<CuentaResumen> cuentas,
        BigDecimal saldoTotal, int alertasUltimos30Dias, List<Notificacion> actividadReciente) {
}
