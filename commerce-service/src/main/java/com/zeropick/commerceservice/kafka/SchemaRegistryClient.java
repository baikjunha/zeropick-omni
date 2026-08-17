package com.zeropick.commerceservice.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Confluent Schema Registry REST 로 스키마를 등록하고 id 를 캐시한다. (subject = <토픽>-value)
@Component
public class SchemaRegistryClient {

    private final RestClient restClient;
    private final ConcurrentHashMap<String, Integer> idCache = new ConcurrentHashMap<>();

    public SchemaRegistryClient(@Value("${zeropick.schema-registry-url}") String registryUrl) {
        this.restClient = RestClient.builder().baseUrl(registryUrl).build();
    }

    @SuppressWarnings("unchecked")
    public int registerAndGetId(String topic, String schemaJson) {
        return idCache.computeIfAbsent(topic, t -> {
            Map<String, Object> response = restClient.post()
                    .uri("/subjects/{subject}/versions", t + "-value")
                    .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                    .body(Map.of("schema", schemaJson))
                    .retrieve()
                    .body(Map.class);
            return ((Number) response.get("id")).intValue();
        });
    }
}
