package com.duoc.bancoxyz.bff.web.comun;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Toma (o genera) el X-Correlation-Id de cada request y lo deja en el MDC del log. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CorrelationId.establecer(request.getHeader(CorrelationId.HEADER_HTTP));
        response.setHeader(CorrelationId.HEADER_HTTP, CorrelationId.actual());
        if (request.getRequestURI().startsWith("/api/")) {
            log.info("{} {}", request.getMethod(), request.getRequestURI());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationId.limpiar();
        }
    }
}
