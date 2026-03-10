package org.fineract.iso20022.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.PaymentProcessingException;
import org.fineract.iso20022.mapper.Camt052Mapper;
import org.fineract.iso20022.mapper.Camt053Mapper;
import org.fineract.iso20022.mapper.Camt054Mapper;
import org.fineract.iso20022.model.dto.AccountStatementRequest;
import org.fineract.iso20022.model.dto.FineractTransaction;
import org.fineract.iso20022.model.entity.PaymentMessage;
import org.fineract.iso20022.model.enums.MessageDirection;
import org.fineract.iso20022.model.enums.MessageStatus;
import org.fineract.iso20022.repository.PaymentMessageRepository;
import org.fineract.iso20022.util.IdGenerator;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private final FineractClientService fineractClient;
    private final Camt053Mapper camt053Mapper;
    private final Camt054Mapper camt054Mapper;
    private final Camt052Mapper camt052Mapper;
    private final PaymentMessageRepository paymentMessageRepository;
    private final AuditService auditService;

    public String generateStatement(AccountStatementRequest request) {
        String accountId = request.getAccountId();
        LocalDate fromDate = request.getFromDate() != null ? request.getFromDate() : LocalDate.now().minusMonths(1);
        LocalDate toDate = request.getToDate() != null ? request.getToDate() : LocalDate.now();

        log.info("Generating camt.053 statement for account {} from {} to {}", accountId, fromDate, toDate);

        Map<String, Object> accountDetails = fineractClient.getAccountDetails(accountId);
        String accountName = extractString(accountDetails, "clientName");
        String currency = extractCurrency(accountDetails);

        List<FineractTransaction> transactions = fineractClient.getTransactions(accountId, fromDate, toDate);

        BigDecimal openingBalance = calculateOpeningBalance(transactions);
        BigDecimal closingBalance = calculateClosingBalance(transactions, openingBalance);

        String statementXml = camt053Mapper.buildStatement(
                accountId, accountName, currency,
                openingBalance, closingBalance,
                transactions, fromDate, toDate);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(IdGenerator.generateMessageId())
                .messageType("camt.053")
                .direction(MessageDirection.OUTBOUND)
                .status(MessageStatus.COMPLETED)
                .rawXml(statementXml)
                .debtorAccount(accountId)
                .currency(currency)
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, "GENERATED", "camt.053 statement generated for account " + accountId);

        return statementXml;
    }

    public String generateStatementForAccount(String accountId) {
        AccountStatementRequest req = AccountStatementRequest.builder()
                .accountId(accountId)
                .fromDate(LocalDate.now().minusMonths(1))
                .toDate(LocalDate.now())
                .build();
        return generateStatement(req);
    }

    public String generateIntradayReport(String accountId) {
        LocalDate today = LocalDate.now();
        log.info("Generating camt.052 intraday report for account {}", accountId);

        Map<String, Object> accountDetails = fineractClient.getAccountDetails(accountId);
        String accountName = extractString(accountDetails, "clientName");
        String currency = extractCurrency(accountDetails);

        List<FineractTransaction> transactions = fineractClient.getTransactions(accountId, today, today);

        BigDecimal openingBalance = calculateOpeningBalance(transactions);
        BigDecimal closingBalance = calculateClosingBalance(transactions, openingBalance);

        String reportXml = camt052Mapper.buildIntradayReport(
                accountId, accountName, currency,
                openingBalance, closingBalance,
                transactions, today, today);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(IdGenerator.generateMessageId())
                .messageType("camt.052")
                .direction(MessageDirection.OUTBOUND)
                .status(MessageStatus.COMPLETED)
                .rawXml(reportXml)
                .debtorAccount(accountId)
                .currency(currency)
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, "GENERATED", "camt.052 intraday report generated for account " + accountId);

        return reportXml;
    }

    public String generateLoanStatement(String loanId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null) fromDate = LocalDate.now().minusMonths(1);
        if (toDate == null) toDate = LocalDate.now();

        log.info("Generating camt.053 loan statement for loan {} from {} to {}", loanId, fromDate, toDate);

        Map<String, Object> loanDetails = fineractClient.getLoanDetails(loanId);
        String clientName = extractString(loanDetails, "clientName");
        String currency = extractLoanCurrency(loanDetails);

        List<FineractTransaction> transactions = fineractClient.getLoanTransactions(loanId, fromDate, toDate);

        BigDecimal openingBalance = calculateOpeningBalance(transactions);
        BigDecimal closingBalance = calculateClosingBalance(transactions, openingBalance);

        String statementXml = camt053Mapper.buildStatement(
                loanId, clientName, currency,
                openingBalance, closingBalance,
                transactions, fromDate, toDate);

        PaymentMessage msg = PaymentMessage.builder()
                .messageId(IdGenerator.generateMessageId())
                .messageType("camt.053")
                .direction(MessageDirection.OUTBOUND)
                .status(MessageStatus.COMPLETED)
                .rawXml(statementXml)
                .debtorAccount(loanId)
                .currency(currency)
                .build();
        msg = paymentMessageRepository.save(msg);
        auditService.logAction(msg, "GENERATED", "camt.053 loan statement generated for loan " + loanId);

        return statementXml;
    }

    public String generateNotification(String paymentMessageId, boolean isCredit) {
        PaymentMessage payment = paymentMessageRepository.findByMessageId(paymentMessageId)
                .orElseThrow(() -> new PaymentProcessingException(
                        "Payment not found: " + paymentMessageId));

        if (payment.getStatus() != MessageStatus.COMPLETED) {
            throw new PaymentProcessingException(
                    "Cannot generate notification for non-completed payment: " + payment.getStatus());
        }

        String notificationXml = camt054Mapper.buildNotification(payment, isCredit);

        PaymentMessage notifMsg = PaymentMessage.builder()
                .messageId(IdGenerator.generateMessageId())
                .messageType("camt.054")
                .direction(MessageDirection.OUTBOUND)
                .status(MessageStatus.COMPLETED)
                .rawXml(notificationXml)
                .creditorAccount(payment.getCreditorAccount())
                .debtorAccount(payment.getDebtorAccount())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();
        notifMsg = paymentMessageRepository.save(notifMsg);
        auditService.logAction(notifMsg, "GENERATED", "camt.054 notification for payment " + paymentMessageId);

        return notificationXml;
    }

    private String extractString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private String extractCurrency(Map<String, Object> accountDetails) {
        if (accountDetails.containsKey("currency")) {
            Object curr = accountDetails.get("currency");
            if (curr instanceof Map) {
                return ((Map<String, Object>) curr).getOrDefault("code", "USD").toString();
            }
        }
        return "USD";
    }

    @SuppressWarnings("unchecked")
    private String extractLoanCurrency(Map<String, Object> loanDetails) {
        if (loanDetails.containsKey("currency")) {
            Object curr = loanDetails.get("currency");
            if (curr instanceof Map) {
                return ((Map<String, Object>) curr).getOrDefault("code", "USD").toString();
            }
        }
        return "USD";
    }

    private BigDecimal calculateOpeningBalance(List<FineractTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return BigDecimal.ZERO;
        FineractTransaction first = transactions.getFirst();
        BigDecimal runningBalance = first.getRunningBalance();
        return runningBalance != null ? runningBalance.subtract(first.getAmount()) : BigDecimal.ZERO;
    }

    private BigDecimal calculateClosingBalance(List<FineractTransaction> transactions,
                                                BigDecimal openingBalance) {
        if (transactions == null || transactions.isEmpty()) return openingBalance;
        FineractTransaction last = transactions.getLast();
        return last.getRunningBalance() != null ? last.getRunningBalance() : openingBalance;
    }
}
