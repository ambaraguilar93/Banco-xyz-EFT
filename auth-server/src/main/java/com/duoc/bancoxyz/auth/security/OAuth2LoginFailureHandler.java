package com.duoc.bancoxyz.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Si el login con GitHub falla se responde un 401 en JSON con el mismo formato
 * de error que usan los microservicios.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

    private final JsonMapper jsonMapper;

    public OAuth2LoginFailureHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("[AUTH] Login con GitHub fallido: {}", exception.getMessage());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", 401);
        cuerpo.put("estado", "UNAUTHORIZED");
        cuerpo.put("mensaje", "No fue posible autenticar con GitHub");
        cuerpo.put("detalles", List.of(String.valueOf(exception.getMessage())));
        cuerpo.put("ruta", request.getRequestURI());
        cuerpo.put("fechaHora", OffsetDateTime.now().toString());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
