package com.duoc.bancoxyz.clientes.perfil;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.duoc.bancoxyz.clientes.cliente.Cliente;
import com.duoc.bancoxyz.clientes.cliente.ClienteService;
import com.duoc.bancoxyz.clientes.cuentas.CuentaResumen;
import com.duoc.bancoxyz.clientes.cuentas.CuentasClient;
import com.duoc.bancoxyz.clientes.notificacion.NotificacionRepository;

@Service
public class PerfilService {

    private final ClienteService clientes;
    private final CuentasClient cuentasClient;
    private final NotificacionRepository notificaciones;

    public PerfilService(ClienteService clientes, CuentasClient cuentasClient, NotificacionRepository notificaciones) {
        this.clientes = clientes;
        this.cuentasClient = cuentasClient;
        this.notificaciones = notificaciones;
    }

    public PerfilCliente perfil(Long clienteId) {
        Cliente cliente = clientes.obtener(clienteId);
        CuentasClient.Resultado cuentas = cuentasClient.cuentasDe(clienteId);
        BigDecimal saldoTotal = cuentas.cuentas().stream()
                .filter(c -> !"PRESTAMO".equals(c.tipo()) && !"CERRADA".equals(c.estado()))
                .map(CuentaResumen::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PerfilCliente(cliente, cuentas.disponible(), cuentas.cuentas(), saldoTotal,
                notificaciones.alertasUltimos30Dias(clienteId), notificaciones.listar(clienteId, null, 10));
    }
}
