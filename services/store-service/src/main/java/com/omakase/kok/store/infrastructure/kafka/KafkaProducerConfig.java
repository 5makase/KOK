package com.omakase.kok.store.infrastructure.kafka;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {
    // application.yml -> spring.kafka.producer 설정을 읽어 Producer 생성
    @Bean
    public ProducerFactory<String, String> storeProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(producerProperties);
    }

    // Outbox Publisher가 주입받아 Kafka 메시지 발행에 사용
    @Bean
    public KafkaTemplate<String, String> storeKafkaTemplate(ProducerFactory<String, String> storeProducerFactory) {
        return new KafkaTemplate<>(storeProducerFactory);
    }
}