package com.duoc.bancoxyz.ejecucion;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.duoc.bancoxyz.exception.FalloNoRecuperableException;

/**
 * EjecutorJobs
 *
 * Lanza un job y aplica la reejecución automática ante fallos críticos.
 *
 * Si el job termina en FAILED por un fallo recuperable (por ejemplo, se cayó
 * la base de datos), espera unos segundos y lo reinicia con
 * JobOperator.restart(). Spring Batch retoma desde el step que falló, así
 * que los steps ya terminados no se repiten.
 *
 * No se reejecuta si:
 * - ya se usaron todos los intentos (banco.reejecucion.intentos),
 * - el fallo es FalloNoRecuperableException (la integridad no cuadra), o
 * - el job falló sin excepción (el decider lo detuvo por SIN_DATOS, y
 * reintentar con el mismo archivo daría lo mismo).
 */
@Component
public class EjecutorJobs {

    private final JobOperator jobOperator;
    private final int intentosMaximos;
    private final long esperaMs;

    public EjecutorJobs(JobOperator jobOperator,
            @Value("${banco.reejecucion.intentos:2}") int intentosMaximos,
            @Value("${banco.reejecucion.espera-ms:3000}") long esperaMs) {

        this.jobOperator = jobOperator;
        this.intentosMaximos = intentosMaximos;
        this.esperaMs = esperaMs;
    }

    public JobExecution ejecutar(Job job, JobParameters parametros) throws Exception {

        JobExecution ejecucion = jobOperator.start(job, parametros);
        int intento = 0;

        while (ejecucion.getStatus() == BatchStatus.FAILED) {

            if (!esRecuperable(ejecucion)) {
                System.out.printf("[REEJECUCION] %s fallo por una causa NO recuperable. "
                        + "No se reejecuta: requiere revision.%n", job.getName());
                break;
            }

            if (intento >= intentosMaximos) {
                System.out.printf("[REEJECUCION] %s sigue en FAILED despues de %d reejecuciones. "
                        + "Se detiene.%n", job.getName(), intentosMaximos);
                break;
            }

            intento++;
            System.out.printf("[REEJECUCION] %s termino FAILED (instancia %d). Reejecucion automatica %d/%d "
                    + "en %d ms, retomando desde el step que fallo...%n",
                    job.getName(), ejecucion.getJobInstance().getId(), intento, intentosMaximos, esperaMs);

            Thread.sleep(esperaMs);
            ejecucion = jobOperator.restart(ejecucion);
        }

        if (intento > 0 && ejecucion.getStatus() == BatchStatus.COMPLETED) {
            System.out.printf("[REEJECUCION] %s se recupero en la reejecucion %d -> COMPLETED%n",
                    job.getName(), intento);
        }

        return ejecucion;
    }

    private boolean esRecuperable(JobExecution ejecucion) {

        if (ejecucion.getAllFailureExceptions().isEmpty()) {
            return false;
        }

        for (Throwable fallo : ejecucion.getAllFailureExceptions()) {
            for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
                if (causa instanceof FalloNoRecuperableException) {
                    return false;
                }
            }
        }

        return true;
    }
}
