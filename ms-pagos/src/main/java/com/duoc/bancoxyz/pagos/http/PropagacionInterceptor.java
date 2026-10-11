package com.duoc.bancoxyz.pagos.http;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.duoc.bancoxyz.pagos.comun.CorrelationId;

/**
 * En cada llamada a otro microservicio:
 * 
 * Token delegado (token relay): reenvia el MISMO JWT que trajo el usuario, asi
 * el servicio llamado aplica sus propios permisos sobre la identidad original.
 * Propaga el X-Correlation-Id para seguir la operacion en los logs de todos los
 * servicios.
 * 
 */
public class PropagacionInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwt) {
            request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
        }
        request.getHeaders().set(CorrelationId.HEADER_HTTP, CorrelationId.actual());
        return execution.execute(request, body);
    }
}
