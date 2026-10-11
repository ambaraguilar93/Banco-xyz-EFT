package com.duoc.bancoxyz.cuentas.comun;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/** Manejo centralizado de errores: toda respuesta de error usa {@link ErrorResponse}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String DATOS_INVALIDOS = "Los datos enviados no son validos";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> detalles = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return construir(HttpStatus.BAD_REQUEST, DATOS_INVALIDOS, detalles, req);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validacionParametros(HandlerMethodValidationException e,
            HttpServletRequest req) {
        List<String> detalles = new ArrayList<>();
        for (ParameterValidationResult resultado : e.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors errores) {
                errores.getFieldErrors()
                        .forEach(error -> detalles.add(error.getField() + ": " + error.getDefaultMessage()));
            }
            else {
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

    /** Circuit Breaker abierto o BD caida: 503 controlado en vez de un error 500. */
    @ExceptionHandler({ CallNotPermittedException.class, DataAccessException.class,
            ServicioNoDisponibleException.class })
    public ResponseEntity<ErrorResponse> noDisponible(Exception e, HttpServletRequest req) {
        log.warn("Dependencia no disponible en {}: {}", req.getRequestURI(), e.toString());
        String mensaje = e instanceof ServicioNoDisponibleException ? e.getMessage()
                : "El servicio no esta disponible en este momento, intente nuevamente en unos segundos";
        return construir(HttpStatus.SERVICE_UNAVAILABLE, mensaje,
                List.of("Respuesta controlada por la politica de tolerancia a fallos (Resilience4j)"), req);
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
