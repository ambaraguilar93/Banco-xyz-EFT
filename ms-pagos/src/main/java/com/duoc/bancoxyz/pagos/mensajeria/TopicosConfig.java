package com.duoc.bancoxyz.pagos.mensajeria;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Crea los topicos (y sus -dlt) al iniciar, si no existen. Varias particiones por topico
 * permiten escalar horizontalmente: las instancias de un mismo grupo de consumidores se
 * reparten las particiones. El DLT tiene las mismas particiones que su topico porque el
 * mensaje fallido se envia a la misma particion.
 */
@Configuration
public class TopicosConfig {

    @Bean
    public KafkaAdmin.NewTopics topicosBanco(@Value("${banco.kafka.particiones:3}") int particiones,
            @Value("${banco.kafka.replicas:1}") int replicas) {
        List<NewTopic> topicos = new ArrayList<>();
        for (String nombre : Topicos.TODOS) {
            topicos.add(TopicBuilder.name(nombre).partitions(particiones).replicas(replicas).build());
            topicos.add(TopicBuilder.name(nombre + "-dlt").partitions(particiones).replicas(replicas).build());
        }
        return new KafkaAdmin.NewTopics(topicos.toArray(NewTopic[]::new));
    }
}
