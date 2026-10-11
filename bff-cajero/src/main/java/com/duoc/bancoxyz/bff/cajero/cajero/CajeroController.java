package com.duoc.bancoxyz.bff.cajero.cajero;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.bancoxyz.bff.cajero.security.TerminalCajeroFilter;
import com.duoc.bancoxyz.bff.cajero.security.UsuarioCanal;

/**
 * API del CAJERO AUTOMATICO: iniciar sesion (tarjeta + PIN), consultar saldo y
 * retirar.
 * Por HTTPS con certificado de cliente (mTLS).
 */
@RestController
@RequestMapping("/api/cajero")
public class CajeroController {

    private final CajeroService servicio;

    public CajeroController(CajeroService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/sesion")
    public ResponseEntity<Object> iniciarSesion(@Valid @RequestBody SolicitudesCajero.InicioSesion solicitud,
            HttpServletRequest request) {
        Object resultado = servicio.iniciarSesion(solicitud.numeroTarjeta(), solicitud.pin(), terminal(request));
        HttpStatus estado = resultado instanceof VistasCajero.Sesion ? HttpStatus.CREATED : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(estado).body(resultado);
    }

    @GetMapping("/saldo")
    public VistasCajero.Saldo saldo() {
        return servicio.saldo(UsuarioCanal.actual());
    }

    @PostMapping("/retiros")
    public VistasCajero.ResultadoRetiro retirar(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = ReglasCajero.PATRON_IDEMPOTENCY_KEY, message = ReglasCajero.MENSAJE_IDEMPOTENCY_KEY) String clave,
            @Valid @RequestBody SolicitudesCajero.Retiro solicitud, HttpServletRequest request) {
        return servicio.retirar(UsuarioCanal.actual(), solicitud.monto(), clave, terminal(request));
    }

    private static String terminal(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(TerminalCajeroFilter.ATRIBUTO_TERMINAL));
    }
}
