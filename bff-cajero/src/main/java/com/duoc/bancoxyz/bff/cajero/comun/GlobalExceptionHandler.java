package com.duoc.bancoxyz.bff.cajero.comun;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import com.duoc.bancoxyz.bff.cajero.delegacion.DelegacionTokens;

/**
 * Errores del BFF con el mismo formato que los microservicios.
 * Una respuesta 4xx de un microservicio se devuelve al
 * canal con su mensaje, una caida (5xx, timeout, circuito abierto) se
 * transforma en 503.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DATOS_INVALIDOS = "Los datos enviados no son validos";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        List<String> globales = e.getBindingResult().getGlobalErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        List<String> todos = new ArrayList<>(detalles);
        todos.addAll(globales);
        return construir(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, todos, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validacionParametros(HandlerMethodValidationException e,
            HttpServletRequest req) {
        List<String> detalles = new ArrayList<>();
        for (ParameterValidationResult resultado : e.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors errores) {
                errores.getFieldErrors()
                        .forEach(error -> detalles.add(error.getField() + ": " + error.getDefaultMessage()));
            } else {
                String parametro = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors()
                        .forEach(error -> detalles.add(parametro + ": " + error.getDefaultMessage()));
            }
        }
        return construir(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, detalles, req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoIncorrecto(MethodArgumentTypeMismatchException e,
            HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS,
                List.of(e.getName() + ": el valor '" + e.getValue() + "' no es valido"), req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> jsonInvalido(HttpMessageNotReadableException e, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST,
                "El cuerpo de la solicitud no es un JSON valido o tiene campos/tipos no permitidos", List.of(), req);
    }

    @ExceptionHandler({ MissingRequestHeaderException.class, MissingServletRequestParameterException.class })
    public ResponseEntity<ErrorResponse> faltaParametro(Exception e, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> reglaNegocio(ReglaNegocioException e, HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException e, HttpServletRequest req) {
        log.warn("[SEGURIDAD] Acceso denegado en {}: {}", req.getRequestURI(), e.getMessage());
        return construir(HttpStatus.FORBIDDEN, e.getMessage(), List.of(), req);
    }

    @ExceptionHandler(DelegacionTokens.TokenNoDelegableException.class)
    public ResponseEntity<ErrorResponse> noDelegable(DelegacionTokens.TokenNoDelegableException e,
            HttpServletRequest req) {
        return construir(HttpStatus.FORBIDDEN, "Su usuario no esta habilitado para operar en este canal",
                List.of(e.getMessage()), req);
    }

    /** 4xx de un microservicio: se devuelve al canal con el mensaje original. */
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> errorDelServicio(HttpClientErrorException e, HttpServletRequest req) {
        HttpStatus estado = HttpStatus.resolve(e.getStatusCode().value());
        if (estado == null) {
            estado = HttpStatus.BAD_REQUEST;
        }
        if (estado == HttpStatus.UNAUTHORIZED) {
            // el token delegado no fue aceptado: es un problema interno, no del usuario
            log.error("Un microservicio rechazo el token delegado en {}: {}", req.getRequestURI(), e.getMessage());
            return construir(HttpStatus.BAD_GATEWAY,
                    "No fue posible autorizar la operacion con los servicios del banco",
                    List.of(), req);
        }
        String mensaje = estado.getReasonPhrase();
        List<String> detalles = List.of();
        try {
            ErrorResponse original = e.getResponseBodyAs(ErrorResponse.class);
            if (original != null && original.mensaje() != null) {
                mensaje = original.mensaje();
                detalles = original.detalles() == null ? List.of() : original.detalles();
            }
        } catch (RuntimeException ignorada) {
            // cuerpo no JSON: se usa el texto del estado
        }
        return construir(estado, mensaje, detalles, req);
    }

    /** Microservicio caido, lento o con su circuito abierto: 503 controlado. */
    @ExceptionHandler({ CallNotPermittedException.class, RestClientException.class,
            ServicioNoDisponibleException.class })
    public ResponseEntity<ErrorResponse> noDisponible(Exception e, HttpServletRequest req) {
        log.warn("Dependencia no disponible en {}: {}", req.getRequestURI(), e.toString());
        String mensaje = e instanceof ServicioNoDisponibleException ? e.getMessage()
                : "El servicio no esta disponible en este momento, intente nuevamente en unos segundos";
        return construir(HttpStatus.SERVICE_UNAVAILABLE, mensaje,
                List.of("Respuesta controlada por la politica de tolerancia a fallos (Resilience4j)"), req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException e, HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException e,
            HttpServletRequest req) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + e.getMethod() + " no esta permitido en esta ruta", List.of(), req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> general(Exception e, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), e);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error interno al procesar la solicitud",
                List.of(), req);
    }

    private static ResponseEntity<ErrorResponse> construir(HttpStatus estado, String mensaje, List<String> detalles,
            HttpServletRequest req) {
        return ResponseEntity.status(estado).body(new ErrorResponse(estado.value(), estado.name(), mensaje, detalles,
                req.getRequestURI(), OffsetDateTime.now()));
    }
}
