package com.zeropick.possyncservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

// 자동설정 KafkaTemplate 은 <Object, Object> 라 <String, String> 주입이 안 돼서 명시적으로 만든다.
// DLQ 발행이 컨슈머 스레드를 오래 잡지 않도록 max.block.ms 를 짧게 둔다.
@Configuration
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
