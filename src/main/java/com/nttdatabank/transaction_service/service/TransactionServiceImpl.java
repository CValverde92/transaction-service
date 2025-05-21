package com.nttdatabank.transaction_service.service;

import com.nttdatabank.transaction_service.client.AccountServiceClient;
import com.nttdatabank.transaction_service.client.CreditServiceClient;
import com.nttdatabank.transaction_service.client.CustomerServiceClient;
import com.nttdatabank.transaction_service.exception.BusinessRuleException;
import com.nttdatabank.transaction_service.model.*;
import com.nttdatabank.transaction_service.repository.TransactionRepository;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.webjars.NotFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;

@Service
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final CustomerServiceClient customerServiceClient;
    private final CreditServiceClient creditServiceClient;
    private final AccountServiceClient accountServiceClient;

    // Constructor for dependency injection
    public TransactionServiceImpl(TransactionRepository transactionRepository, CustomerServiceClient customerServiceClient, CreditServiceClient creditServiceClient, AccountServiceClient accountServiceClient) {
        this.transactionRepository = transactionRepository;
        this.customerServiceClient = customerServiceClient;
        this.creditServiceClient = creditServiceClient;
        this.accountServiceClient = accountServiceClient;
    }

    // Main transaction creation method with comprehensive validation pipeline
    @Override
    public Mono<Transaction> createTransaction(Transaction transaction) {
        return Mono.just(transaction)
                // asic data validation
                .flatMap(this::validateTransactionData)

                // Credit validations (if transaction involves credit)
                .flatMap(tx -> {
                    if (tx.getCreditId() == null) {
                        return Mono.just(tx);
                    }

                    return creditServiceClient.getCreditDetails(tx.getCreditId())
                            .flatMap(creditDetails -> {
                                // Validate credit status
                                if (!creditDetails.isActive()) {
                                    return Mono.error(new BusinessRuleException("Credit is not active"));
                                }

                                // Validate available credit limit for card charges
                                if (tx.getType() == TransactionType.CREDIT_CARD_CHARGE) {
                                    return creditServiceClient.getAvailableCreditLimit(tx.getCreditId())
                                            .flatMap(availableLimit -> {
                                                if (availableLimit < tx.getAmount()) {
                                                    return Mono.error(new BusinessRuleException(
                                                            "nsufficient credit limit. Available: " + availableLimit));
                                                }
                                                return Mono.just(tx);
                                            });
                                }

                                return Mono.just(tx);
                            });
                })

                // 3. Validate personal customer credit limit rules
                .flatMap(tx -> {
                    if (tx.getCustomerType() == CustomerType.PERSONAL &&
                            tx.getType() == TransactionType.CREDIT_PAYMENT) {
                        return creditServiceClient.countActiveCreditsByCustomer(tx.getCustomerId(), CustomerType.PERSONAL.name())
                                .flatMap(count -> {
                                    if (count >= 1) {
                                        return Mono.error(new BusinessRuleException(
                                                "Personal customers can only have one active credit"));
                                    }
                                    return Mono.just(tx);
                                });
                    }
                    return Mono.just(tx);
                })

                // Account-related validations
                .flatMap(tx -> accountServiceClient.getAccount(tx.getAccountId())
                        .switchIfEmpty(Mono.error(new BusinessRuleException("La cuenta no existe")))
                        .flatMap(account -> {
                            tx.setAccountType(account.getType());
                            return validateAccountStatus(account)
                                    .then(validateSufficientBalance(tx, account))
                                    .thenReturn(tx);
                        }))

                // Customer-related validations
                .flatMap(tx -> customerServiceClient.getCustomer(tx.getCustomerId())
                        .switchIfEmpty(Mono.error(new BusinessRuleException("Customer does not exist")))
                        .flatMap(customer -> {
                            tx.setCustomerType(customer.getType());
                            return validateCustomerAccountCompatibility(tx, customer);
                        }))

                // Final processing steps
                .then(validateAccountSpecificRules(transaction))
                .then(applyMaintenanceFeeIfApplicable(transaction))
                .then(transactionRepository.save(transaction))
                .onErrorMap(this::handleTransactionError);
    }

    // Validates compatibility between customer type and account type
    private Mono<Void> validateCustomerAccountCompatibility(Transaction tx, Customer customer) {
        if (customer.getType() == CustomerType.BUSINESS &&
                (tx.getAccountType() == AccountType.SAVINGS || tx.getAccountType() == AccountType.FIXED_TERM)) {
            return Mono.error(new BusinessRuleException(
                    "Business customers cannot have savings or fixed-term accounts"));
        }

        // Personal customers can only have one account of each type
        if (customer.getType() == CustomerType.PERSONAL) {
            return accountServiceClient.countAccountsByCustomerAndType(
                            customer.getId(),
                            tx.getAccountType())
                    .flatMap(count -> {
                        if (count > 0) {
                            return Mono.error(new BusinessRuleException(
                                    "Personal customers can only have one account of this type"));
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }

    // Retrieves a transaction by its unique identifier
    @Override
    public Mono<Transaction> getTransactionById(String id) {
        return transactionRepository.findById(id);
    }

    // Retrieves all transactions in the system
    @Override
    public Flux<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    // Retrieves all transactions for a specific account
    @Override
    public Flux<Transaction> getTransactionsByAccount(String accountId) {
        return transactionRepository.findByAccountId(accountId);
    }

    // Retrieves all payment transactions for a specific credit
    @Override
    public Flux<Transaction> getPaymentsByCredit(String creditId) {
        return transactionRepository.findByCreditIdAndType(creditId, TransactionType.CREDIT_PAYMENT.name());
    }

    // Deletes a transaction by its ID
    @Override
    public Mono<Void> deleteTransaction(String id) {
        return transactionRepository.deleteById(id);
    }

    // Updates an existing transaction with validation
    @Override
    public Mono<Transaction> updateTransaction(String id, Transaction transaction) {
        return transactionRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("transaction not found")))
                .flatMap(existing -> {
                    // Validate positive amount
                    if (transaction.getAmount() != null && transaction.getAmount() <= 0) {
                        return Mono.error(new IllegalArgumentException("Amount must be positive"));
                    }

                    // Update allowed fields
                    if (transaction.getAmount() != null) {
                        existing.setAmount(transaction.getAmount());
                    }
                    if (transaction.getDescription() != null) {
                        existing.setDescription(transaction.getDescription());
                    }

                    // Validate against business rules before saving
                    return validateTransactionRules(existing)
                            .then(transactionRepository.save(existing));
                });
    }

    // Retrieves all transactions for a specific customer
    // Returns transactions sorted by timestamp (newest first)
    @Override
    public Flux<Transaction> getTransactionsByCustomer(String customerId) {
        if (customerId == null || customerId.trim().isEmpty()) {
            return Flux.error(new IllegalArgumentException("Customer ID cannot be empty"));
        }
        return transactionRepository.findByCustomerId(customerId)
                .switchIfEmpty(Flux.error(new NotFoundException("No transactions found for specified customer")))
                .sort(Comparator.comparing(Transaction::getTimestamp).reversed()); // Ordenar por fecha descendente
    }

    // Retrieves filtered transactions with multiple criteria
    // Supports filtering by account type and date range
    @Override
    public Flux<Transaction> getFilteredTransactions(
            String customerId,
            String accountType,
            LocalDate startDate,
            LocalDate endDate) {

        // Validate date range
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            return Flux.error(new IllegalArgumentException("Start date cannot be after end date"));
        }

        return transactionRepository.findByCustomerId(customerId)
                .filter(transaction -> filterByAccountType(transaction, accountType))
                .filter(transaction -> filterByDateRange(transaction, startDate, endDate))
                .switchIfEmpty(Flux.error(new NotFoundException("No transactions found for customer")));
    }

    // Helper method to filter transactions by account type
    // Returns true if account type matches or filter is not specified
    private boolean filterByAccountType(Transaction transaction, String accountType) {
        return accountType == null ||
                accountType.equalsIgnoreCase(transaction.getAccountType().name());
    }

    // Helper method to filter transactions by date range
    // Includes transactions on the end date
    private boolean filterByDateRange(Transaction transaction, LocalDate startDate, LocalDate endDate) {
        LocalDateTime transactionTime = transaction.getTimestamp();

        boolean afterStart = startDate == null ||
                !transactionTime.isBefore(startDate.atStartOfDay());

        boolean beforeEnd = endDate == null ||
                transactionTime.isBefore(endDate.plusDays(1).atStartOfDay());

        return afterStart && beforeEnd;
    }

    // Validates transaction against account-specific rules
    // Delegates to appropriate validator based on account type
    private Mono<Void> validateTransactionRules(Transaction transaction) {
        switch (transaction.getAccountType()) {
            case SAVINGS:
                return validateSavingsAccount(transaction);
            case FIXED_TERM:
                return validateFixedTermAccount(transaction);
            case CHECKING:
                return Mono.empty();
            default:
                return Mono.error(new IllegalArgumentException("Invalid account type"));
        }
    }

    // Validates savings account specific rules
    // Enforces monthly withdrawal limit (5 per month)
    private Mono<Void> validateSavingsAccount(Transaction transaction) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

        return transactionRepository.countTransactionsByAccountIdAndTimestampBetween(
                        transaction.getAccountId(),
                        TransactionType.WITHDRAWAL,
                        monthStart,
                        now
                )
                .flatMap(count -> {
                    if (count >= 5) {
                        return Mono.error(new BusinessRuleException("Monthly withdrawal limit reached for savings account"));
                    }
                    return Mono.empty();
                });
    }

    // Validates fixed-term account specific rules
    // Only allows transactions on the 5th day of each month
    private Mono<Void> validateFixedTermAccount(Transaction transaction) {
        LocalDateTime now = LocalDateTime.now();
        if (now.getDayOfMonth() != 5) {
            return Mono.error(new BusinessRuleException("Fixed-term account only allows transactions on the 5th of each month"));
        }
        return Mono.empty();
    }

    // Validates customer-account type compatibility
    // Business customers cannot have savings/fixed-term accounts
    private Mono<Void> validateCustomerAccountType(Transaction transaction) {
        return customerServiceClient.getCustomerType(transaction.getCustomerId())
                .flatMap(customerType -> {
                    if (customerType == CustomerType.BUSINESS &&
                            (transaction.getAccountType() == AccountType.SAVINGS ||
                                    transaction.getAccountType() == AccountType.FIXED_TERM)) {
                        return Mono.error(new BusinessRuleException(
                                "Business customers cannot have savings or fixed-term accounts"));
                    }
                    return Mono.empty();
                });
    }

    // Validates basic transaction data
    // Ensures positive amount and required transaction type
    private Mono<Transaction> validateTransactionData(Transaction transaction) {
        if (transaction.getAmount() <= 0) {
            return Mono.error(new BusinessRuleException("Amount must be positive"));
        }
        if (transaction.getType() == null) {
            return Mono.error(new BusinessRuleException("Transaction type is required"));
        }
        return Mono.just(transaction);
    }

    // Validates account-specific rules based on account type
    private Mono<Void> validateAccountSpecificRules(Transaction transaction) {
        switch (transaction.getAccountType()) {
            case SAVINGS:
                return validateSavingsAccountRules(transaction);
            case FIXED_TERM:
                return validateFixedTermAccountRules(transaction);
            case CHECKING:
                return Mono.empty();
            default:
                return Mono.error(new BusinessRuleException("Invalid account type"));
        }
    }

    // Validates savings account withdrawal limits
    private Mono<Void> validateSavingsAccountRules(Transaction transaction) {
        if (transaction.getType() == TransactionType.WITHDRAWAL) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);

            return transactionRepository.countTransactionsByAccountIdAndTimestampBetween(
                            transaction.getAccountId(),
                            TransactionType.WITHDRAWAL,
                            monthStart,
                            now)
                    .flatMap(count -> {
                        if (count >= 5) { // 5 withdrawals/month limit
                            return Mono.error(new BusinessRuleException(
                                    "Monthly withdrawal limit reached for savings account"));
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }

    // Validates fixed-term account transaction rules
    // Only allows transactions on the 5th and limits to one per day
    private Mono<Void> validateFixedTermAccountRules(Transaction transaction) {
        if (transaction.getAccountType() != AccountType.FIXED_TERM) {
            return Mono.empty();
        }

        // Validate 5th day rule
        if (LocalDate.now().getDayOfMonth() != 5) {
            return Mono.error(new BusinessRuleException(
                    "Fixed-term account only allows transactions on the 5th"));
        }

        // Validate one transaction per day limit
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        // Validate one transaction per day limit
        return transactionRepository.countTransactionsByAccountIdAndTimestampBetween(
                        transaction.getAccountId(),
                        startOfDay,
                        endOfDay)
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new BusinessRuleException(
                                "Only one daily transaction allowed for fixed-term accounts"));
                    }
                    return Mono.empty();
                });
    }

    // Applies monthly maintenance fee for checking accounts
    // Only processes on the 1st day of each month
    private Mono<Void> applyMaintenanceFeeIfApplicable(Transaction transaction) {
        if (transaction.getAccountType() == AccountType.CHECKING &&
                LocalDate.now().getDayOfMonth() == 1) {

            Transaction fee = new Transaction();
            fee.setAccountId(transaction.getAccountId());
            fee.setType(TransactionType.FEE);
            fee.setAmount(10.0);
            fee.setDescription("Monthly checking account maintenance fee");

            return transactionRepository.save(fee).then();
        }
        return Mono.empty();
    }

    // Credit service related validation methods
    // Validates credit-related transaction rules
    private Mono<Void> validateCreditRules(Transaction transaction) {
        if (transaction.getType() == TransactionType.CREDIT_CARD_CHARGE) {
            return validateCreditCardCharge(transaction);
        } else if (transaction.getType() == TransactionType.CREDIT_PAYMENT) {
            return validateCreditPayment(transaction);
        }
        return Mono.empty();
    }

    // Validates credit card charge against available limit
    private Mono<Void> validateCreditCardCharge(Transaction transaction) {
        return creditServiceClient.getAvailableCreditLimit(transaction.getCreditId())
                .flatMap(availableLimit -> {
                    if (availableLimit < transaction.getAmount()) {
                        return Mono.error(new BusinessRuleException(
                                "Insufficient credit limit. Available: " + availableLimit));
                    }
                    return Mono.empty();
                });
    }

    // Validates credit payment conditions
    private Mono<Void> validateCreditPayment(Transaction transaction) {
        return creditServiceClient.getCreditDetails(transaction.getCreditId())
                .flatMap(creditDetails -> {
                    if (!creditDetails.isActive()) {
                        return Mono.error(new BusinessRuleException("Credit is not active"));
                    }
                    if (creditDetails.getUsedAmount().doubleValue() <= 0) {
                        return Mono.error(new BusinessRuleException("Credit has no pending balance"));
                    }
                    return Mono.empty();
                });
    }

    // Validates personal customer credit limit (1 active credit max)
    private Mono<Void> validatePersonalCreditLimit(String customerId) {
        return creditServiceClient.countActiveCreditsByCustomer(customerId, "PERSONAL_LOAN")
                .flatMap(count -> {
                    if (count >= 1) {
                        return Mono.error(new BusinessRuleException(
                                "Personal customers can only have one active credit"));
                    }
                    return Mono.empty();
                });
    }

    // Account service related validation methods
    // Validates account status (active/unlocked)
    private Mono<Void> validateAccountStatus(Account account) {
        if (!account.isActive()) {
            return Mono.error(new BusinessRuleException("Account is not active"));
        }
        if (account.getType() == AccountType.FIXED_TERM && account.isLocked()) {
            return Mono.error(new BusinessRuleException("Fixed-term account is locked for transactions"));
        }
        return Mono.empty();
    }

    // Validates sufficient balance for withdrawal/transfer transactions
    private Mono<Void> validateSufficientBalance(Transaction transaction, Account account) {
        if (transaction.getType() == TransactionType.WITHDRAWAL ||
                transaction.getType() == TransactionType.TRANSFER_OUT) {
            return accountServiceClient.getCurrentBalance(account.getId())
                    .flatMap(balance -> {
                        if (balance < transaction.getAmount()) {
                            return Mono.error(new BusinessRuleException(
                                    "Insufficient balance. Available" + balance));
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }

    // Translates various exceptions into appropriate business exceptions
    private Throwable handleTransactionError(Throwable e) {
        if (e instanceof FeignException.NotFound) {
            return new BusinessRuleException("Resource not found in external service");
        }
        if (e instanceof FeignException.BadRequest) {
            return new BusinessRuleException("Invalid data in external service");
        }
        if (e instanceof FeignException.ServiceUnavailable) {
            return new BusinessRuleException("External service temporarily unavailable");
        }
        return e instanceof BusinessRuleException ? e :
                new BusinessRuleException("Error processing transaction: " + e.getMessage());
    }
}
