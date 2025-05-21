package com.nttdatabank.transaction_service.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreditDetails {
    private String creditId;
    private String customerId;
    private CreditType type; // PERSONAL, BUSINESS, CREDIT_CARD
    private BigDecimal totalLimit;
    private BigDecimal usedAmount;
    private BigDecimal availableAmount;
    private boolean active;
}
