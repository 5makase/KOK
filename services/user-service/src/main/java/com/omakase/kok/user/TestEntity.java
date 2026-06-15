package com.omakase.kok.user;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "test_entities")
public class TestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
}