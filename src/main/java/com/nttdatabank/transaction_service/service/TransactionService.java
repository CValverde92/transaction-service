package com.nttdatabank.transaction_service.service;

import com.nttdatabank.transaction_service.model.Transaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface TransactionService {
    Mono<Transaction> createTransaction(Transaction transaction);

    Mono<Transaction> getTransactionById(String id);

    Flux<Transaction> getAllTransactions();

    Flux<Transaction> getTransactionsByAccount(String accountId);

    Flux<Transaction> getPaymentsByCredit(String creditId);

    Mono<Void> deleteTransaction(String id);

    Mono<Transaction> updateTransaction(String id, Transaction transaction);

    Flux<Transaction> getTransactionsByCustomer(String customerId);

    Flux<Transaction> getFilteredTransactions(String customerId, String accountType, LocalDate startDate, LocalDate endDate);

}
