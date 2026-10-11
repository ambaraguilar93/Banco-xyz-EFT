package com.duoc.bancoxyz.clientes.cliente;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.bancoxyz.clientes.notificacion.Notificacion;
import com.duoc.bancoxyz.clientes.notificacion.NotificacionRepository;
import com.duoc.bancoxyz.clientes.perfil.PerfilCliente;
import com.duoc.bancoxyz.clientes.perfil.PerfilService;

/** API de Gestion de Clientes. Permisos (scopes) en SecurityConfig. */
@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    private final ClienteService servicio;
    private final PerfilService perfiles;
    private final NotificacionRepository notificaciones;

    public ClienteController(ClienteService servicio, PerfilService perfiles, NotificacionRepository notificaciones) {
        this.servicio = servicio;
        this.perfiles = perfiles;
        this.notificaciones = notificaciones;
    }

    @GetMapping
    public List<Cliente> listar(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite) {
        return servicio.listar(limite);
    }

    @GetMapping("/{id}")
    public Cliente obtener(@PathVariable @Positive Long id) {
        return servicio.obtener(id);
    }

    /** Vista 360: datos + cuentas (ms-cuentas, con fallback) + actividad reciente. */
    @GetMapping("/{id}/perfil")
    public PerfilCliente perfil(@PathVariable @Positive Long id) {
        return perfiles.perfil(id);
    }

    @GetMapping("/{id}/notificaciones")
    public List<Notificacion> notificaciones(@PathVariable @Positive Long id,
            @RequestParam(required = false) @Pattern(regexp = "TRANSACCION|CUENTA|ALERTA") String categoria,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limite) {
        servicio.obtener(id);
        return notificaciones.listar(id, categoria, limite);
    }

    @PostMapping
    public ResponseEntity<Cliente> registrar(@Valid @RequestBody ClienteRequest solicitud) {
        Cliente cliente = servicio.registrar(solicitud);
        return ResponseEntity.created(URI.create("/api/clientes/" + cliente.id())).body(cliente);
    }

    @PutMapping("/{id}")
    public Cliente actualizar(@PathVariable @Positive Long id, @Valid @RequestBody ActualizacionClienteRequest solicitud) {
        return servicio.actualizar(id, solicitud);
    }

    @PatchMapping("/{id}/desactivacion")
    public Cliente desactivar(@PathVariable @Positive Long id, @Valid @RequestBody MotivoRequest solicitud) {
        return servicio.desactivar(id, solicitud.motivo());
    }

    @PatchMapping("/{id}/reactivacion")
    public Cliente reactivar(@PathVariable @Positive Long id) {
        return servicio.reactivar(id);
    }
}
