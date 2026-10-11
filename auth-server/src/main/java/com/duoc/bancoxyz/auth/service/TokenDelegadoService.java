package com.duoc.bancoxyz.auth.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * TOKENS DELEGADOS por canal.
 *
 * Los BFF nunca reenvian el token del usuario tal cual: lo cambian por un token
 * del canal
 * con vida corta y con los permisos recortados al maximo del canal.
 *
 * - token-exchange (web y movil): el BFF presenta el JWT del usuario y recibe
 * uno nuevo y expiracion de minutos.
 * - client_credentials (cajero): el BFF del cajero se autentica a si mismo. Sin
 * cliente recibe su token de servicio, con cliente_id/cuenta_id (despues de
 * validar tarjeta y PIN) recibe el token de la sesion del cajero, limitado a
 * esa cuenta.
 */
@Service
public class TokenDelegadoService {

    public static final String GRANT_TOKEN_EXCHANGE = "urn:ietf:params:oauth:grant-type:token-exchange";
    public static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";

    private static final Logger log = LoggerFactory.getLogger(TokenDelegadoService.class);

    /** Error OAuth 2.0 */
    public static class ErrorOAuth extends RuntimeException {
        private final String codigo;
        private final int status;

        public ErrorOAuth(int status, String codigo, String descripcion) {
            super(descripcion);
            this.status = status;
            this.codigo = codigo;
        }

        public String codigo() {
            return codigo;
        }

        public int status() {
            return status;
        }
    }

    public record TokenDelegado(String accessToken, long expiresIn, String scope) {
    }

    private final JwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final ClientesBffProperties propiedades;
    private final String issuer;
    private final String audience;

    public TokenDelegadoService(JwtEncoder encoder, RSAKey rsaKey, ClientesBffProperties propiedades,
            @Value("${auth.jwt.issuer}") String issuer, @Value("${auth.jwt.audience}") String audience)
            throws JOSEException {
        this.encoder = encoder;
        this.propiedades = propiedades;
        this.issuer = issuer;
        this.audience = audience;
        this.decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer)));
    }

    /** Valida client_id + secret (HTTP Basic). */
    public ClientesBffProperties.ClienteBff autenticar(String clientId, String secret) {
        ClientesBffProperties.ClienteBff cliente = clientId == null ? null : propiedades.bff().get(clientId);
        if (cliente == null || secret == null || !java.security.MessageDigest.isEqual(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                String.valueOf(cliente.secret()).getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ErrorOAuth(401, "invalid_client", "Credenciales del cliente invalidas");
        }
        return cliente;
    }

    /** token-exchange: token del usuario -> token delegado del canal. */
    public TokenDelegado intercambiar(String clientId, ClientesBffProperties.ClienteBff cliente, String subjectToken) {
        if (!StringUtils.hasText(subjectToken)) {
            throw new ErrorOAuth(400, "invalid_request", "Falta subject_token");
        }
        Jwt usuario;
        try {
            usuario = decoder.decode(subjectToken);
        } catch (JwtException e) {
            throw new ErrorOAuth(400, "invalid_grant", "subject_token invalido o expirado");
        }
        if (usuario.hasClaim("canal")) {
            // un token delegado no se puede volver a delegar.
            throw new ErrorOAuth(400, "invalid_grant", "subject_token ya es un token delegado");
        }
        Object clienteId = usuario.getClaim("clienteId");
        if (clienteId == null) {
            throw new ErrorOAuth(400, "invalid_grant", "El usuario no esta asociado a un cliente del banco");
        }
        Set<String> scopes = interseccion(separar(usuario.getClaimAsString("scope")), separar(cliente.scopes()));
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(cliente.expiracion());
        if (usuario.getExpiresAt() != null && usuario.getExpiresAt().isBefore(expira)) {
            expira = usuario.getExpiresAt(); // nunca vive mas que el token original
        }
        JwtClaimsSet.Builder claims = base(clientId, cliente, ahora, expira, scopes)
                .subject(usuario.getSubject())
                .claim("clienteId", ((Number) clienteId).longValue());
        return emitir(claims, ahora, expira, scopes, "token-exchange", clientId, usuario.getSubject());
    }

    /**
     * client_credentials: token propio del BFF, o de la sesion del cajero si trae
     * cliente_id.
     */
    public TokenDelegado credencialesCliente(String clientId, ClientesBffProperties.ClienteBff cliente,
            Long clienteId, Long cuentaId, String terminalId) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plusSeconds(cliente.expiracion());
        if (clienteId == null) {
            Set<String> scopes = new LinkedHashSet<>(separar(cliente.scopesServicio()));
            if (scopes.isEmpty()) {
                throw new ErrorOAuth(400, "unauthorized_client", "El cliente no tiene permisos de servicio");
            }
            JwtClaimsSet.Builder claims = base(clientId, cliente, ahora, expira, scopes).subject(clientId);
            return emitir(claims, ahora, expira, scopes, "client_credentials", clientId, clientId);
        }
        if (!cliente.puedeAsegurarCliente()) {
            throw new ErrorOAuth(400, "unauthorized_client", "El cliente no puede pedir tokens a nombre de un cliente");
        }
        Set<String> scopes = new LinkedHashSet<>(separar(cliente.scopes()));
        String sujeto = "cliente:" + clienteId;
        JwtClaimsSet.Builder claims = base(clientId, cliente, ahora, expira, scopes)
                .subject(sujeto)
                .claim("clienteId", clienteId);
        if (cuentaId != null) {
            claims.claim("cuentaId", cuentaId);
        }
        if (StringUtils.hasText(terminalId)) {
            claims.claim("terminal", terminalId); // la sesion solo sirve en ese cajero
        }
        return emitir(claims, ahora, expira, scopes, "client_credentials", clientId, sujeto);
    }

    private JwtClaimsSet.Builder base(String clientId, ClientesBffProperties.ClienteBff cliente, Instant ahora,
            Instant expira, Set<String> scopes) {
        return JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .issuedAt(ahora)
                .expiresAt(expira)
                .id(UUID.randomUUID().toString())
                .claim("scope", String.join(" ", scopes))
                .claim("canal", cliente.canal())
                .claim("azp", clientId)
                .claim("act", Map.of("sub", clientId));
    }

    private TokenDelegado emitir(JwtClaimsSet.Builder claims, Instant ahora, Instant expira, Set<String> scopes,
            String grant, String clientId, String sujeto) {
        String token = encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
        long vida = Math.max(0, expira.getEpochSecond() - ahora.getEpochSecond());
        log.info("[AUTH] Token delegado ({}) para '{}' via {}: scopes [{}], expira en {} s", grant, sujeto, clientId,
                String.join(" ", scopes), vida);
        return new TokenDelegado(token, vida, String.join(" ", scopes));
    }

    private static Set<String> interseccion(List<String> a, List<String> b) {
        Set<String> resultado = new LinkedHashSet<>(a);
        resultado.retainAll(b);
        return resultado;
    }

    private static List<String> separar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return List.of();
        }
        return Arrays.stream(valor.trim().split("\\s+")).filter(StringUtils::hasText).toList();
    }
}
