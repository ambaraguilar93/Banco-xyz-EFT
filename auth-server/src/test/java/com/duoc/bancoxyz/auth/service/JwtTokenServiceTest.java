package com.duoc.bancoxyz.auth.service;

import com.duoc.bancoxyz.auth.config.JwtKeyConfig;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el token que emite el auth-server: se firma con la clave privada,
 * se puede validar con la clave publica (lo mismo que hacen los microservicios)
 * y trae los claims y scopes esperados. No levanta Spring ni llama a GitHub.
 */
@DisplayName("auth-server - emision de JWT")
class JwtTokenServiceTest {

    private static final String ISSUER = "http://localhost:9000";

    private JwtKeyConfig keyConfig;
    private RSAKey rsaKey;
    private JwtEncoder encoder;
    private NimbusJwtDecoder decoderConClavePublica;

    @BeforeEach
    void preparar() throws Exception {
        keyConfig = new JwtKeyConfig();
        rsaKey = keyConfig.rsaKey();
        encoder = keyConfig.jwtEncoder(keyConfig.jwkSource(rsaKey));
        decoderConClavePublica = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }

    private JwtTokenService servicio(String operadores) {
        return new JwtTokenService(encoder, ISSUER, "banco-xyz", 3600,
                "cuentas.read retiros.read", "retiros.write", operadores);
    }

    @Test
    void elTokenSeValidaConLaClavePublicaYTraeLosClaims() {
        JwtTokenService.TokenEmitido emitido = servicio("").emitir("ambar", "ambar@correo.cl", "Ambar");

        Jwt jwt = decoderConClavePublica.decode(emitido.accessToken());

        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
        assertThat(jwt.getAudience()).containsExactly("banco-xyz");
        assertThat(jwt.getSubject()).isEqualTo("ambar");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ambar@correo.cl");
        assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
        assertThat(jwt.getHeaders().get("alg")).hasToString("RS256");
        assertThat(jwt.getHeaders().get("kid")).isEqualTo(rsaKey.getKeyID());
        assertThat(emitido.expiresIn()).isEqualTo(3600);
    }

    @Test
    void sinListaDeOperadoresTodosRecibenTodosLosScopes() {
        assertThat(servicio("").scopesPara("cualquiera"))
                .containsExactly("cuentas.read", "retiros.read", "retiros.write");
    }

    @Test
    void conListaDeOperadoresSoloEllosPuedenRetirar() {
        JwtTokenService servicio = servicio("Ambar, otro-operador");

        assertThat(servicio.scopesPara("ambar")).contains("retiros.write");
        assertThat(servicio.scopesPara("visitante")).containsExactly("cuentas.read", "retiros.read");
    }

    @Test
    void unTokenFirmadoConOtraClaveNoSeAcepta() throws Exception {
        String tokenAjeno = new JwtTokenService(
                keyConfig.jwtEncoder(keyConfig.jwkSource(keyConfig.rsaKey())), ISSUER, "banco-xyz", 3600,
                "cuentas.read", "", "").emitir("intruso", null, null).accessToken();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoderConClavePublica.decode(tokenAjeno))
                .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }
}
