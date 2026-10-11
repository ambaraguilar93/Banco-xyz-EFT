package com.duoc.bancoxyz.auth.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Punto de entrada de como obtener un token. */
@RestController
public class AuthController {

    private final String issuer;

    public AuthController(@Value("${auth.jwt.issuer}") String issuer) {
        this.issuer = issuer;
    }

    @GetMapping({ "/", "/api/auth/login" })
    public Map<String, String> login() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("mensaje",
                "Para obtener un token, abra en el navegador la URL de autorizacion e inicie sesion con GitHub");
        info.put("authorizationUrl", issuer + "/oauth2/authorization/github");
        info.put("jwksUrl", issuer + "/.well-known/jwks.json");
        return info;
    }
}
