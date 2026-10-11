package com.duoc.bancoxyz.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Par de claves RSA con que se firman los JWT.
 *
 * La clave privada solo existe dentro del auth-server.
 * La clave publica se publica para que los microservicios verifiquen la firma.
 *
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAKey rsaKey() {
        KeyPair keyPair = generarParDeClaves();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    /** Fuente de claves (privada + publica) que usa el encoder para firmar. */
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    /** Solo la parte publica: es lo que se expone en el endpoint JWKS. */
    @Bean
    public JWKSet publicJwkSet(RSAKey rsaKey) {
        return new JWKSet(rsaKey.toPublicJWK());
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    private static KeyPair generarParDeClaves() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo generar el par de claves RSA", e);
        }
    }
}
