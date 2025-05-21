package com.nttdatabank.transaction_service.client;

import com.nttdatabank.transaction_service.model.Account;
import com.nttdatabank.transaction_service.model.AccountType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

// Feign client interface for communicating with the Account Service
@FeignClient(name = "account-service", path = "/api/accounts")
public interface AccountServiceClient {

    // Retrieves account details by account ID
    @GetMapping("/{accountId}")
    Mono<Account> getAccount(@PathVariable String accountId);

    // Counts accounts of specific type for a customer
    @GetMapping("/customer/{customerId}/count")
    Mono<Integer> countAccountsByCustomerAndType(
            @PathVariable String customerId,
            @RequestParam AccountType type);

    // Gets current balance for an account
    @GetMapping("/{accountId}/balance")
    Mono<Double> getCurrentBalance(@PathVariable String accountId);

    // Checks if an account exists
    @GetMapping("/{accountId}/exists")
    Mono<Boolean> accountExists(@PathVariable String accountId);
}
