package com.nttdatabank.transaction_service.client;

import com.nttdatabank.transaction_service.model.Customer;
import com.nttdatabank.transaction_service.model.CustomerType;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

// Feign client interface for communicating with the Customer Service
@FeignClient(name = "customer-service", path = "/api/customers")
public interface CustomerServiceClient {

    // Retrieves customer details by customer ID
    @GetMapping("/{customerId}")
    Mono<Customer> getCustomer(@PathVariable String customerId);

    // Gets the customer type (PERSONAL/BUSINESS) for a specific customer
    @GetMapping("/{customerId}/type")
    Mono<CustomerType> getCustomerType(@PathVariable String customerId);

    // Validates if a customer owns a specific account
    @GetMapping("/validate-ownership")
    Mono<Boolean> validateAccountOwnership(
            @RequestParam String accountId,
            @RequestParam String customerId);
}
