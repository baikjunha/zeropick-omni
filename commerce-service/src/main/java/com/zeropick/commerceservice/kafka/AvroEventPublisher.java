package com.zeropick.commerceservice.kafka;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AvroEventPublisher {

    public static final String TOPIC_VIEWED = "product-viewed";
    public static final String TOPIC_CART = "cart-added";
    public static final String TOPIC_ORDER = "order-completed";

    private static final Logger log = LoggerFactory.getLogger(AvroEventPublisher.class);

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final SchemaRegistryClient registryClient;
    private final Map<String, Schema> schemas;

    public AvroEventPublisher(KafkaTemplate<String, byte[]> kafkaTemplate,
                              SchemaRegistryClient registryClient) throws IOException {
        this.kafkaTemplate = kafkaTemplate;
        this.registryClient = registryClient;
        this.schemas = Map.of(
                TOPIC_VIEWED, load("avro/product-viewed.avsc"),
                TOPIC_CART, load("avro/cart-added.avsc"),
                TOPIC_ORDER, load("avro/order-completed.avsc"));
    }

    public void publishProductViewed(long memberId, long productId, String category, long occurredAt) {
        GenericRecord record = new GenericData.Record(schemas.get(TOPIC_VIEWED));
        record.put("memberId", memberId);
        record.put("productId", productId);
        record.put("category", category);
        record.put("occurredAt", occurredAt);
        publish(TOPIC_VIEWED, memberId, record);
    }

    public void publishCartAdded(long memberId, long productId, String category, int qty, long occurredAt) {
        GenericRecord record = new GenericData.Record(schemas.get(TOPIC_CART));
        record.put("memberId", memberId);
        record.put("productId", productId);
        record.put("category", category);
        record.put("qty", qty);
        record.put("occurredAt", occurredAt);
        publish(TOPIC_CART, memberId, record);
    }

    public void publishOrderCompleted(long memberId, long productId, String category, int qty,
                                      long unitPrice, String orderNo, String paymentMethod, long occurredAt) {
        GenericRecord record = new GenericData.Record(schemas.get(TOPIC_ORDER));
        record.put("memberId", memberId);
        record.put("productId", productId);
        record.put("category", category);
        record.put("qty", qty);
        record.put("unitPrice", unitPrice);
        record.put("orderNo", orderNo);
        record.put("paymentMethod", paymentMethod);
        record.put("occurredAt", occurredAt);
        publish(TOPIC_ORDER, memberId, record);
    }

    private void publish(String topic, long memberId, GenericRecord record) {
        try {
            int schemaId = registryClient.registerAndGetId(topic, schemas.get(topic).toString());
            byte[] payload = serialize(record, schemaId);
            kafkaTemplate.send(topic, String.valueOf(memberId), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("이벤트 발행 실패 topic={} memberId={}: {}", topic, memberId, ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("이벤트 발행 스킵 topic={} memberId={}: {}", topic, memberId, e.getMessage());
        }
    }

    private byte[] serialize(GenericRecord record, int schemaId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0);
        out.write(ByteBuffer.allocate(4).putInt(schemaId).array());
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        new GenericDatumWriter<GenericRecord>(record.getSchema()).write(record, encoder);
        encoder.flush();
        return out.toByteArray();
    }

    private static Schema load(String path) throws IOException {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new Schema.Parser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
