package com.duoc.bancoxyz.auth.security;

import com.duoc.bancoxyz.auth.service.ClientesGithub;
import com.duoc.bancoxyz.auth.service.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Se ejecuta cuando GitHub confirma la identidad del usuario.
 * Responde en JSON el JWT propio de Banco XYZ para
 * "Authorization: Bearer <accessToken>" en los microservicios.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenService jwtTokenService;
    private final JsonMapper jsonMapper;
    private final ClientesGithub clientesGithub;

    public OAuth2LoginSuccessHandler(JwtTokenService jwtTokenService, JsonMapper jsonMapper,
            ClientesGithub clientesGithub) {
        this.jwtTokenService = jwtTokenService;
        this.jsonMapper = jsonMapper;
        this.clientesGithub = clientesGithub;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User usuarioGithub = (OAuth2User) authentication.getPrincipal();
        String login = usuarioGithub.getAttribute("login");
        String email = usuarioGithub.getAttribute("email");
        String nombre = usuarioGithub.getAttribute("name");

        Long clienteId = clientesGithub.clienteDe(login);
        JwtTokenService.TokenEmitido token = jwtTokenService.emitir(login, email, nombre, clienteId);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("tokenType", "Bearer");
        cuerpo.put("accessToken", token.accessToken());
        cuerpo.put("expiresIn", token.expiresIn());
        cuerpo.put("scope", token.scope());
        cuerpo.put("githubUser", login);
        cuerpo.put("clienteId", clienteId);
        cuerpo.put("email", email);
        cuerpo.put("uso", "Enviar en cada request: Authorization: Bearer <accessToken>");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
