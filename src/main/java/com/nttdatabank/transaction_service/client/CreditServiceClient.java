package com.nttdatabank.transaction_service.client;

import com.nttdatabank.transaction_service.model.CreditDetails;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

// Feign client interface for communicating with the Credit Service
@FeignClient(name = "credit-service", path = "/api/credits")
public interface CreditServiceClient {

    // Gets detailed information about a specific credit
    @GetMapping("/{creditId}/details")
    Mono<CreditDetails> getCreditDetails(@PathVariable String creditId);

    // Retrieves the available credit limit for a credit account
    @GetMapping("/{creditId}/available-limit")
    Mono<Double> getAvailableCreditLimit(@PathVariable String creditId);

    // Counts active credits for a customer (optionally filtered by credit type)
    @GetMapping("/customer/{customerId}/count")
    Mono<Integer> countActiveCreditsByCustomer(
            @PathVariable String customerId,
            @RequestParam(required = false) String creditType);
}
