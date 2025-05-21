package com.nttdatabank.transaction_service.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Account {
    private String id;
    private AccountType type;
    private boolean active;
    private boolean locked;
    private double balance;
    private String customerId;
    private LocalDateTime createdAt;
}
