package com.duoc.bancoxyz.bff.web.web;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.duoc.bancoxyz.bff.web.backend.ClientesApi;
import com.duoc.bancoxyz.bff.web.backend.CuentasApi;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Cliente;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Cuenta;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Movimiento;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Notificacion;
import com.duoc.bancoxyz.bff.web.comun.Paralelo;
import com.duoc.bancoxyz.bff.web.comun.ServicioNoDisponibleException;

/**
 * Arma el panel web en una llamada del navegador (antes eran 3 + una por
 * cuenta).
 * Optimizacion: las consultas a ms-clientes y ms-cuentas van EN PARALELO, y los
 * movimientos de todas las cuentas tambien.
 * Resiliencia: cada seccion que falla se informa en "disponibilidad" sin botar
 * el panel.
 */
@Service
public class PanelWebService {

    private static final Logger log = LoggerFactory.getLogger(PanelWebService.class);

    private final ClientesApi clientes;
    private final CuentasApi cuentas;
    private final int movimientosPorCuenta;

    public PanelWebService(ClientesApi clientes, CuentasApi cuentas,
            @Value("${bff.web.movimientos-por-cuenta:5}") int movimientosPorCuenta) {
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.movimientosPorCuenta = movimientosPorCuenta;
    }

    public VistasWeb.Inicio inicio(String token, Long clienteId) {
        CompletableFuture<Cliente> cliente = Paralelo.ejecutar(() -> clientes.cliente(token, clienteId));
        CompletableFuture<List<Cuenta>> listaCuentas = Paralelo.ejecutar(() -> cuentas.cuentasDe(token, clienteId));
        CompletableFuture<List<Notificacion>> notificaciones = Paralelo
                .ejecutar(() -> clientes.notificaciones(token, clienteId, null, 10));

        Cliente datosCliente = resultado(cliente, "ms-clientes (datos)");
        List<Cuenta> lista = resultado(listaCuentas, "ms-cuentas");
        List<Notificacion> avisos = resultado(notificaciones, "ms-clientes (notificaciones)");
        if (datosCliente == null && lista == null) {
            throw new ServicioNoDisponibleException("Los servicios del banco no estan disponibles, intente mas tarde");
        }

        // movimientos de todas las cuentas, en paralelo
        List<CompletableFuture<VistasWeb.CuentaConMovimientos>> pendientes = lista == null ? List.of()
                : lista.stream().map(c -> Paralelo.ejecutar(() -> new VistasWeb.CuentaConMovimientos(c,
                        cuentas.movimientos(token, c.id(), movimientosPorCuenta)))).toList();
        boolean movimientosOk = true;
        List<VistasWeb.CuentaConMovimientos> conMovimientos = new java.util.ArrayList<>();
        for (int i = 0; i < pendientes.size(); i++) {
            VistasWeb.CuentaConMovimientos cm = resultado(pendientes.get(i), "movimientos");
            if (cm == null) {
                movimientosOk = false;
                cm = new VistasWeb.CuentaConMovimientos(lista.get(i), List.<Movimiento>of());
            }
            conMovimientos.add(cm);
        }

        BigDecimal saldo = sumar(lista, false);
        BigDecimal deuda = sumar(lista, true);
        OffsetDateTime haceUnaSemana = OffsetDateTime.now().minus(7, ChronoUnit.DAYS);
        long alertas = avisos == null ? 0
                : avisos.stream()
                        .filter(n -> "ALERTA".equals(n.categoria()) && n.fecha() != null
                                && n.fecha().isAfter(haceUnaSemana))
                        .count();
        return new VistasWeb.Inicio(datosCliente, conMovimientos, saldo, deuda, avisos == null ? List.of() : avisos,
                alertas,
                new VistasWeb.Disponibilidad(datosCliente != null, lista != null, movimientosOk, avisos != null),
                OffsetDateTime.now());
    }

    private static BigDecimal sumar(List<Cuenta> lista, boolean prestamos) {
        if (lista == null) {
            return null;
        }
        return lista.stream()
                .filter(c -> !"CERRADA".equals(c.estado()) && "PRESTAMO".equals(c.tipo()) == prestamos)
                .map(Cuenta::saldo)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Espera el resultado; si fallo, deja registro y devuelve null (seccion no
     * disponible).
     */
    private static <T> T resultado(CompletableFuture<T> futuro, String seccion) {
        try {
            return futuro.join();
        } catch (CompletionException e) {
            Throwable causa = e.getCause() == null ? e : e.getCause();
            if (causa instanceof org.springframework.web.client.HttpClientErrorException.NotFound) {
                throw (RuntimeException) causa;
            }
            log.warn("[RESILIENCIA] Seccion '{}' no disponible: {}", seccion, causa.toString());
            return null;
        }
    }
}
