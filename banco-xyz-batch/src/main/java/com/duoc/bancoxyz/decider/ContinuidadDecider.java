package com.duoc.bancoxyz.decider;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.core.job.flow.JobExecutionDecider;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ContinuidadDecider
 *
 * Se ejecuta después del step que carga el CSV y antes del step que arma
 * el resumen. Revisa qué porcentaje de registros se descartó y decide
 * si el job sigue o no, para no generar un resumen con datos incompletos
 * como si todo hubiera salido bien.
 *
 * - CONTINUAR: todo bien, se sigue al step de agregación.
 * - DEGRADADO: se descartó más de lo esperado (umbral por defecto 50 %),
 * pero hay datos útiles. Se sigue y queda marcado en el log.
 * - SIN_DATOS: no se guardó nada útil, el job se detiene.
 */
@Component
public class ContinuidadDecider implements JobExecutionDecider {

    public static final String CONTINUAR = "CONTINUAR";
    public static final String DEGRADADO = "DEGRADADO";
    public static final String SIN_DATOS = "SIN_DATOS";

    // Que % de datos descartados se considera "demasiado" (por defecto, 50%).
    private final double umbralRechazo;

    public ContinuidadDecider(
            @Value("${banco.calidad.umbral-rechazo:0.5}") double umbralRechazo) {

        this.umbralRechazo = umbralRechazo;
    }

    /**
     * Revisa cuantos registros se leyeron y cuantos se terminaron
     * escribiendo. Calcula el % descartado y devuelve uno de los
     * 3 estados.
     */
    @Override
    public FlowExecutionStatus decide(JobExecution jobExecution, StepExecution stepExecution) {

        StepExecution ultimo = stepExecution != null
                ? stepExecution
                : ultimoStep(jobExecution);

        if (ultimo == null) {
            System.out.println("[DECIDER] No hay informacion del step previo -> SIN_DATOS");
            return new FlowExecutionStatus(SIN_DATOS);
        }

        long leidos = ultimo.getReadCount();
        long escritos = ultimo.getWriteCount();
        long descartados = Math.max(leidos - escritos, 0);

        if (escritos == 0) {

            System.out.printf(
                    "[DECIDER] Step '%s': 0 registros escritos de %d leidos -> %s "
                            + "(se detiene, no hay base para agregar)%n",
                    ultimo.getStepName(), leidos, SIN_DATOS);

            return new FlowExecutionStatus(SIN_DATOS);
        }

        double proporcion = leidos == 0 ? 0.0 : (double) descartados / leidos;

        if (proporcion > umbralRechazo) {

            System.out.printf(
                    "[DECIDER] Step '%s': %d de %d registros descartados (%.1f%%, umbral %.1f%%) -> %s%n",
                    ultimo.getStepName(), descartados, leidos,
                    proporcion * 100, umbralRechazo * 100, DEGRADADO);

            return new FlowExecutionStatus(DEGRADADO);
        }

        System.out.printf(
                "[DECIDER] Step '%s': %d de %d registros descartados (%.1f%%) -> %s%n",
                ultimo.getStepName(), descartados, leidos, proporcion * 100, CONTINUAR);

        return new FlowExecutionStatus(CONTINUAR);
    }

    // Busca el step mas reciente que se haya ejecutado, por si
    // Spring Batch no lo entrega directo.
    private StepExecution ultimoStep(JobExecution jobExecution) {

        return jobExecution.getStepExecutions().stream()
                .reduce((primero, segundo) -> segundo)
                .orElse(null);
    }
}
