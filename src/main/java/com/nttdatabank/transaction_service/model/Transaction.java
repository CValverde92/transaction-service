package com.nttdatabank.transaction_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Data
@Document(collection = "transactions")
public class Transaction {
    @Id
    private String id;
    @Field("type")
    private TransactionType type;
    private Double amount;
    private String accountId;
    private String creditId;
    private String customerId;
    private String description;
    private LocalDateTime timestamp = LocalDateTime.now();
    @Field("accountType")
    private AccountType accountType; // SAVINGS, CHECKING, FIXED_TERM
    @Field("customerType")
    private CustomerType customerType; // PERSONAL, BUSINESS
}
