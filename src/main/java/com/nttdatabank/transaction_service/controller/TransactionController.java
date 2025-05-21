package com.nttdatabank.transaction_service.controller;

import com.nttdatabank.transaction_service.api.TransactionsApi;
import com.nttdatabank.transaction_service.mapper.TransactionMapper;
import com.nttdatabank.transaction_service.model.CreditPaymentResponse;
import com.nttdatabank.transaction_service.model.Transaction;
import com.nttdatabank.transaction_service.model.TransactionRequest;
import com.nttdatabank.transaction_service.model.TransactionResponse;
import com.nttdatabank.transaction_service.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.webjars.NotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController implements TransactionsApi {
    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    // Creates a new transaction from the provided request
    @Override
    public Mono<ResponseEntity<TransactionResponse>> createTransaction(
            Mono<TransactionRequest> transactionRequest,
            final ServerWebExchange exchange) {
        return transactionRequest
                .map(transactionMapper::toEntity)
                .flatMap(transactionService::createTransaction)
                .map(transactionMapper::toResponse)
                .map(response -> ResponseEntity
                        .created(URI.create("/api/transactions/" + response.getId()))
                        .body(response));
    }

    // Deletes a transaction by its ID
    @Override
    public Mono<ResponseEntity<Void>> deleteTransaction(String id, final ServerWebExchange exchange) {
        return transactionService.deleteTransaction(id)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    // Retrieves all transactions in the system
    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getAllTransactions(final ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(
                transactionService.getAllTransactions().map(transactionMapper::toResponse)
        ));
    }

    // Retrieves all credit payment transactions for a specific credit
    @Override
    public Mono<ResponseEntity<Flux<CreditPaymentResponse>>> getPaymentsByCredit(
            String creditId,
            final ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(
                transactionService.getPaymentsByCredit(creditId).map(transactionMapper::toPaymentResponse)
        ));
    }

    // Retrieves a single transaction by its ID
    @Override
    public Mono<ResponseEntity<TransactionResponse>> getTransactionById(
            String id,
            final ServerWebExchange exchange) {
        return transactionService.getTransactionById(id)
                .map(transactionMapper::toResponse)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    // Retrieves all transactions for a specific account
    @Override
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getTransactionsByAccount(
            String accountId,
            final ServerWebExchange exchange) {
        return Mono.just(ResponseEntity.ok(
                transactionService.getTransactionsByAccount(accountId).map(transactionMapper::toResponse)
        ));
    }

    // Updates an existing transaction with new data
    @Override
    public Mono<ResponseEntity<TransactionResponse>> updateTransaction(
            String id,
            Mono<TransactionRequest> transactionRequest,
            final ServerWebExchange exchange) {
        return transactionRequest
                .map(transactionMapper::toEntity)
                .flatMap(transaction -> transactionService.updateTransaction(id, transaction))
                .map(transactionMapper::toResponse)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    if (e instanceof NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // Retrieves all transactions for a specific customer
    @GetMapping("/customer/{customerId}")
    public Flux<TransactionResponse> getTransactionsByCustomer(@PathVariable String customerId) {
        return transactionService.getTransactionsByCustomer(customerId)
                .map(transactionMapper::toResponse);
    }

    // Retrieves filtered transactions for a customer with optional filters
    @GetMapping("/customers/{customerId}/transactions")
    public Mono<ResponseEntity<Flux<TransactionResponse>>> getCustomerTransactions(
            @PathVariable String customerId,
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            final ServerWebExchange exchange) {

        // Business logic delegated to service layer
        Flux<Transaction> transactions = transactionService.getFilteredTransactions(
                customerId,
                accountType,
                startDate,
                endDate);

        return Mono.just(ResponseEntity.ok(transactions.map(transactionMapper::toResponse)))
                .onErrorResume(e -> {
                    if (e instanceof NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
