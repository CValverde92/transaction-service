package com.nttdatabank.transaction_service.model;

import lombok.Data;

@Data
public class Customer {
    private String id;
    private CustomerType type; // PERSONAL o BUSINESS
    private String name;
    private String documentNumber;
    private boolean active;
}
