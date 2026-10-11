package com.duoc.bancoxyz.auth.controller;

import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Clave publica. Es lo que consultan banco-servicios y ms-cuentas
 * para verificar la firma de los tokens.
 */
@RestController
public class JwkSetController {

    private final JWKSet publicJwkSet;

    public JwkSetController(JWKSet publicJwkSet) {
        this.publicJwkSet = publicJwkSet;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> clavesPublicas() {
        return publicJwkSet.toJSONObject();
    }
}
