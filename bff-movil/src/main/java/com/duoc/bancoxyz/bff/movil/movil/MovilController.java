package com.duoc.bancoxyz.bff.movil.movil;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import com.duoc.bancoxyz.bff.movil.backend.ClientesApi;
import com.duoc.bancoxyz.bff.movil.backend.CuentasApi;
import com.duoc.bancoxyz.bff.movil.backend.Modelos.Cliente;
import com.duoc.bancoxyz.bff.movil.backend.Modelos.Cuenta;
import com.duoc.bancoxyz.bff.movil.backend.Modelos.Movimiento;
import com.duoc.bancoxyz.bff.movil.backend.Modelos.Notificacion;
import com.duoc.bancoxyz.bff.movil.backend.Modelos.Operacion;
import com.duoc.bancoxyz.bff.movil.backend.PagosApi;
import com.duoc.bancoxyz.bff.movil.comun.ReglaNegocioException;
import com.duoc.bancoxyz.bff.movil.delegacion.DelegacionTokens;
import com.duoc.bancoxyz.bff.movil.security.SecurityConfig;
import com.duoc.bancoxyz.bff.movil.security.UsuarioCanal;

/**
 * API del canal MOVIL. Respuestas livianas (ver {@link VistasMovil}), dispositivo
 * obligatorio en cada request y un tope por transferencia/pago propio del canal.
 */
@RestController
@RequestMapping("/api/movil")
public class MovilController {

    private static final Logger log = LoggerFactory.getLogger(MovilController.class);
    private static final String DISPOSITIVO = "^[A-Za-z0-9_-]{8,64}$";
    private static final String MENSAJE_DISPOSITIVO = "identificador de dispositivo invalido (8 a 64 caracteres)";
    private static final String CLAVE = "^[A-Za-z0-9_-]{8,100}$";
    private static final String MENSAJE_CLAVE = "debe tener entre 8 y 100 caracteres (letras, numeros, - o _)";
    private static final int TAMANO_PAGINA = 10;

    private final DelegacionTokens delegacion;
    private final ClientesApi clientes;
    private final CuentasApi cuentas;
    private final PagosApi pagos;
    private final BigDecimal limitePorOperacion;

    public MovilController(DelegacionTokens delegacion, ClientesApi clientes, CuentasApi cuentas, PagosApi pagos,
            @Value("${bff.movil.limite-por-operacion:500000}") BigDecimal limitePorOperacion) {
        this.delegacion = delegacion;
        this.clientes = clientes;
        this.cuentas = cuentas;
        this.pagos = pagos;
        this.limitePorOperacion = limitePorOperacion;
    }

    private String token(UsuarioCanal usuario, String dispositivo) {
        log.info("[MOVIL] Cliente {} desde dispositivo {}", usuario.clienteId(), dispositivo);
        return delegacion.paraUsuario(usuario.token()).valor();
    }

    /** Pantalla de inicio de la app: saludo, saldo disponible y cuentas en una sola respuesta corta. */
    @GetMapping("/inicio")
    public VistasMovil.Inicio inicio(
            @RequestHeader(SecurityConfig.HEADER_DISPOSITIVO) @Pattern(regexp = DISPOSITIVO, message = MENSAJE_DISPOSITIVO) String dispositivo) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario, dispositivo);
        List<Cuenta> lista;
        try {
            lista = cuentas.cuentasDe(token, usuario.clienteId());
        }
        catch (RuntimeException e) {
            if (e instanceof org.springframework.web.client.HttpClientErrorException h) {
                throw h;
            }
            // Fallback del canal: la app abre igual y muestra el aviso en vez de una pantalla de error
            log.warn("[RESILIENCIA] Saldos no disponibles para la app: {}", e.toString());
            return new VistasMovil.Inicio(saludo(token, usuario.clienteId()), null, null, null,
                    "Tus saldos no estan disponibles en este momento. Intenta en unos minutos.");
        }
        List<VistasMovil.CuentaBreve> breves = lista.stream()
                .filter(c -> !"CERRADA".equals(c.estado()))
                .map(c -> new VistasMovil.CuentaBreve(c.id(), enmascarar(c.id()), c.tipo(), c.saldo()))
                .toList();
        BigDecimal disponible = lista.stream()
                .filter(c -> c.activa() && !"PRESTAMO".equals(c.tipo()))
                .map(Cuenta::saldo).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VistasMovil.Inicio(saludo(token, usuario.clienteId()), disponible, breves,
                alertasRecientes(token, usuario.clienteId()), null);
    }

    @GetMapping("/cuentas/{id}/movimientos")
    public VistasMovil.PaginaMovimientos movimientos(
            @RequestHeader(SecurityConfig.HEADER_DISPOSITIVO) @Pattern(regexp = DISPOSITIVO, message = MENSAJE_DISPOSITIVO) String dispositivo,
            @PathVariable @Positive Long id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(9) int pagina) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario, dispositivo);
        cuentas.cuentaPropia(token, id, usuario.clienteId());
        int hasta = (pagina + 1) * TAMANO_PAGINA;
        List<Movimiento> todos = cuentas.movimientos(token, id, hasta + 1);
        List<VistasMovil.MovimientoBreve> items = todos.stream()
                .skip((long) pagina * TAMANO_PAGINA).limit(TAMANO_PAGINA)
                .map(m -> new VistasMovil.MovimientoBreve(m.fecha() == null ? null : m.fecha().toLocalDate(),
                        "DEBITO".equals(m.tipoMovimiento()) ? m.monto().negate() : m.monto(),
                        m.descripcion() == null ? m.tipoOperacion() : m.descripcion()))
                .toList();
        return new VistasMovil.PaginaMovimientos(pagina, todos.size() > hasta, items);
    }

    @PostMapping("/transferencias")
    public ResponseEntity<VistasMovil.EstadoOperacion> transferir(
            @RequestHeader(SecurityConfig.HEADER_DISPOSITIVO) @Pattern(regexp = DISPOSITIVO, message = MENSAJE_DISPOSITIVO) String dispositivo,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody SolicitudesMovil.Transferencia solicitud) {
        exigirLimite(solicitud.monto());
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario, dispositivo);
        cuentas.cuentaPropia(token, solicitud.cuentaOrigenId(), usuario.clienteId());
        return aceptada(pagos.verificarPropia(pagos.operar(token, "transferencias", clave, solicitud), usuario.clienteId(),
                solicitud.cuentaOrigenId()));
    }

    @PostMapping("/pagos")
    public ResponseEntity<VistasMovil.EstadoOperacion> pagar(
            @RequestHeader(SecurityConfig.HEADER_DISPOSITIVO) @Pattern(regexp = DISPOSITIVO, message = MENSAJE_DISPOSITIVO) String dispositivo,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = CLAVE, message = MENSAJE_CLAVE) String clave,
            @Valid @RequestBody SolicitudesMovil.Pago solicitud) {
        exigirLimite(solicitud.monto());
        UsuarioCanal usuario = UsuarioCanal.actual();
        String token = token(usuario, dispositivo);
        cuentas.cuentaPropia(token, solicitud.cuentaOrigenId(), usuario.clienteId());
        return aceptada(pagos.verificarPropia(pagos.operar(token, "pagos", clave, solicitud), usuario.clienteId(),
                solicitud.cuentaOrigenId()));
    }

    @GetMapping("/operaciones/{id}")
    public VistasMovil.EstadoOperacion operacion(
            @RequestHeader(SecurityConfig.HEADER_DISPOSITIVO) @Pattern(regexp = DISPOSITIVO, message = MENSAJE_DISPOSITIVO) String dispositivo,
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "debe ser un UUID") String id) {
        UsuarioCanal usuario = UsuarioCanal.actual();
        return breve(pagos.operacionPropia(token(usuario, dispositivo), id, usuario.clienteId()));
    }

    private void exigirLimite(BigDecimal monto) {
        if (monto != null && monto.compareTo(limitePorOperacion) > 0) {
            throw new ReglaNegocioException("Desde la app el maximo por operacion es $" + limitePorOperacion.toPlainString()
                    + ". Para montos mayores use el sitio web.");
        }
    }

    private String saludo(String token, Long clienteId) {
        try {
            Cliente cliente = clientes.cliente(token, clienteId);
            String nombre = cliente.nombre() == null ? "" : cliente.nombre().trim().split("\\s+")[0];
            return "Hola, " + nombre;
        }
        catch (RuntimeException e) {
            return "Hola"; // el saludo no es critico: si ms-clientes falla, se omite el nombre
        }
    }

    private Integer alertasRecientes(String token, Long clienteId) {
        try {
            OffsetDateTime desde = OffsetDateTime.now().minus(7, ChronoUnit.DAYS);
            List<Notificacion> alertas = clientes.notificaciones(token, clienteId, "ALERTA", 20);
            return (int) alertas.stream().filter(n -> n.fecha() != null && n.fecha().isAfter(desde)).count();
        }
        catch (RuntimeException e) {
            return null; // se omite del JSON (NON_NULL)
        }
    }

    private static String enmascarar(Long id) {
        String numero = String.valueOf(id);
        return "****" + numero.substring(Math.max(0, numero.length() - 4));
    }

    private static VistasMovil.EstadoOperacion breve(Operacion operacion) {
        return new VistasMovil.EstadoOperacion(operacion.id(), operacion.estado(), operacion.motivo());
    }

    private static ResponseEntity<VistasMovil.EstadoOperacion> aceptada(Operacion operacion) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/movil/operaciones/" + operacion.id())).body(breve(operacion));
    }
}
