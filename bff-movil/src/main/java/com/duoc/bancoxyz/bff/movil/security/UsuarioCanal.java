package com.duoc.bancoxyz.bff.movil.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Datos del usuario autenticado en este canal (desde el JWT ya validado). */
public record UsuarioCanal(String sujeto, Long clienteId, String token, Jwt jwt) {

    public static UsuarioCanal actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacion instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("No hay un usuario autenticado con JWT");
        }
        Jwt jwt = jwtAuth.getToken();
        Object cliente = jwt.getClaim("clienteId");
        Long clienteId = cliente instanceof Number n ? n.longValue() : null;
        return new UsuarioCanal(jwt.getSubject(), clienteId, jwt.getTokenValue(), jwt);
    }
}
