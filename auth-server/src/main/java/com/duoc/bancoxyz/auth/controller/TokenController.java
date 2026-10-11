package com.duoc.bancoxyz.auth.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.bancoxyz.auth.service.ClientesBffProperties;
import com.duoc.bancoxyz.auth.service.TokenDelegadoService;
import com.duoc.bancoxyz.auth.service.TokenDelegadoService.ErrorOAuth;

/**
 * Endpoint de token OAuth 2.0 para los BFF (clientes confidenciales).
 *
 * POST /oauth2/token
 */
@RestController
public class TokenController {

    private final TokenDelegadoService servicio;

    public TokenController(TokenDelegadoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "subject_token", required = false) String subjectToken,
            @RequestParam(value = "cliente_id", required = false) Long clienteId,
            @RequestParam(value = "cuenta_id", required = false) Long cuentaId,
            @RequestParam(value = "terminal_id", required = false) String terminalId) {

        String[] credenciales = basic(authorization);
        ClientesBffProperties.ClienteBff cliente = servicio.autenticar(credenciales[0], credenciales[1]);

        TokenDelegadoService.TokenDelegado token = switch (grantType) {
            case TokenDelegadoService.GRANT_TOKEN_EXCHANGE ->
                servicio.intercambiar(credenciales[0], cliente, subjectToken);
            case TokenDelegadoService.GRANT_CLIENT_CREDENTIALS ->
                servicio.credencialesCliente(credenciales[0], cliente, clienteId, cuentaId, terminalId);
            default -> throw new ErrorOAuth(400, "unsupported_grant_type", "grant_type no soportado: " + grantType);
        };

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("access_token", token.accessToken());
        cuerpo.put("token_type", "Bearer");
        cuerpo.put("expires_in", token.expiresIn());
        cuerpo.put("scope", token.scope());
        cuerpo.put("issued_token_type", "urn:ietf:params:oauth:token-type:access_token");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(cuerpo);
    }

    @ExceptionHandler(ErrorOAuth.class)
    public ResponseEntity<Map<String, String>> error(ErrorOAuth e) {
        return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", e.codigo(), "error_description", e.getMessage()));
    }

    private static String[] basic(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            throw new ErrorOAuth(401, "invalid_client", "Falta la autenticacion del cliente (HTTP Basic)");
        }
        try {
            String decodificado = new String(Base64.getDecoder().decode(authorization.substring(6).trim()),
                    StandardCharsets.UTF_8);
            int separador = decodificado.indexOf(':');
            if (separador < 0) {
                throw new IllegalArgumentException();
            }
            return new String[] { decodificado.substring(0, separador), decodificado.substring(separador + 1) };
        } catch (IllegalArgumentException e) {
            throw new ErrorOAuth(401, "invalid_client", "Autenticacion Basic mal formada");
        }
    }
}
