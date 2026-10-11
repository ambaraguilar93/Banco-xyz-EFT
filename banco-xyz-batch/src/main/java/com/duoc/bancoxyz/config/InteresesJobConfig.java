package com.duoc.bancoxyz.config;

import javax.sql.DataSource;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import com.duoc.bancoxyz.decider.ContinuidadDecider;
import com.duoc.bancoxyz.ejecucion.SimuladorFalloCritico;
import com.duoc.bancoxyz.integridad.VerificacionIntegridadTasklet;
import com.duoc.bancoxyz.listener.MetricasStepListener;
import com.duoc.bancoxyz.listener.PreparacionCargaListener;
import com.duoc.bancoxyz.listener.JobListener;
import com.duoc.bancoxyz.model.Interes;
import com.duoc.bancoxyz.model.InteresCalculado;
import com.duoc.bancoxyz.processor.InteresProcessor;
import com.duoc.bancoxyz.util.CampoCsv;

/**
 * 
 * InteresesJobConfig
 * Se arma el Job 2 - Calculo de intereses mensuales.
 *
 * EFT:
 * - El archivo es intereses_trimestrales.csv (fin_legacy_data, semana_3).
 * - Step 2. depurarDuplicadosStep: en el archivo nuevo una misma cuenta
 * aparece muchas veces. Una cuenta recibe interes una vez por mes, asi
 * que se conserva la primera aparicion valida y las demas pasan a
 * registros_rechazados como duplicado. Se hace en SQL al final porque
 * los 3 hilos procesan el archivo en paralelo y ninguno ve el archivo completo.
 */

@Configuration
public class InteresesJobConfig {

        private static final String NOMBRE_JOB = "calculoInteresesMensualesJob";
        private static final String TABLA = "intereses_calculados";

        // EFT: mueve a registros_rechazados las apariciones repetidas de una cuenta
        // (todas menos la de menor linea) y despues las borra de intereses_calculados.
        private static final String MOVER_DUPLICADOS = """
                        INSERT INTO registros_rechazados
                            (job_instance_id, job_name, archivo_origen, identificador, motivo, dato_original)
                        SELECT ?, ?, ?, CAST(d.cuenta_id AS VARCHAR),
                               'Registro duplicado: la cuenta ' || d.cuenta_id
                                   || ' ya recibio interes por la linea ' || p.primera_linea,
                               'linea=' || d.linea_origen || ', cuentaId=' || d.cuenta_id || ', nombre=' || d.nombre
                                   || ', saldo=' || d.saldo_inicial || ', tipo=' || d.tipo
                          FROM intereses_calculados d
                          JOIN (SELECT cuenta_id, MIN(linea_origen) AS primera_linea
                                  FROM intereses_calculados GROUP BY cuenta_id) p
                            ON p.cuenta_id = d.cuenta_id
                         WHERE d.linea_origen > p.primera_linea
                        """;

        private static final String BORRAR_DUPLICADOS = """
                        DELETE FROM intereses_calculados d
                         USING (SELECT cuenta_id, MIN(linea_origen) AS primera_linea
                                  FROM intereses_calculados GROUP BY cuenta_id) p
                         WHERE d.cuenta_id = p.cuenta_id
                           AND d.linea_origen > p.primera_linea
                        """;

        /**
         * Lector del CSV de cuentas.
         *
         * El archivo de esta semana trae una fila sin saldo y otra sin edad. Leer
         * esos campos con readBigDecimal()/readInt() reventaria el mapper; CampoCsv
         * devuelve null y deja que el processor decida y documente el rechazo.
         */
        @Bean
        public FlatFileItemReader<Interes> interesReader(
                        @Value("${banco.data.directorio}") String directorio,
                        @Value("${banco.archivo.intereses}") String archivo) {

                DelimitedLineTokenizer columnas = new DelimitedLineTokenizer(",");
                columnas.setNames("cuentaId", "nombre", "saldo", "edad", "tipo");

                DefaultLineMapper<Interes> mapeo = new DefaultLineMapper<>();
                mapeo.setLineTokenizer(columnas);
                mapeo.setFieldSetMapper(campos -> {

                        Interes interes = new Interes();
                        interes.setCuentaId(CampoCsv.entero(campos, "cuentaId"));
                        interes.setNombre(CampoCsv.texto(campos, "nombre"));
                        interes.setSaldo(CampoCsv.decimal(campos, "saldo"));
                        interes.setEdad(CampoCsv.enteroCorto(campos, "edad"));
                        interes.setTipo(CampoCsv.texto(campos, "tipo"));
                        return interes;
                });

                // EFT: mismo mapeo de antes, pero ademas se anota el numero de linea
                // (para resolver duplicados) y saveState(false) por correr con varios hilos.
                return new FlatFileItemReaderBuilder<Interes>()
                                .name("interesReader")
                                .saveState(false)
                                .resource(new ClassPathResource(directorio + "/" + archivo))
                                .encoding("UTF-8")
                                .linesToSkip(1)
                                .lineMapper((linea, numeroLinea) -> {
                                        Interes interes = mapeo.mapLine(linea, numeroLinea);
                                        interes.setLinea(numeroLinea);
                                        return interes;
                                })
                                .build();
        }

        /**
         * El escritor que guarda cada interes ya calculado en la tabla
         * intereses_calculados. La tabla usa un id propio como llave porque
         * una misma cuenta puede aparecer varias veces en el archivo.
         */
        @Bean
        public JdbcBatchItemWriter<InteresCalculado> interesWriter(DataSource dataSource) {

                return new JdbcBatchItemWriterBuilder<InteresCalculado>()
                                .dataSource(dataSource)
                                .sql("""
                                                INSERT INTO intereses_calculados
                                                    (cuenta_id, nombre, tipo, saldo_inicial, tasa_aplicada,
                                                     interes_mensual, saldo_final, linea_origen)
                                                VALUES
                                                    (:cuentaId, :nombre, :tipo, :saldoInicial, :tasaAplicada,
                                                     :interesMensual, :saldoFinal, :lineaOrigen)
                                                """)
                                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                                .build();
        }

        /**
         * Junta el reader y el writer de arriba con el InteresProcessor,
         * y le pide a FabricaStepsCarga que arme el step completo.
         */
        @Bean
        public Step calcularInteresesStep(FabricaStepsCarga fabrica,
                        JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        FlatFileItemReader<Interes> interesReader,
                        InteresProcessor interesProcessor,
                        JdbcBatchItemWriter<InteresCalculado> interesWriter,
                        JdbcTemplate jdbcTemplate,
                        @Value("${banco.archivo.intereses}") String archivo) {

                return fabrica.construir(
                                "calcularInteresesStep",
                                jobRepository,
                                transactionManager,
                                interesReader,
                                interesProcessor,
                                interesWriter,
                                NOMBRE_JOB,
                                archivo,
                                new PreparacionCargaListener(jdbcTemplate, TABLA, archivo));
        }

        /**
         * EFT - Step 2: depuracion de cuentas duplicadas (ver comentario de la clase).
         * Los rechazos y el borrado van en la misma transaccion: o se mueven
         * todos los duplicados o ninguno.
         */
        @Bean
        public Step depurarDuplicadosInteresesStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        JdbcTemplate jdbcTemplate,
                        SimuladorFalloCritico simuladorFalloCritico,
                        MetricasStepListener metricasStepListener,
                        @Value("${banco.archivo.intereses}") String archivo) {

                return new StepBuilder("depurarDuplicadosInteresesStep", jobRepository)
                                .tasklet((contribucion, contexto) -> {

                                        long instancia = contexto.getStepContext().getStepExecution()
                                                        .getJobExecution().getJobInstance().getId();

                                        int movidos = jdbcTemplate.update(MOVER_DUPLICADOS, instancia, NOMBRE_JOB,
                                                        archivo);
                                        int borrados = jdbcTemplate.update(BORRAR_DUPLICADOS);

                                        if (movidos != borrados) {
                                                throw new IllegalStateException("Depuracion inconsistente: " + movidos
                                                                + " rechazados como duplicado pero " + borrados
                                                                + " borrados");
                                        }

                                        contribucion.incrementWriteCount(borrados);
                                        System.out.printf(
                                                        "[DUPLICADOS] %d calculos repetidos de la misma cuenta pasaron a "
                                                                        + "registros_rechazados%n",
                                                        borrados);
                                        return RepeatStatus.FINISHED;
                                }, transactionManager)
                                .listener(simuladorFalloCritico)
                                .listener(metricasStepListener)
                                .build();
        }

        // EFT - Step 3: verificacion de integridad.
        @Bean
        public Step verificarIntegridadInteresesStep(JobRepository jobRepository,
                        PlatformTransactionManager transactionManager,
                        JdbcTemplate jdbcTemplate,
                        @Value("${banco.data.directorio}") String directorio,
                        @Value("${banco.archivo.intereses}") String archivo) {

                return new StepBuilder("verificarIntegridadInteresesStep", jobRepository)
                                .tasklet(new VerificacionIntegridadTasklet(jdbcTemplate, transactionManager,
                                                new ClassPathResource(directorio + "/" + archivo), archivo, TABLA),
                                                transactionManager)
                                .build();
        }

        /**
         * El ContinuidadDecider decide entre dos caminos: si se cargo algo util
         * se sigue (EFT: a la depuracion de duplicados y la verificacion de
         * integridad), si no se guardo nada, el Job se detiene marcando el error.
         */
        @Bean
        public Job calculoInteresesMensualesJob(JobRepository jobRepository,
                        @Qualifier("calcularInteresesStep") Step calcularInteresesStep,
                        @Qualifier("depurarDuplicadosInteresesStep") Step depurarDuplicadosStep,
                        @Qualifier("verificarIntegridadInteresesStep") Step verificarIntegridadStep,
                        ContinuidadDecider continuidadDecider,
                        JobListener jobListener) {

                return new JobBuilder(NOMBRE_JOB, jobRepository)
                                .incrementer(new RunIdIncrementer())
                                .listener(jobListener)
                                .start(calcularInteresesStep)
                                .next(continuidadDecider)
                                .on(ContinuidadDecider.SIN_DATOS).fail()
                                .from(continuidadDecider)
                                .on("*").to(depurarDuplicadosStep)
                                .next(verificarIntegridadStep)
                                .end()
                                .build();
        }
}