package com.nttdatabank.transaction_service.repository;

import com.nttdatabank.transaction_service.model.Transaction;
import com.nttdatabank.transaction_service.model.TransactionType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

// Reactive MongoDB repository for Transaction entities
@Repository
public interface TransactionRepository extends ReactiveMongoRepository<Transaction, String> {
    // Finds all transactions associated with a specific account ID
    Flux<Transaction> findByAccountId(String accountId);

    // Finds transactions by credit ID and transaction type
    Flux<Transaction> findByCreditIdAndType(String creditId, String type);

    // Counts transactions for an account within a specific date range
    Mono<Long> countTransactionsByAccountIdAndTimestampBetween(String accountId, LocalDateTime startDate, LocalDateTime endDate);

    // Finds all transactions for a specific customer
    Flux<Transaction> findByCustomerId(String customerId);

    // Counts transactions of a specific type for an account within a date range
    Mono<Long> countTransactionsByAccountIdAndTimestampBetween(String accountId, TransactionType type, LocalDateTime start, LocalDateTime end);

}
