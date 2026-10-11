package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.regex.Pattern;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.exception.ValidacionDatosException;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.model.CuentaAnual;
import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * Limpia los movimientos de cuenta antes de guardarlos.
 * Se validan las fechas, los montos, el tipo de movimiento, la descripcion,
 * signo del monto.
 * Para Descripcion no se rechaza, se corrige.
 * Para Signo del monto se normaliza segun el tipo de movimiento.
 */
@Component
public class CuentaAnualProcessor implements ItemProcessor<CuentaAnual, CuentaAnual> {

    private static final String JOB = "estadosCuentaAnualesJob";
    private static final Pattern MARCAS_DIACRITICAS = Pattern.compile("\\p{M}");

    private final RechazoRepository rechazoRepository;
    private final MetricasStepListener metricasStepListener;
    private final String archivo;

    public CuentaAnualProcessor(RechazoRepository rechazoRepository,
            MetricasStepListener metricasStepListener,
            @Value("${banco.archivo.cuentas-anuales}") String archivo) {
        this.rechazoRepository = rechazoRepository;
        this.metricasStepListener = metricasStepListener;
        this.archivo = archivo;
    }

    /**
     * Revisa un movimeinto de cuenta: la fecha, el monto, el tipo de movimiento,
     * la descripcion y el signo del monto (positivo/negativo).
     */
    @Override
    public CuentaAnual process(CuentaAnual movimiento) {

        metricasStepListener.anotarHilo();

        String id = "cuenta " + movimiento.getCuentaId();

        // Validacion de fechas
        if (movimiento.getFecha() == null) {
            throw new ValidacionDatosException(
                    id + ": la fecha no pudo interpretarse");
        }

        // Validacion de montos
        if (movimiento.getMonto() == null
                || movimiento.getMonto().compareTo(BigDecimal.ZERO) == 0) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Monto invalido (nulo o cero)", movimiento.toString());
            return null;
        }

        // Validacion de tipo de movimiento
        // SEMANA 3: la data real trae depósito con tilde. Se le quitan las tildes
        // antes de comparar, para no rechazar un dato que es un deposito valido.
        String tipo = movimiento.getTransaccion() == null
                ? ""
                : sinTildes(movimiento.getTransaccion().trim().toUpperCase());

        // EFT: fin_legacy_data agrega movimientos de tipo "pago" (salida de dinero,
        // igual que retiro y compra).
        if (!tipo.equals("DEPOSITO") && !tipo.equals("RETIRO") && !tipo.equals("COMPRA")
                && !tipo.equals("PAGO")) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Tipo de movimiento no reconocido: '" + tipo + "'", movimiento.toString());
            return null;
        }

        movimiento.setTransaccion(tipo);

        // Validacion de descripcion
        if (movimiento.getDescripcion() == null || movimiento.getDescripcion().isBlank()) {
            movimiento.setDescripcion("SIN DESCRIPCION");
        } else {
            movimiento.setDescripcion(movimiento.getDescripcion().trim());
        }

        // Verificacion de signo segun el tipo de movimiento
        BigDecimal montoAbsoluto = movimiento.getMonto().abs();

        if (tipo.equals("DEPOSITO")) {
            movimiento.setMonto(montoAbsoluto);
        } else {
            movimiento.setMonto(montoAbsoluto.negate());
        }

        return movimiento;
    }

    private static String sinTildes(String valor) {

        String descompuesto = Normalizer.normalize(valor, Normalizer.Form.NFD);
        return MARCAS_DIACRITICAS.matcher(descompuesto).replaceAll("");
    }
}
