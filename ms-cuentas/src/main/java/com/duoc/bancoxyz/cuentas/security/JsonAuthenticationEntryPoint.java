package com.duoc.bancoxyz.cuentas.security;

import java.io.IOException;
import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 en JSON: falta el token o no es valido (firma, emisor, audiencia o expiracion). */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        boolean traeToken = request.getHeader(HttpHeaders.AUTHORIZATION) != null;
        String mensaje = traeToken ? "El token enviado no es valido o expiro"
                : "Se requiere un token OAuth 2.0 (Authorization: Bearer <token>) emitido por el auth-server";
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, traeToken ? "Bearer error=\"invalid_token\"" : "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().printf("{\"codigo\":401,\"estado\":\"UNAUTHORIZED\",\"mensaje\":\"%s\","
                + "\"detalles\":[],\"ruta\":\"%s\",\"fechaHora\":\"%s\"}", mensaje,
                request.getRequestURI().replace("\"", ""), OffsetDateTime.now());
    }
}
