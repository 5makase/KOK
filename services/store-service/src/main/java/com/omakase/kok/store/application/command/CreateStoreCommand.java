package com.omakase.kok.store.application.command;

import com.omakase.kok.store.domain.vo.Address;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class CreateStoreCommand {

    private UUID ownerId;
    private UUID categoryId;
    private String name;
    private String phone;
    private Address address;
    private String description;
    private Integer maxCapacity;
}
