package com.duoc.bancoxyz.listener;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

/**
 * 
 * JobListener
 * Se imprime en consola cuando un job empieza y termina, con un
 * resumen de cada step (cuantas filas se leyeron, filtraron, escribieron y
 * omitieron).
 */
@Component
public class JobListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        System.out.println();
        System.out.println(" INICIANDO JOB: " + jobExecution.getJobInstance().getJobName());

    }

    @Override
    public void afterJob(JobExecution jobExecution) {

        System.out.println(" JOB TERMINADO: " + jobExecution.getJobInstance().getJobName()
                + " | ESTADO: " + jobExecution.getStatus());

        for (StepExecution step : jobExecution.getStepExecutions()) {
            System.out.printf("   Step '%s' -> leidos: %d | filtrados: %d | escritos: %d | omitidos: %d%n",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getFilterCount(),
                    step.getWriteCount(),
                    step.getSkipCount());
        }

        System.out.println("==========================================================");
    }
}
