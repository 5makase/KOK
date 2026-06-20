package com.omakase.kok.user.domain.user.repository;

import com.omakase.kok.user.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // 회원 중복 검증
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // 로그인 조회용
    Optional<User> findByUsername(String username);
}
