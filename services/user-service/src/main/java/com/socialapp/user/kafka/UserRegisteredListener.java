package com.socialapp.user.kafka;

import com.socialapp.common.event.KafkaTopics;
import com.socialapp.common.event.UserRegisteredEvent;
import com.socialapp.user.entity.Gender;
import com.socialapp.user.entity.UserProfile;
import com.socialapp.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredListener {

    private final UserProfileRepository userProfileRepository;

    @KafkaListener(topics = KafkaTopics.USER_REGISTERED, groupId = "${spring.kafka.consumer.group-id}")
    public void onUserRegistered(UserRegisteredEvent event) {
        if (userProfileRepository.existsById(event.userId())) {
            log.info("UserProfile already exists for id={}, skipping (idempotent redelivery)", event.userId());
            return;
        }
        Instant now = Instant.now();
        UserProfile profile = UserProfile.builder()
                .id(event.userId())
                .fullName(event.fullName())
                .gender(parseGender(event.gender()))
                .dob(event.dob())
                .createdAt(now)
                .updatedAt(now)
                .build();
        userProfileRepository.save(profile);
        log.info("Created UserProfile for id={}", event.userId());
    }

    private Gender parseGender(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Gender.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring unrecognized gender value at registration: {}", raw);
            return null;
        }
    }
}
