package com.nttdatabank.transaction_service.mapper;

import com.nttdatabank.transaction_service.model.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class TransactionMapper {

    public Transaction toEntity(TransactionRequest request) {
        if (request == null) {
            return null;
        }

        Transaction transaction = new Transaction();
        transaction.setType(TransactionType.valueOf(request.getType().name()));
        transaction.setAmount(request.getAmount().doubleValue());
        transaction.setAccountId(request.getAccountId());
        transaction.setCreditId(request.getCreditId());
        transaction.setDescription(request.getDescription());
        transaction.setAccountType(request.getAccountType());
        transaction.setCustomerType(request.getCustomerType());
        transaction.setCustomerId(request.getCustomerId());
        return transaction;
    }

    public TransactionResponse toResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        TransactionResponse response = new TransactionResponse();
        response.setId(transaction.getId());
        response.setType(transaction.getType());
        response.setAmount(BigDecimal.valueOf(transaction.getAmount()));
        response.setAccountId(transaction.getAccountId());
        response.setTimestamp(OffsetDateTime.parse(transaction.getTimestamp().toString()));
        response.setDescription(transaction.getDescription());
        return response;
    }

    public CreditPaymentResponse toPaymentResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        CreditPaymentResponse response = new CreditPaymentResponse();
        response.setId(transaction.getId());
        response.setCreditId(transaction.getCreditId());
        response.setAmount(BigDecimal.valueOf(transaction.getAmount()));
        response.setTimestamp(OffsetDateTime.parse(transaction.getTimestamp().toString()));
        return response;
    }
}
