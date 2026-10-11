package com.duoc.bancoxyz.processor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.model.Interes;
import com.duoc.bancoxyz.model.InteresCalculado;
import com.duoc.bancoxyz.repository.RechazoRepository;

/**
 * 
 * InteresProcessor
 * Aplica interes mensual a cuentas de ahorro y prestamos.
 * Se valida y transforma el dato en un resultado calculado.
 * 
 * Se rechazan: saldos nulos, negativos o en 0,
 * edades fueran de un rango logico, tipos que no sean ahorro o prestamo,
 * cuentas duplicadas.
 * 
 */
@Component
public class InteresProcessor implements ItemProcessor<Interes, InteresCalculado> {

    private static final String JOB = "calculoInteresesMensualesJob";

    private final RechazoRepository rechazoRepository;
    private final MetricasStepListener metricasStepListener;
    private final BigDecimal tasaAhorro;
    private final BigDecimal tasaPrestamo;
    private final int edadMinima;
    private final int edadMaxima;
    private final String archivo;

    private final ConcurrentHashMap<String, String> huellaAId = new ConcurrentHashMap<>();

    public InteresProcessor(RechazoRepository rechazoRepository,
            MetricasStepListener metricasStepListener,
            @Value("${banco.tasa.ahorro}") BigDecimal tasaAhorro,
            @Value("${banco.tasa.prestamo}") BigDecimal tasaPrestamo,
            @Value("${banco.edad.minima}") int edadMinima,
            @Value("${banco.edad.maxima}") int edadMaxima,
            @Value("${banco.archivo.intereses}") String archivo) {

        this.archivo = archivo;

        this.rechazoRepository = rechazoRepository;
        this.metricasStepListener = metricasStepListener;
        this.tasaAhorro = tasaAhorro;
        this.tasaPrestamo = tasaPrestamo;
        this.edadMinima = edadMinima;
        this.edadMaxima = edadMaxima;
    }

    // Revisa una cuenta y si pasa las validaciones calcula su interes mensual.

    @Override
    public InteresCalculado process(Interes interes) {

        // SEMANA 3: ver TransaccionProcessor - misma correccion para que
        // MetricasStepListener pueda mostrar el reparto real por hilo.
        metricasStepListener.anotarHilo();

        String id = String.valueOf(interes.getCuentaId());

        if (interes.getSaldo() == null
                || interes.getSaldo().compareTo(BigDecimal.ZERO) <= 0) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Saldo invalido (nulo, cero o negativo)", interes.toString());
            return null;
        }

        if (interes.getEdad() == null
                || interes.getEdad() < edadMinima
                || interes.getEdad() > edadMaxima) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Edad fuera de rango (" + edadMinima + "-" + edadMaxima + "): " + interes.getEdad(),
                    interes.toString());
            return null;
        }

        String tipo = interes.getTipo() == null
                ? ""
                : interes.getTipo().trim().toLowerCase();

        BigDecimal tasa;

        // Segun el tipo de cuenta se elige que tasa de interes aplicar.
        if (tipo.equals("ahorro")) {
            tasa = tasaAhorro;
        } else if (tipo.equals("prestamo")) {
            tasa = tasaPrestamo;
        } else {
            rechazoRepository.registrar(JOB, archivo, id,
                    "Tipo de cuenta no soportado: '" + tipo + "'", interes.toString());
            return null;
        }

        String huella = interes.getNombre() + "|" + interes.getSaldo()
                + "|" + interes.getEdad() + "|" + tipo;
        String idQueLaReclamoPrimero = huellaAId.putIfAbsent(huella, id);

        if (idQueLaReclamoPrimero != null && !idQueLaReclamoPrimero.equals(id)) {

            rechazoRepository.registrar(JOB, archivo, id,
                    "Registro duplicado (mismo titular, saldo, edad y tipo)", interes.toString());
            return null;
        }

        // Calculo del interes: saldo x tasa, redondeado a 2 decimales.
        BigDecimal interesMensual = interes.getSaldo()
                .multiply(tasa)
                .setScale(2, RoundingMode.HALF_UP);

        InteresCalculado resultado = new InteresCalculado();
        resultado.setCuentaId(interes.getCuentaId());
        resultado.setLineaOrigen(interes.getLinea());
        resultado.setNombre(interes.getNombre().trim());
        resultado.setTipo(tipo);
        resultado.setSaldoInicial(interes.getSaldo().setScale(2, RoundingMode.HALF_UP));
        resultado.setTasaAplicada(tasa);
        resultado.setInteresMensual(interesMensual);
        resultado.setSaldoFinal(interes.getSaldo().add(interesMensual).setScale(2, RoundingMode.HALF_UP));

        return resultado;
    }
}