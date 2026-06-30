package com.omakase.kok.waiting.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
@EnableConfigurationProperties({
        WaitingKafkaProperties.class,
        WaitingKafkaPublisherProperties.class,
        WaitingAutoNoShowProperties.class
})
public class KafkaProducerConfig {
    @Bean // Producer 인스턴스를 생성하는 팩토리 빈
    public ProducerFactory<String, String> waitingProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties();
        return new DefaultKafkaProducerFactory<>(producerProperties);
    }

    @Bean // Kafka 메시지를 발행할 때 사용할 KafkaTemplate 빈
    public KafkaTemplate<String, String> waitingKafkaTemplate(ProducerFactory<String, String> waitingProducerFactory) {
        return new KafkaTemplate<>(waitingProducerFactory);
    }

    @Bean // 애플리케이션 시작 시 waiting.events.v1 토픽을 생성하는 빈
    public NewTopic waitingEventsTopic(WaitingKafkaProperties waitingKafkaProperties) {
        return TopicBuilder.name(waitingKafkaProperties.name())
                .partitions(waitingKafkaProperties.partitions())
                .replicas(waitingKafkaProperties.replicas())
                .build();
    }
}
