package com.duoc.bancoxyz.clientes.mensajeria;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Convierte el JSON de un mensaje en su clase de evento; si no se puede, es un evento invalido. */
@Component
public class LectorEventos {

    private final JsonMapper jsonMapper;

    public LectorEventos(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public <T> T leer(String json, Class<T> tipo) {
        try {
            T evento = jsonMapper.readValue(json, tipo);
            if (evento == null) {
                throw new EventoInvalidoException("Mensaje vacio para " + tipo.getSimpleName());
            }
            return evento;
        }
        catch (JacksonException e) {
            throw new EventoInvalidoException("JSON invalido para " + tipo.getSimpleName() + ": " + e.getOriginalMessage());
        }
    }
}
