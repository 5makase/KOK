package com.kok.review.domain.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReviewRepositoryCustomImpl implements  ReviewRepositoryCustom {
    private final JPAQueryFactory queryFactory;
}
