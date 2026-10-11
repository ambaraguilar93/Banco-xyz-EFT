package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.exception.ValidacionDatosException;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * 
 * TransaccionProcessor
 * Se validan las transacciones antes de guardarlas.
 * Se consideran las fechas, montos, los tipos (debito o credito) y duplicados.
 * 
 */
@Component
public class TransaccionProcessor implements ItemProcessor<Transaccion, Transaccion> {

    private static final String JOB = "reporteTransaccionesDiariasJob";

    private final RechazoRepository rechazoRepository;
    private final MetricasStepListener metricasStepListener;
    private final ConcurrentHashMap<String, String> huellaAId = new ConcurrentHashMap<>();
    private final String archivo;

    public TransaccionProcessor(RechazoRepository rechazoRepository,
            MetricasStepListener metricasStepListener,
            @Value("${banco.archivo.transacciones}") String archivo) {
        this.rechazoRepository = rechazoRepository;
        this.metricasStepListener = metricasStepListener;
        this.archivo = archivo;
    }

    /**
     * Revisa las transacciones.
     */
    @Override
    public Transaccion process(Transaccion transaccion) {

        metricasStepListener.anotarHilo();

        String id = String.valueOf(transaccion.getId());

        if (transaccion.getFecha() == null) {
            throw new ValidacionDatosException(
                    "Transaccion " + id + ": la fecha no pudo interpretarse");
        }

        if (transaccion.getMonto() == null
                || transaccion.getMonto().compareTo(BigDecimal.ZERO) <= 0) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Transacción descartada: ID " + transaccion.getId()
                            + " - monto inválido: " + transaccion.getMonto(),
                    transaccion.toString());
            return null;
        }

        String tipo = transaccion.getTipo() == null
                ? ""
                : transaccion.getTipo().trim().toUpperCase();

        if (!tipo.equals("DEBITO") && !tipo.equals("CREDITO")) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Tipo de transaccion no reconocido: '" + tipo + "'", transaccion.toString());
            return null;
        }

        transaccion.setTipo(tipo);

        String huella = transaccion.getFecha() + "|" + transaccion.getMonto() + "|" + tipo;
        String idQueLaReclamoPrimero = huellaAId.putIfAbsent(huella, id);

        if (idQueLaReclamoPrimero != null && !idQueLaReclamoPrimero.equals(id)) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Registro duplicado (misma fecha, monto y tipo)", transaccion.toString());
            return null;
        }

        return transaccion;
    }
}