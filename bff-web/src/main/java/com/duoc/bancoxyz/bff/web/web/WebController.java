package com.duoc.bancoxyz.bff.web.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.bancoxyz.bff.web.backend.ClientesApi;
import com.duoc.bancoxyz.bff.web.backend.CuentasApi;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Cliente;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Cuenta;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Movimiento;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Notificacion;
import com.duoc.bancoxyz.bff.web.backend.Modelos.Operacion;
import com.duoc.bancoxyz.bff.web.backend.PagosApi;
import com.duoc.bancoxyz.bff.web.delegacion.DelegacionTokens;
import com.duoc.bancoxyz.bff.web.security.UsuarioCanal;

/**
 * API del canal WEB (navegador). Siempre opera sobre el cliente del token: el usuario
 * nunca indica "de que cliente" quiere ver datos, y cada cuenta u operacion se verifica
 * contra ese cliente antes de responder.
 */
@RestController
@RequestMapping("/api/web")
public class WebController {

    private static final String CLAVE = "^[A-Za-z0-9_-]{8,100}$";
    private static final String MENSAJE_CLAVE = "debe tener entre 8 y 100 caracteres (letras, numeros, - o _)";

    private final DelegacionTokens delegacion;
    private final PanelWebService panel;
    private final ClientesApi clientes;
    private final CuentasApi cuentas;
    private final PagosApi pagos;

    public WebController(DelegacionTokens delegacion, PanelWebService panel, ClientesApi clientes, CuentasApi cuentas,
            PagosApi pagos) {
        this.delegacion = delegacion;
        this.panel = panel;
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.pagos = pagos;
    }

    /** Token delegado del canal web para el usuario actual. */
    private String token(UsuarioCanal usuario) {
        return delegacion.paraUsuario(usuario.token()).valor();
    }

    @GetMapping("/inicio")
    public VistasWeb.Inicio inicio() {
        UsuarioCanal usuario = UsuarioCanal.actual();
        return panel.inicio(token(usuario), usuario.clienteId());
    }

    @GetMapping("/perfil")
    public Cliente perfil() {
        UsuarioCanal usuario = UsuarioCanal.actual();
        return clientes.cliente(token(usuario), usuario.clienteId());
    }

    @PutMapping("/perfil")
    public Cliente actualizarPerfil(@Valid @RequestBody SolicitudesWeb.DatosPersonales datos) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", datos.nombre());
        cuerpo.put("email", datos.email());
        cuerpo.put("telefono", datos.telefono());
        cuerpo.put("direccion", datos.direccion());
        return clientes.actualizar(token(usuario), usuario.clienteId(), cuerpo);
    }

    @GetMapping("/cuentas/{id}")
    public Cuenta cuenta(@PathVariable @Positive Long id) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        return cuentas.cuentaPropia(token(usuario), id, usuario.clienteId());
    }

    @GetMapping("/cuentas/{id}/movimientos")
    public List<Movimiento> movimientos(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario);
        cuentas.cuentaPropia(token, id, usuario.clienteId());
        return cuentas.movimientos(token, id, limite);
    }

    @GetMapping("/cuentas/{id}/operaciones")
    public List<Operacion> operacionesDeCuenta(@PathVariable @Positive Long id,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limite) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario);
        cuentas.cuentaPropia(token, id, usuario.clienteId());
        return pagos.operacionesDeCuenta(token, id, limite);
    }

    @PostMapping("/cuentas")
    public ResponseEntity<Cuenta> abrirCuenta(@Valid @RequestBody SolicitudesWeb.AperturaCuenta solicitud) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("clienteId", usuario.clienteId());
        cuerpo.put("tipo", solicitud.tipo());
        cuerpo.put("saldoInicial", solicitud.saldoInicial());
        Cuenta cuenta = cuentas.abrir(token(usuario), cuerpo);
        return ResponseEntity.created(URI.create("/api/web/cuentas/" + cuenta.id())).body(cuenta);
    }

    @PostMapping("/pagos")
    public ResponseEntity<Operacion> pagar(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody SolicitudesWeb.Pago solicitud) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario);
        cuentas.cuentaPropia(token, solicitud.cuentaOrigenId(), usuario.clienteId());
        return aceptada(pagos.verificarPropia(pagos.operar(token, "pagos", clave, solicitud), usuario.clienteId(),
                solicitud.cuentaOrigenId()));
    }

    @PostMapping("/transferencias")
    public ResponseEntity<Operacion> transferir(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody SolicitudesWeb.Transferencia solicitud) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario);
        cuentas.cuentaPropia(token, solicitud.cuentaOrigenId(), usuario.clienteId());
        return aceptada(pagos.verificarPropia(pagos.operar(token, "transferencias", clave, solicitud), usuario.clienteId(),
                solicitud.cuentaOrigenId()));
    }

    @GetMapping("/operaciones/{id}")
    public Operacion operacion(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "debe ser un UUID") String id) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        return pagos.operacionPropia(token(usuario), id, usuario.clienteId());
    }

    @GetMapping("/notificaciones")
    public List<Notificacion> notificaciones(
            @RequestParam(required = false) @Pattern(regexp = "TRANSACCION|CUENTA|ALERTA") String categoria,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        return clientes.notificaciones(token(usuario), usuario.clienteId(), categoria, limite);
    }

    private static ResponseEntity<Operacion> aceptada(Operacion operacion) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/web/operaciones/" + operacion.id())).body(operacion);
    }
}
