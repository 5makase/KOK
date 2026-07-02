package com.kok.review.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kok.review.domain.entity.ReviewOutboxEvent;
import com.kok.review.domain.repository.ReviewOutboxEventRepository;
import com.kok.review.global.exception.ReviewErrorCode;
import com.kok.review.infrastructure.messaging.dto.ReviewEventEnvelope;
import com.kok.review.infrastructure.messaging.dto.ReviewEventPayloadType;
import com.omakase.kok.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxAppender {
    private final ReviewOutboxEventRepository reviewOutboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * ReviewEventEnvelope 생성하여 outbox에 추가.
     * @param reviewId
     * @param eventType
     * @param storeId
     * @param payload
     */
    public void append(UUID reviewId, String eventType, UUID storeId, ReviewEventPayloadType payload) {
        //ReviewEventEnvelope 생성
        ReviewEventEnvelope envelope = ReviewEventEnvelope.of(eventType, payload);

        //ReviewEventEnvelope을 json으로 변환
        String json = serialize(envelope);

        //ReviewOutboxEvent를 생성하여 DB에 저장.
        reviewOutboxEventRepository.save(ReviewOutboxEvent.create(reviewId,eventType,json,storeId));
    }

    /**
     *  직렬화
     * @param envelope 카프카에 태울 데이터 틀
     * @return
     */
    private String serialize(ReviewEventEnvelope envelope) {
        try{
            //카프카에 태울 데이터 틀을 json으로 변환
            return objectMapper.writeValueAsString(envelope);
        }catch (JsonProcessingException e) {
            //안되면 예외 처리
            log.warn(e.getMessage());
            throw new BaseException(ReviewErrorCode.REPLY_ALREADY_EXISTS);
        }
    }
}
