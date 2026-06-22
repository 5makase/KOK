package com.omakase.kok.notification.service;

import com.omakase.kok.notification.enums.NotificationType;
import com.omakase.kok.notification.enums.ReferenceType;
import com.omakase.kok.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationSaveService notificationSaveService;
    private final SlackSendService slackSendService;

    public void process(NotificationEvent event) {
        NotificationType notificationType = NotificationType.valueOf(event.getEventType());
        ReferenceType referenceType = ReferenceType.valueOf(event.getReferenceType());

        NotificationSaveService.SaveResult result;
        try {
            result = notificationSaveService.save(event, notificationType, referenceType);
        } catch (DataIntegrityViolationException e) {
            log.info("[NotificationEventService] 중복 이벤트 skip. referenceId={}, type={}",
                    event.getReferenceId(), event.getEventType());
            return;
        }

        slackSendService.send(
                event.getUserId(),
                result.notification().getMessage(),
                result.slackSendLog()
        );
    }
}
