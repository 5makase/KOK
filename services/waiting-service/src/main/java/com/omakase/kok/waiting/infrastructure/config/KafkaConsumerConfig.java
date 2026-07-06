package com.omakase.kok.waiting.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StoreEventDltProperties.class)
public class KafkaConsumerConfig {
    private final StoreEventDltProperties storeEventDltProperties;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> waitingKafkaTemplate
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(defaultErrorHandler(waitingKafkaTemplate));
        return factory;
    }

    private DefaultErrorHandler defaultErrorHandler(KafkaTemplate<String, String> waitingKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                waitingKafkaTemplate,
                (record, exception) -> {
                    log.error("Kafka 메시지 재처리 한도 초과. DLT로 전송. topic={}, partition={}, offset={}, dltTopic={}",
                            record.topic(), record.partition(), record.offset(), storeEventDltProperties.name(), exception);
                    return new TopicPartition(storeEventDltProperties.name(), -1);
                }
        );

        return new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(storeEventDltProperties.retryIntervalMs(), storeEventDltProperties.maxRetryAttempts())
        );
    }
}
