package com.duoc.bancoxyz.escalamiento;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.config.FabricaStepsCarga;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.model.Transaccion;
import com.duoc.bancoxyz.processor.TransaccionProcessor;
import com.duoc.bancoxyz.util.CampoCsv;
import com.duoc.bancoxyz.util.ParseadorFechas;

/**
 * EscalamientoRunner
 *
 * Se activa con el perfil 'escalamiento':
 * ./mvnw spring-boot:run "-Dspring-boot.run.profiles=escalamiento"
 *
 * Escribe en escalamiento_transacciones para no pisar la tabla de negocio
 * "transacciones".
 */
@Component
@Profile("escalamiento")
public class EscalamientoRunner implements CommandLineRunner {

        // Archivo que se usa como base para todas las combinaciones del
        // experimento: data/movimientos_financieros_diarios.csv (sale de las
        // properties banco.data.directorio y banco.archivo.transacciones).
        private final String archivoDataset;

        private final JobLauncher jobLauncher;
        private final JobRepository jobRepository;
        private final PlatformTransactionManager transactionManager;
        private final DataSource dataSource;
        private final JdbcTemplate jdbcTemplate;
        private final FabricaStepsCarga fabricaStepsCarga;
        private final TransaccionProcessor transaccionProcessor;
        private final MetricasStepListener metricasStepListener;

        public EscalamientoRunner(
                        JobLauncher jobLauncher,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        DataSource dataSource,
                        JdbcTemplate jdbcTemplate,
                        FabricaStepsCarga fabricaStepsCarga,
                        TransaccionProcessor transaccionProcessor,
                        MetricasStepListener metricasStepListener,
                        @Value("${banco.data.directorio}") String directorio,
                        @Value("${banco.archivo.transacciones}") String archivo) {

                this.archivoDataset = directorio + "/" + archivo;

                this.jobLauncher = jobLauncher;
                this.jobRepository = jobRepository;
                this.transactionManager = transactionManager;
                this.dataSource = dataSource;
                this.jdbcTemplate = jdbcTemplate;
                this.fabricaStepsCarga = fabricaStepsCarga;
                this.transaccionProcessor = transaccionProcessor;
                this.metricasStepListener = metricasStepListener;
        }

        /**
         * Este es el punto de entrada del experimento. Arma la lista de
         * combinaciones, corre cada una y al final imprime la tabla
         * comparativa con imprimirResumen.
         */
        @Override
        public void run(String... args) throws Exception {

                System.out.println();
                System.out.println("==========================================================");
                System.out.println(" SEMANA 3 - Comparacion de parametros de escalamiento");
                System.out.println(" Dataset: " + archivoDataset + " (registros reales)");
                System.out.println("==========================================================");

                // SEMANA 3: primero se varian los hilos con el chunk fijo en 20,
                // despues se varia el chunk con los hilos fijos en 3. La ultima
                // fila es exactamente la configuracion que usan los 3 Jobs de
                // negocio (3 hilos, chunk 5), para comparar directamente contra
                // ella.
                List<ParametroEscalamiento> combinaciones = List.of(
                                new ParametroEscalamiento("1 hilo (serie)", 1, 20),
                                new ParametroEscalamiento("2 hilos", 2, 20),
                                new ParametroEscalamiento("3 hilos", 3, 20),
                                new ParametroEscalamiento("6 hilos", 6, 20),
                                new ParametroEscalamiento("3 hilos, chunk 50", 3, 50),
                                new ParametroEscalamiento("3 hilos, chunk 100", 3, 100),
                                new ParametroEscalamiento("3 hilos, chunk 5", 3, 5));

                List<ResultadoEscalamiento> resultados = new ArrayList<>();

                for (ParametroEscalamiento combo : combinaciones) {
                        resultados.add(correrCombinacion(combo));
                }

                imprimirResumen(resultados);
        }

        /**
         * Corre una combinacion de parametos:
         * 
         * 1. vacia la tabla de prueba.
         * 2. Crea un pool de hilos con la cantidad de hilos que pide
         * esta combinacion.
         * 3. Arma un reader, un writer y un step.
         * 4. Envuelve este step en un job de un paso y lo ejecuta.
         * 5. Mide cuanto se demoro y calcula el rendimiento, lo devuelve
         * en un ResultadoEscalamiento.
         * 
         * Se apaga el pool de hilos que se creo para no dejar hilos sueltos.
         */
        private ResultadoEscalamiento correrCombinacion(ParametroEscalamiento combo) throws Exception {

                jdbcTemplate.update("TRUNCATE TABLE escalamiento_transacciones");

                ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
                executor.setCorePoolSize(combo.hilos());
                executor.setMaxPoolSize(combo.hilos());
                executor.setQueueCapacity(50);
                executor.setThreadNamePrefix("Escalamiento-" + combo.hilos() + "h-");
                executor.setWaitForTasksToCompleteOnShutdown(true);
                executor.initialize();

                try {

                        FlatFileItemReader<Transaccion> reader = new FlatFileItemReaderBuilder<Transaccion>()
                                        .name("escalamientoReader")
                                        .resource(new ClassPathResource(archivoDataset))
                                        .encoding("UTF-8")
                                        .linesToSkip(1)
                                        .delimited()
                                        .delimiter(",")
                                        .names("id", "fecha", "monto", "tipo")
                                        .fieldSetMapper(campos -> {

                                                Transaccion transaccion = new Transaccion();
                                                transaccion.setId(CampoCsv.entero(campos, "id"));
                                                transaccion.setFecha(ParseadorFechas.parsear(
                                                                CampoCsv.texto(campos, "fecha")));
                                                transaccion.setMonto(CampoCsv.decimal(campos, "monto"));
                                                transaccion.setTipo(CampoCsv.texto(campos, "tipo"));
                                                return transaccion;
                                        })
                                        .build();

                        JdbcBatchItemWriter<Transaccion> writer = new JdbcBatchItemWriterBuilder<Transaccion>()
                                        .dataSource(dataSource)
                                        .sql("""
                                                        INSERT INTO escalamiento_transacciones (id, fecha, monto, tipo)
                                                        VALUES (:id, :fecha, :monto, :tipo)
                                                        """)
                                        .itemSqlParameterSourceProvider(
                                                        new BeanPropertyItemSqlParameterSourceProvider<>())
                                        .build();

                        Step step = fabricaStepsCarga.construir(
                                        "escalamientoStep_" + combo.hilos() + "h_chunk" + combo.tamanoChunk(),
                                        jobRepository,
                                        transactionManager,
                                        reader,
                                        transaccionProcessor,
                                        writer,
                                        "reporteTransaccionesDiariasJob",
                                        archivoDataset,
                                        combo.tamanoChunk(),
                                        executor);

                        Job job = new JobBuilder("escalamientoComparacionJob", jobRepository)
                                        .incrementer(new RunIdIncrementer())
                                        .start(step)
                                        .build();

                        JobParameters parametros = new JobParametersBuilder()
                                        .addString("combo", combo.etiqueta())
                                        .addLong("timestamp", System.currentTimeMillis())
                                        .toJobParameters();

                        JobExecution ejecucion = jobLauncher.run(job, parametros);
                        StepExecution stepExecution = ejecucion.getStepExecutions().iterator().next();

                        long duracionMs = Math.max(1, Duration.between(
                                        stepExecution.getStartTime(), stepExecution.getEndTime()).toMillis());
                        long leidos = stepExecution.getReadCount();
                        double throughput = leidos * 1000.0 / duracionMs;

                        return new ResultadoEscalamiento(
                                        combo,
                                        leidos,
                                        stepExecution.getWriteCount(),
                                        stepExecution.getSkipCount(),
                                        duracionMs,
                                        metricasStepListener.repartoPorHilo().size(),
                                        throughput);

                } finally {
                        executor.shutdown();
                }
        }

        /**
         * Imprime la tabla final con el resultado de las combinaciones.
         * Destaca cual fue la que logro el mayor rendimiento (registros por segundo).
         */
        private void imprimirResumen(List<ResultadoEscalamiento> resultados) {

                System.out.println();
                System.out.println("---------------------------------------------------------------");
                System.out.println(" RESULTADOS DEL EXPERIMENTO DE ESCALAMIENTO");
                System.out.println(" (Registros por corrida, misma entrada para todas)");
                System.out.println("---------------------------------------------------------------");

                resultados.forEach(resultado -> System.out.println("  " + resultado.comoFilaTabla()));

                ResultadoEscalamiento optimo = resultados.stream()
                                .max(Comparator.comparingDouble(ResultadoEscalamiento::throughputRegPorSeg))
                                .orElse(null);

                System.out.println("---------------------------------------------------------------");

                if (optimo != null) {
                        System.out.printf(" CONFIGURACION OPTIMA -> %s -> %.1f registros/seg%n",
                                        optimo.parametro(), optimo.throughputRegPorSeg());
                }

                System.out.println("---------------------------------------------------------------");
        }
}
