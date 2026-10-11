package com.duoc.bancoxyz.cuentas.security;

import java.io.IOException;
import java.time.OffsetDateTime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 en JSON: el token es valido pero no trae el scope que exige el endpoint. */
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader("WWW-Authenticate", "Bearer error=\"insufficient_scope\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().printf("{\"codigo\":403,\"estado\":\"FORBIDDEN\","
                + "\"mensaje\":\"El token no tiene el permiso (scope) que exige este recurso\","
                + "\"detalles\":[],\"ruta\":\"%s\",\"fechaHora\":\"%s\"}",
                request.getRequestURI().replace("\"", ""), OffsetDateTime.now());
    }
}
