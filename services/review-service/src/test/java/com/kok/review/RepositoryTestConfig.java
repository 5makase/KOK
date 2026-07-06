//package com.kok.review;
//
//import com.querydsl.jpa.impl.JPAQueryFactory;
//import jakarta.persistence.EntityManager;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.boot.autoconfigure.domain.EntityScan;
//import org.springframework.boot.test.context.TestConfiguration;
//import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
//
///**
// * 리포지토리 슬라이스 테스트 전용 최소 설정.
// * 메인 클래스(ReviewServiceApplication)의 넓은 @ComponentScan / @EnableFeignClients 를
// * 물지 않도록, 엔티티와 리포지토리만 스캔하는 별도 부트 설정을 제공한다.
// */
//
//@TestConfiguration(proxyBeanMethods = false)
//@EnableAutoConfiguration
//@EntityScan(basePackages = "com.kok.review.domain.entity")
//@EnableJpaRepositories(basePackages = "com.kok.review.domain.repository")
//class RepositoryTestConfig {
//}