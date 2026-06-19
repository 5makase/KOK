package com.omakase.kok.notification.service;

import com.omakase.kok.notification.repository.NotificationRepository;
import com.omakase.kok.notification.repository.SlackSendLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SlackSendLogRepository slackSendLogRepository;
}
