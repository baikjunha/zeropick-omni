package com.zeropick.commerceservice.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

// 자동설정 KafkaTemplate 은 <Object, Object> 라 <String, byte[]> 주입이 안 돼서 명시적으로 만든다.
// max.block.ms 를 짧게 둬서 Kafka 미기동 시 결제 API 가 블로킹되지 않게 한다.
@Configuration
public class KafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, byte[]> byteArrayKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
