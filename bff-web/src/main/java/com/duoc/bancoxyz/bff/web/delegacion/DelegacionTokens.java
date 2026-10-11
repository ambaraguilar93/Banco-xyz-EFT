package com.duoc.bancoxyz.bff.web.delegacion;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import com.duoc.bancoxyz.bff.web.comun.ServicioNoDisponibleException;

/**
 * TOKENS DELEGADOS: este BFF se autentica ante el auth-server como cliente
 * OAuth 2.0 confidencial (client_id + secret propios del canal) y obtiene los
 * tokens con los que llama a los microservicios.
 *
 * Los tokens se guardan en memoria hasta poco antes de vencer.
 * Circuit Breaker "authServer": si el auth-server no responde,
 * el BFF contesta 503 en vez de quedar esperando.
 */
@Component
public class DelegacionTokens {

    private static final Logger log = LoggerFactory.getLogger(DelegacionTokens.class);
    private static final String TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";

    /** Respuesta del endpoint /oauth2/token. */
    record RespuestaToken(String access_token, Long expires_in, String scope) {
    }

    private final RestClient restClient;
    private final String clientId;
    private final String secret;
    private final Map<String, TokenDelegado> cache = new ConcurrentHashMap<>();

    public DelegacionTokens(@LoadBalanced RestClient.Builder builder,
            @Value("${bff.oauth.token-uri:http://auth-server/oauth2/token}") String tokenUri,
            @Value("${bff.oauth.client-id}") String clientId,
            @Value("${bff.oauth.client-secret}") String secret) {
        this.restClient = builder.baseUrl(tokenUri).build();
        this.clientId = clientId;
        this.secret = secret;
    }

    /** Token del usuario (login) -> token delegado del canal (token-exchange). */
    @CircuitBreaker(name = "authServer")
    public TokenDelegado paraUsuario(String tokenUsuario) {
        TokenDelegado enCache = cache.get(tokenUsuario);
        if (enCache != null && enCache.vigente()) {
            return enCache;
        }
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", TOKEN_EXCHANGE);
        formulario.add("subject_token", tokenUsuario);
        formulario.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        TokenDelegado token = pedir(formulario);
        limpiarVencidos();
        cache.put(tokenUsuario, token);
        return token;
    }

    /** Token propio del BFF (client_credentials sin usuario). */
    @CircuitBreaker(name = "authServer")
    public TokenDelegado deServicio() {
        TokenDelegado enCache = cache.get("__servicio__");
        if (enCache != null && enCache.vigente()) {
            return enCache;
        }
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "client_credentials");
        TokenDelegado token = pedir(formulario);
        cache.put("__servicio__", token);
        return token;
    }

    /**
     * Token a nombre de un cliente ya autenticado por el BFF (sesion del cajero).
     */
    @CircuitBreaker(name = "authServer")
    public TokenDelegado aNombreDe(Long clienteId, Long cuentaId, String terminal) {
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "client_credentials");
        formulario.add("cliente_id", String.valueOf(clienteId));
        formulario.add("cuenta_id", String.valueOf(cuentaId));
        if (terminal != null) {
            formulario.add("terminal_id", terminal);
        }
        return pedir(formulario);
    }

    private TokenDelegado pedir(MultiValueMap<String, String> formulario) {
        try {
            RespuestaToken respuesta = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .headers(h -> h.setBasicAuth(clientId, secret))
                    .body(formulario)
                    .retrieve()
                    .body(RespuestaToken.class);
            if (respuesta == null || respuesta.access_token() == null) {
                throw new ServicioNoDisponibleException("El servidor de autorizacion no entrego un token");
            }
            long vida = respuesta.expires_in() == null ? 60 : respuesta.expires_in();
            log.debug("[DELEGACION] Token obtenido para {} (scopes [{}], {} s)", clientId, respuesta.scope(), vida);
            return new TokenDelegado(respuesta.access_token(), Instant.now().plusSeconds(vida), respuesta.scope());
        } catch (HttpClientErrorException.Unauthorized e) {
            // invalid_client: el secreto de este BFF no coincide con el del auth-server
            log.error("[DELEGACION] El auth-server rechazo las credenciales de {}: revise bff.oauth.client-secret",
                    clientId);
            throw new ServicioNoDisponibleException(
                    "No fue posible autorizar el canal ante el servidor de autorizacion");
        } catch (HttpClientErrorException e) {
            // 400/401 del auth-server: token de usuario no delegable o credenciales del BFF
            // malas
            log.warn("[DELEGACION] El auth-server rechazo la solicitud: {}", e.getResponseBodyAsString());
            throw new TokenNoDelegableException(e.getResponseBodyAsString());
        }
    }

    private void limpiarVencidos() {
        if (cache.size() > 500) {
            cache.entrySet().removeIf(e -> !e.getValue().vigente());
        }
    }

    /**
     * El auth-server no quiso emitir el token (usuario sin cliente asociado, token
     * ajeno, etc.).
     */
    public static class TokenNoDelegableException extends RuntimeException {
        public TokenNoDelegableException(String detalle) {
            super(detalle);
        }
    }
}
