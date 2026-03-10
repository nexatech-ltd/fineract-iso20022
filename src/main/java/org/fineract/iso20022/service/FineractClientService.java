package org.fineract.iso20022.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.FineractApiException;
import org.fineract.iso20022.model.dto.FineractTransaction;
import org.fineract.iso20022.model.dto.InternalPaymentInstruction;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineractClientService {

    private final WebClient fineractWebClient;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    private static final DateTimeFormatter FINERACT_DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String executeDeposit(String savingsAccountId, BigDecimal amount, String currency,
                                  String note) {
        log.info("Executing deposit to account {}: {} {}", savingsAccountId, amount, currency);
        Map<String, Object> body = buildTransactionBody(amount, note, "ISO 20022 credit transfer");

        try {
            String response = fineractWebClient.post()
                    .uri("/savingsaccounts/{accountId}/transactions?command=deposit", savingsAccountId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            cacheService.evictAccountCache(savingsAccountId);
            String txnId = extractResourceId(response);
            log.info("Deposit successful: account={}, txnId={}", savingsAccountId, txnId);
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/deposit", "SUCCESS", "Deposit txn=" + txnId + ", amount=" + amount);
            return txnId;
        } catch (WebClientResponseException e) {
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/deposit", "FAILED", e.getMessage());
            throw new FineractApiException("Deposit failed for account " + savingsAccountId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/deposit", "FAILED", e.getMessage());
            throw new FineractApiException("Deposit failed for account " + savingsAccountId, e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String executeWithdrawal(String savingsAccountId, BigDecimal amount, String currency,
                                     String note) {
        log.info("Executing withdrawal from account {}: {} {}", savingsAccountId, amount, currency);
        Map<String, Object> body = buildTransactionBody(amount, note, "ISO 20022 debit transfer");

        try {
            String response = fineractWebClient.post()
                    .uri("/savingsaccounts/{accountId}/transactions?command=withdrawal", savingsAccountId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            cacheService.evictAccountCache(savingsAccountId);
            String txnId = extractResourceId(response);
            log.info("Withdrawal successful: account={}, txnId={}", savingsAccountId, txnId);
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/withdrawal", "SUCCESS", "Withdrawal txn=" + txnId + ", amount=" + amount);
            return txnId;
        } catch (WebClientResponseException e) {
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/withdrawal", "FAILED", e.getMessage());
            throw new FineractApiException("Withdrawal failed for account " + savingsAccountId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/withdrawal", "FAILED", e.getMessage());
            throw new FineractApiException("Withdrawal failed for account " + savingsAccountId, e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String executeTransfer(InternalPaymentInstruction instruction) {
        String fromAcctId = instruction.getDebtorAccountOther();
        String toAcctId = instruction.getCreditorAccountOther();
        log.info("Executing transfer: {} -> {}, {} {}",
                fromAcctId, toAcctId, instruction.getAmount(), instruction.getCurrency());

        Map<String, Object> fromAcct = getSavingsAccountInfo(fromAcctId);
        Map<String, Object> toAcct = getSavingsAccountInfo(toAcctId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fromOfficeId", fromAcct.get("officeId"));
        body.put("fromClientId", fromAcct.get("clientId"));
        body.put("fromAccountId", parseAccountId(fromAcctId));
        body.put("fromAccountType", 2);
        body.put("toOfficeId", toAcct.get("officeId"));
        body.put("toClientId", toAcct.get("clientId"));
        body.put("toAccountId", parseAccountId(toAcctId));
        body.put("toAccountType", 2);
        body.put("transferDate", LocalDate.now().format(FINERACT_DATE_FMT));
        body.put("transferAmount", instruction.getAmount());
        body.put("transferDescription",
                instruction.getRemittanceInfo() != null ? instruction.getRemittanceInfo() : "ISO 20022 transfer");
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");

        try {
            String response = fineractWebClient.post()
                    .uri("/accounttransfers")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            cacheService.evictAccountCache(fromAcctId);
            cacheService.evictAccountCache(toAcctId);
            String txnId = extractResourceId(response);
            log.info("Transfer successful: txnId={}", txnId);
            auditService.logFineractApiCall("POST", "/accounttransfers", "SUCCESS", "Transfer txn=" + txnId);
            return txnId;
        } catch (WebClientResponseException e) {
            auditService.logFineractApiCall("POST", "/accounttransfers", "FAILED", e.getMessage());
            throw new FineractApiException("Transfer failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            auditService.logFineractApiCall("POST", "/accounttransfers", "FAILED", e.getMessage());
            throw new FineractApiException("Transfer failed", e);
        }
    }

    private Map<String, Object> getSavingsAccountInfo(String savingsAccountId) {
        try {
            String acctResp = fineractWebClient.get()
                    .uri("/savingsaccounts/{id}", savingsAccountId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode acctNode = objectMapper.readTree(acctResp);
            int clientId = acctNode.path("clientId").asInt(0);

            int officeId = 1;
            if (clientId > 0) {
                try {
                    String clientResp = fineractWebClient.get()
                            .uri("/clients/{id}", clientId)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();
                    officeId = objectMapper.readTree(clientResp).path("officeId").asInt(1);
                } catch (Exception e) {
                    log.debug("Could not fetch client {} office: {}", clientId, e.getMessage());
                }
            }
            return Map.of("clientId", clientId, "officeId", officeId);
        } catch (Exception e) {
            log.warn("Failed to fetch account info for {}: {}", savingsAccountId, e.getMessage());
            return Map.of("clientId", 0, "officeId", 1);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String undoTransaction(String savingsAccountId, String transactionId) {
        log.info("Undoing transaction {} on account {}", transactionId, savingsAccountId);

        try {
            String response = fineractWebClient.post()
                    .uri("/savingsaccounts/{accountId}/transactions/{txnId}?command=undo",
                            savingsAccountId, transactionId)
                    .bodyValue(Map.of())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            cacheService.evictAccountCache(savingsAccountId);
            String txnId = extractResourceId(response);
            log.info("Undo successful: account={}, txnId={}", savingsAccountId, txnId);
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + savingsAccountId + "/undo/" + transactionId, "SUCCESS", "Undo txn=" + txnId);
            return txnId;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Undo failed for txn " + transactionId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Undo failed for txn " + transactionId, e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String executeLoanDisbursement(String loanId, BigDecimal amount, LocalDate date, String note) {
        log.info("Executing loan disbursement for loan {}: {} on {}", loanId, amount, date);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actualDisbursementDate", date.format(FINERACT_DATE_FMT));
        body.put("transactionAmount", amount);
        body.put("note", note != null ? note : "ISO 20022 loan disbursement");
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");

        try {
            String response = fineractWebClient.post()
                    .uri("/loans/{loanId}/transactions?command=disburse", loanId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String txnId = extractResourceId(response);
            log.info("Loan disbursement successful: loan={}, txnId={}", loanId, txnId);
            return txnId;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Loan disbursement failed for loan " + loanId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Loan disbursement failed for loan " + loanId, e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String executeLoanRepayment(String loanId, BigDecimal amount, LocalDate date, String note) {
        log.info("Executing loan repayment for loan {}: {} on {}", loanId, amount, date);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionDate", date.format(FINERACT_DATE_FMT));
        body.put("transactionAmount", amount);
        body.put("note", note != null ? note : "ISO 20022 loan repayment");
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");

        try {
            String response = fineractWebClient.post()
                    .uri("/loans/{loanId}/transactions?command=repayment", loanId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String txnId = extractResourceId(response);
            log.info("Loan repayment successful: loan={}, txnId={}", loanId, txnId);
            return txnId;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Loan repayment failed for loan " + loanId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Loan repayment failed for loan " + loanId, e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String executeLoanChargeBack(String loanId, String transactionId) {
        log.info("Executing loan chargeback for loan {} txn {}", loanId, transactionId);

        try {
            String response = fineractWebClient.post()
                    .uri("/loans/{loanId}/transactions/{txnId}?command=chargeback", loanId, transactionId)
                    .bodyValue(Map.of())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String txnId = extractResourceId(response);
            log.info("Loan chargeback successful: loan={}, txnId={}", loanId, txnId);
            return txnId;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Loan chargeback failed for loan " + loanId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Loan chargeback failed for loan " + loanId, e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String createStandingInstruction(String fromAccountId, String toAccountId,
                                             BigDecimal amount, String frequency, String name) {
        log.info("Creating standing instruction: {} -> {}, {} ({})", fromAccountId, toAccountId, amount, frequency);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name != null ? name : "ISO20022 Direct Debit");
        body.put("fromAccountId", fromAccountId);
        body.put("fromAccountType", 2);
        body.put("fromClientId", 1);
        body.put("fromOfficeId", 1);
        body.put("toAccountId", toAccountId);
        body.put("toAccountType", 2);
        body.put("toClientId", 1);
        body.put("toOfficeId", 1);
        body.put("transferType", 1);
        body.put("priority", 1);
        body.put("status", 1);
        body.put("instructionType", 1);
        body.put("amount", amount);
        body.put("validFrom", LocalDate.now().format(FINERACT_DATE_FMT));
        body.put("recurrenceType", 1);
        body.put("recurrenceFrequency", mapFrequency(frequency));
        body.put("recurrenceInterval", 1);
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");

        try {
            String response = fineractWebClient.post()
                    .uri("/standinginstructions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String resourceId = extractResourceId(response);
            log.info("Standing instruction created: id={}", resourceId);
            auditService.logFineractApiCall("POST", "/standinginstructions", "SUCCESS", "Standing instruction=" + resourceId);
            return resourceId;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Standing instruction creation failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Standing instruction creation failed", e);
        }
    }

    public void deleteStandingInstruction(String instructionId) {
        log.info("Deleting standing instruction: {}", instructionId);

        try {
            fineractWebClient.delete()
                    .uri("/standinginstructions/{id}", instructionId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Standing instruction deleted: {}", instructionId);
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Standing instruction deletion failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Standing instruction deletion failed", e);
        }
    }

    public List<FineractTransaction> getTransactions(String savingsAccountId,
                                                      LocalDate fromDate, LocalDate toDate) {
        log.info("Fetching transactions for account {} from {} to {}", savingsAccountId, fromDate, toDate);

        try {
            String response = fineractWebClient.get()
                    .uri("/savingsaccounts/{accountId}?associations=transactions", savingsAccountId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTransactionsFromAccount(response, fromDate, toDate);
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Failed to fetch transactions for account " + savingsAccountId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Failed to fetch transactions for account " + savingsAccountId, e);
        }
    }

    public List<FineractTransaction> getLoanTransactions(String loanId, LocalDate fromDate, LocalDate toDate) {
        log.info("Fetching loan transactions for loan {} from {} to {}", loanId, fromDate, toDate);

        try {
            String response = fineractWebClient.get()
                    .uri("/loans/{loanId}?associations=transactions", loanId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseLoanTransactions(response, fromDate, toDate);
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Failed to fetch loan transactions for loan " + loanId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Failed to fetch loan transactions for loan " + loanId, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getAccountDetails(String savingsAccountId) {
        Map<String, Object> cached = cacheService.getCachedAccountInfo(savingsAccountId);
        if (cached != null) return cached;

        try {
            String response = fineractWebClient.get()
                    .uri("/savingsaccounts/{accountId}", savingsAccountId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            Map<String, Object> result = objectMapper.readValue(response,
                    new TypeReference<Map<String, Object>>() {});
            cacheService.cacheAccountInfo(savingsAccountId, result);
            return result;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Failed to get account " + savingsAccountId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Failed to get account " + savingsAccountId, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLoanDetails(String loanId) {
        try {
            String response = fineractWebClient.get()
                    .uri("/loans/{loanId}", loanId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Failed to get loan " + loanId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Failed to get loan " + loanId, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> searchAccountByExternalId(String externalId) {
        try {
            String response = fineractWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/savingsaccounts")
                            .queryParam("externalId", externalId)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode pageItems = root.has("pageItems") ? root.get("pageItems") : root;
            if (pageItems.isArray() && !pageItems.isEmpty()) {
                return objectMapper.readValue(pageItems.get(0).toString(),
                        new TypeReference<Map<String, Object>>() {});
            }
            return null;
        } catch (Exception e) {
            log.debug("Search by external ID '{}' failed: {}", externalId, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> executeBatch(List<Map<String, Object>> batchRequests) {
        log.info("Executing Fineract batch API with {} requests", batchRequests.size());

        try {
            String response = fineractWebClient.post()
                    .uri("/batches")
                    .bodyValue(batchRequests)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(response, new TypeReference<List<Map<String, Object>>>() {});
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Batch API failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Batch API failed", e);
        }
    }

    @Retryable(retryFor = FineractApiException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2))
    public String createSavingsAccount(String clientName, String currency, String accountName) {
        log.info("Creating savings account for client: {}, currency: {}", clientName, currency);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", 1);
        body.put("productId", 1);
        body.put("locale", "en");
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("submittedOnDate", LocalDate.now().format(FINERACT_DATE_FMT));
        if (accountName != null) body.put("externalId", accountName);

        try {
            String response = fineractWebClient.post()
                    .uri("/savingsaccounts")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String accountId = extractResourceId(response);
            log.info("Savings account created: {}", accountId);
            auditService.logFineractApiCall("POST", "/savingsaccounts", "SUCCESS", "Account created=" + accountId);

            approveAndActivateSavings(accountId);
            return accountId;
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Create savings failed",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Create savings failed", e);
        }
    }

    private void approveAndActivateSavings(String accountId) {
        Map<String, Object> approveBody = new LinkedHashMap<>();
        approveBody.put("approvedOnDate", LocalDate.now().format(FINERACT_DATE_FMT));
        approveBody.put("dateFormat", "dd MMMM yyyy");
        approveBody.put("locale", "en");

        try {
            fineractWebClient.post()
                    .uri("/savingsaccounts/{id}?command=approve", accountId)
                    .bodyValue(approveBody)
                    .retrieve().bodyToMono(String.class).block();

            Map<String, Object> activateBody = new LinkedHashMap<>();
            activateBody.put("activatedOnDate", LocalDate.now().format(FINERACT_DATE_FMT));
            activateBody.put("dateFormat", "dd MMMM yyyy");
            activateBody.put("locale", "en");

            fineractWebClient.post()
                    .uri("/savingsaccounts/{id}?command=activate", accountId)
                    .bodyValue(activateBody)
                    .retrieve().bodyToMono(String.class).block();

            log.info("Savings account {} approved and activated", accountId);
        } catch (Exception e) {
            log.warn("Could not approve/activate savings {}: {}", accountId, e.getMessage());
        }
    }

    public void updateSavingsAccount(String accountId, String externalId) {
        log.info("Updating savings account: {}", accountId);

        Map<String, Object> body = new LinkedHashMap<>();
        if (externalId != null) body.put("externalId", externalId);
        body.put("locale", "en");

        try {
            fineractWebClient.put()
                    .uri("/savingsaccounts/{id}", accountId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            cacheService.evictAccountCache(accountId);
            log.info("Savings account updated: {}", accountId);
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Update savings failed for " + accountId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Update savings failed for " + accountId, e);
        }
    }

    public void closeSavingsAccount(String accountId) {
        log.info("Closing savings account: {}", accountId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawBalance", true);
        body.put("closedOnDate", LocalDate.now().format(FINERACT_DATE_FMT));
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");

        try {
            fineractWebClient.post()
                    .uri("/savingsaccounts/{id}?command=close", accountId)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            cacheService.evictAccountCache(accountId);
            log.info("Savings account closed: {}", accountId);
            auditService.logFineractApiCall("POST", "/savingsaccounts/" + accountId + "/close", "SUCCESS", "Account closed");
        } catch (WebClientResponseException e) {
            throw new FineractApiException("Close savings failed for " + accountId,
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new FineractApiException("Close savings failed for " + accountId, e);
        }
    }

    private Map<String, Object> buildTransactionBody(BigDecimal amount, String note, String defaultNote) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionDate", LocalDate.now().format(FINERACT_DATE_FMT));
        body.put("transactionAmount", amount);
        body.put("paymentTypeId", 1);
        body.put("note", note != null ? note : defaultNote);
        body.put("dateFormat", "dd MMMM yyyy");
        body.put("locale", "en");
        return body;
    }

    private Object parseAccountId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return id;
        }
    }

    private String extractResourceId(String response) {
        try {
            JsonNode node = objectMapper.readTree(response);
            if (node.has("resourceId")) return node.get("resourceId").asText();
            if (node.has("savingsId")) return node.get("savingsId").asText();
            if (node.has("loanId")) return node.get("loanId").asText();
            return node.toString();
        } catch (Exception e) {
            log.warn("Could not extract resourceId from response: {}", response);
            return response;
        }
    }

    private List<FineractTransaction> parseTransactions(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode pageItems = root.has("pageItems") ? root.get("pageItems") : root;
            if (pageItems.isArray()) {
                return objectMapper.readValue(pageItems.toString(),
                        new TypeReference<List<FineractTransaction>>() {});
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not parse transactions response: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FineractTransaction> parseTransactionsFromAccount(
            String response, LocalDate from, LocalDate to) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode txns = root.path("transactions");
            if (txns.isArray()) {
                List<FineractTransaction> all = objectMapper.readValue(txns.toString(),
                        new TypeReference<List<FineractTransaction>>() {});
                return all.stream()
                        .filter(t -> {
                            if (t.getDate() == null || t.getDate().size() < 3) return true;
                            LocalDate txDate = LocalDate.of(
                                    t.getDate().get(0), t.getDate().get(1), t.getDate().get(2));
                            return !txDate.isBefore(from) && !txDate.isAfter(to);
                        })
                        .toList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not parse account transactions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<FineractTransaction> parseLoanTransactions(String response, LocalDate from, LocalDate to) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode txns = root.path("transactions");
            if (txns.isArray()) {
                List<FineractTransaction> all = objectMapper.readValue(txns.toString(),
                        new TypeReference<List<FineractTransaction>>() {});
                return all.stream()
                        .filter(t -> {
                            if (t.getDate() == null || t.getDate().size() < 3) return true;
                            LocalDate txDate = LocalDate.of(t.getDate().get(0), t.getDate().get(1), t.getDate().get(2));
                            return !txDate.isBefore(from) && !txDate.isAfter(to);
                        })
                        .toList();
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("Could not parse loan transactions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private int mapFrequency(String frequency) {
        if (frequency == null) return 2; // monthly
        return switch (frequency.toUpperCase()) {
            case "DAILY", "DAIL" -> 0;
            case "WEEKLY", "WEEK" -> 1;
            case "MONTHLY", "MNTH" -> 2;
            case "QUARTERLY", "QUTR" -> 3;
            case "YEARLY", "YEAR" -> 4;
            default -> 2;
        };
    }
}
