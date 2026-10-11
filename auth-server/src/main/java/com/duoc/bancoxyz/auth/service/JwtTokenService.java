package com.duoc.bancoxyz.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Emite los JWT propios de Banco XYZ.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final long expirationSeconds;
    private final List<String> scopesLectura;
    private final List<String> scopesOperacion;
    private final List<String> operadores;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${auth.jwt.issuer}") String issuer,
            @Value("${auth.jwt.audience}") String audience,
            @Value("${auth.jwt.expiration-seconds}") long expirationSeconds,
            @Value("${auth.scopes.lectura}") String scopesLectura,
            @Value("${auth.scopes.operacion}") String scopesOperacion,
            @Value("${auth.operadores:}") String operadores) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.expirationSeconds = expirationSeconds;
        this.scopesLectura = separar(scopesLectura, " ");
        this.scopesOperacion = separar(scopesOperacion, " ");
        this.operadores = separar(operadores.toLowerCase(Locale.ROOT), ",");
    }

    /** Token emitido mas los datos que se devuelven al cliente. */
    public record TokenEmitido(String accessToken, long expiresIn, String scope) {
    }

    public TokenEmitido emitir(String githubLogin, String email, String nombre) {
        return emitir(githubLogin, email, nombre, null);
    }

    /**
     * clienteId = cliente del banco asociado a la cuenta de GitHub.
     */
    public TokenEmitido emitir(String githubLogin, String email, String nombre, Long clienteId) {
        Instant ahora = Instant.now();
        String scope = String.join(" ", scopesPara(githubLogin));

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(githubLogin)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(expirationSeconds))
                .claim("scope", scope)
                .claim("provider", "github");
        if (clienteId != null) {
            claims.claim("clienteId", clienteId);
        }
        if (StringUtils.hasText(email)) {
            claims.claim("email", email);
        }
        if (StringUtils.hasText(nombre)) {
            claims.claim("name", nombre);
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
        log.info("[AUTH] Token emitido para '{}' (cliente {}) con scopes [{}], expira en {} s", githubLogin,
                clienteId, scope, expirationSeconds);
        return new TokenEmitido(token, expirationSeconds, scope);
    }

    /**
     * Los usuarios reciben los scopes de lectura. Los de operacion
     * (retiros.write) solo los usuarios listados en auth.operadores.
     * Si la lista esta vacia, todos los usuarios autenticados son operadores.
     */
    List<String> scopesPara(String githubLogin) {
        List<String> scopes = new ArrayList<>(scopesLectura);
        boolean esOperador = operadores.isEmpty()
                || (githubLogin != null && operadores.contains(githubLogin.toLowerCase(Locale.ROOT)));
        if (esOperador) {
            scopes.addAll(scopesOperacion);
        }
        return scopes;
    }

    private static List<String> separar(String valor, String separador) {
        if (!StringUtils.hasText(valor)) {
            return List.of();
        }
        return Arrays.stream(valor.split(separador))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
