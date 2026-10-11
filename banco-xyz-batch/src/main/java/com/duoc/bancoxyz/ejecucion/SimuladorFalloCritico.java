package com.duoc.bancoxyz.ejecucion;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * EFT - SimuladorFalloCritico
 *
 * Igual que WriterConFalloSimulado sirve para ver el retry, esta clase sirve
 * para ver la REEJECUCION AUTOMATICA.
 *
 * Con el perfil 'fallo-critico' el primer step de agregacion que se ejecute
 * falla UNA vez. El job queda FAILED, EjecutorJobs lo reejecuta y Spring Batch
 * retoma desde ese step.
 *
 * Con la configuracion normal no hace nada.
 */
@Component
public class SimuladorFalloCritico implements StepExecutionListener {

    private final AtomicBoolean pendiente;

    public SimuladorFalloCritico(@Value("${banco.simular.fallo-critico:false}") boolean activo) {
        this.pendiente = new AtomicBoolean(activo);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {

        if (pendiente.compareAndSet(true, false)) {

            System.out.printf("  [FALLO CRITICO SIMULADO] step '%s': se pierde la conexion con PostgreSQL%n",
                    stepExecution.getStepName());

            throw new IllegalStateException(
                    "Fallo critico simulado: el servidor de base de datos se reinicio durante "
                            + stepExecution.getStepName());
        }
    }
}
