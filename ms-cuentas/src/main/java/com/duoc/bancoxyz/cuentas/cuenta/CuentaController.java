package com.duoc.bancoxyz.cuentas.cuenta;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API de Gestion de Cuentas. Permisos (scopes) en SecurityConfig. */
@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    private final CuentaService servicio;

    public CuentaController(CuentaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{id}")
    public Cuenta obtener(@PathVariable @Positive Long id) {
        return servicio.obtener(id);
    }

    @GetMapping
    public List<Cuenta> listarPorCliente(@RequestParam @Positive Long clienteId) {
        return servicio.listarPorCliente(clienteId);
    }

    @GetMapping("/{id}/movimientos")
    public List<Movimiento> movimientos(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limite) {
        return servicio.movimientos(id, limite);
    }

    @PostMapping
    public ResponseEntity<Cuenta> abrir(@Valid @RequestBody AperturaCuentaRequest solicitud) {
        Cuenta cuenta = servicio.abrir(solicitud);
        return ResponseEntity.created(URI.create("/api/cuentas/" + cuenta.id())).body(cuenta);
    }

    @PatchMapping("/{id}/bloqueo")
    public Cuenta bloquear(@PathVariable @Positive Long id, @Valid @RequestBody MotivoRequest solicitud) {
        return servicio.bloquear(id, solicitud.motivo());
    }

    @PatchMapping("/{id}/desbloqueo")
    public Cuenta desbloquear(@PathVariable @Positive Long id) {
        return servicio.desbloquear(id);
    }

    @PatchMapping("/{id}/cierre")
    public Cuenta cerrar(@PathVariable @Positive Long id, @Valid @RequestBody MotivoRequest solicitud) {
        return servicio.cerrar(id, solicitud.motivo());
    }
}
