package com.duoc.bancoxyz.pagos.operacion;

import java.net.URI;
import java.util.List;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import com.duoc.bancoxyz.pagos.comun.ReglaNegocioException;
import com.duoc.bancoxyz.pagos.operacion.Solicitudes.DepositoRequest;
import com.duoc.bancoxyz.pagos.operacion.Solicitudes.PagoRequest;
import com.duoc.bancoxyz.pagos.operacion.Solicitudes.RetiroRequest;
import com.duoc.bancoxyz.pagos.operacion.Solicitudes.TransferenciaRequest;

/**
 * API de Procesamiento de Pagos. Los POST responden 202 Accepted con la operacion PENDIENTE
 * (o 200 si la Idempotency-Key ya se habia usado); el resultado se consulta con GET.
 */
@RestController
@RequestMapping("/api")
public class OperacionController {

    private static final String CLAVE = "^[A-Za-z0-9_-]{8,100}$";
    private static final String MENSAJE_CLAVE = "debe tener entre 8 y 100 caracteres (letras, numeros, - o _)";

    private final OperacionService servicio;

    public OperacionController(OperacionService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/pagos")
    public ResponseEntity<Operacion> pagar(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody PagoRequest solicitud) {
        return responder(servicio.registrar("PAGO", solicitud.cuentaOrigenId(), null, solicitud.monto(),
                solicitud.descripcion(), clave));
    }

    /** EFT Parte 2: retiro en cajero automatico (debita la cuenta, igual que un pago). */
    @PostMapping("/retiros")
    public ResponseEntity<Operacion> retirar(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody RetiroRequest solicitud, @AuthenticationPrincipal Jwt jwt) {
        // Defensa en profundidad: el token de sesion del cajero viene ligado a UNA cuenta
        Object cuentaSesion = jwt == null ? null : jwt.getClaim("cuentaId");
        if (!(cuentaSesion instanceof Number n) || n.longValue() != solicitud.cuentaOrigenId()) {
            throw new ReglaNegocioException("El retiro solo puede hacerse desde la cuenta de la tarjeta de la sesion");
        }
        return responder(servicio.registrar("RETIRO", solicitud.cuentaOrigenId(), null, solicitud.monto(),
                solicitud.descripcion() == null ? "Retiro en cajero" : solicitud.descripcion(), clave));
    }

    @PostMapping("/transferencias")
    public ResponseEntity<Operacion> transferir(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody TransferenciaRequest solicitud) {
        return responder(servicio.registrar("TRANSFERENCIA", solicitud.cuentaOrigenId(), solicitud.cuentaDestinoId(),
                solicitud.monto(), solicitud.descripcion(), clave));
    }

    @PostMapping("/depositos")
    public ResponseEntity<Operacion> depositar(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody DepositoRequest solicitud) {
        return responder(servicio.registrar("DEPOSITO", null, solicitud.cuentaDestinoId(), solicitud.monto(),
                solicitud.descripcion(), clave));
    }

    @GetMapping("/operaciones/{id}")
    public Operacion obtener(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "debe ser un UUID") String id) {
        return servicio.obtener(id);
    }

    @GetMapping("/operaciones")
    public List<Operacion> listarPorCuenta(@RequestParam @Positive Long cuentaId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int limite) {
        return servicio.listarPorCuenta(cuentaId, limite);
    }

    private static ResponseEntity<Operacion> responder(OperacionService.Registro registro) {
        Operacion op = registro.operacion();
        return ResponseEntity.status(registro.nueva() ? HttpStatus.ACCEPTED : HttpStatus.OK)
                .location(URI.create("/api/operaciones/" + op.id()))
                .header("X-Idempotency-Replayed", String.valueOf(!registro.nueva()))
                .body(op);
    }
}
