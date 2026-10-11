package com.duoc.bancoxyz.bff.cajero.security;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * mTLS: el servidor exige certificado de cliente y solo
 * confia en la CA de la red de cajeros. Este filtro toma el CN del certificado
 * ya validado como identidad del TERMINAL: queda en cada linea de log y
 * la sesion del cliente solo es valida en ese mismo cajero.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class TerminalCajeroFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_TERMINAL = "bancoxyz.terminal";
    private static final Logger log = LoggerFactory.getLogger(TerminalCajeroFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        X509Certificate[] certificados = (X509Certificate[]) request
                .getAttribute("jakarta.servlet.request.X509Certificate");
        String terminal = certificados == null || certificados.length == 0 ? null : cn(certificados[0]);
        if (terminal == null) {
            log.warn("[CAJERO] Request sin certificado de terminal valido desde {}", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter()
                    .printf("{\"codigo\":403,\"estado\":\"FORBIDDEN\",\"mensaje\":\"Terminal no identificado: "
                            + "se requiere el certificado del cajero\",\"detalles\":[],\"ruta\":\"%s\",\"fechaHora\":\"%s\"}",
                            request.getRequestURI(), OffsetDateTime.now());
            return;
        }
        request.setAttribute(ATRIBUTO_TERMINAL, terminal);
        MDC.put("terminal", terminal);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("terminal");
        }
    }

    private static String cn(X509Certificate certificado) {
        try {
            for (Rdn rdn : new LdapName(certificado.getSubjectX500Principal().getName()).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (InvalidNameException e) {
            log.warn("[CAJERO] Certificado con nombre invalido: {}", e.getMessage());
        }
        return null;
    }
}
