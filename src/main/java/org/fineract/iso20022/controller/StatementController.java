package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.model.dto.AccountStatementRequest;
import org.fineract.iso20022.service.StatementService;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/statements")
@RequiredArgsConstructor
@Validated
@Tag(name = "Statements", description = "ISO 20022 statement and notification endpoints")
public class StatementController {

    private final StatementService statementService;

    @GetMapping(value = "/{accountId}", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate camt.053 account statement",
            description = "Fetches transactions from Fineract and returns an ISO 20022 camt.053 statement")
    @ApiResponse(responseCode = "200", description = "Statement generated",
            content = @Content(mediaType = MediaType.APPLICATION_XML_VALUE))
    public ResponseEntity<String> getStatement(
            @Parameter(description = "Fineract savings account ID") @PathVariable @Size(max = 64) String accountId,
            @Parameter(description = "Start date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {

        AccountStatementRequest request = AccountStatementRequest.builder()
                .accountId(accountId)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();

        String statementXml = statementService.generateStatement(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(statementXml);
    }

    @PostMapping(value = "/{accountId}", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate camt.053 statement with custom parameters")
    public ResponseEntity<String> generateStatement(
            @PathVariable String accountId,
            @Valid @RequestBody AccountStatementRequest request) {
        request.setAccountId(accountId);
        String statementXml = statementService.generateStatement(request);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(statementXml);
    }

    @GetMapping(value = "/notification/{paymentMessageId}", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate camt.054 debit/credit notification",
            description = "Generates an ISO 20022 camt.054 notification for a processed payment")
    public ResponseEntity<String> getNotification(
            @Parameter(description = "Payment message ID") @PathVariable @Size(max = 64) String paymentMessageId,
            @Parameter(description = "true for credit notification, false for debit")
            @RequestParam(defaultValue = "true") boolean credit) {

        String notificationXml = statementService.generateNotification(paymentMessageId, credit);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(notificationXml);
    }

    @GetMapping(value = "/{accountId}/intraday", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate camt.052 intraday account report")
    public ResponseEntity<String> getIntradayReport(
            @Parameter(description = "Fineract savings account ID") @PathVariable @Size(max = 64) String accountId) {
        String reportXml = statementService.generateIntradayReport(accountId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(reportXml);
    }

    @GetMapping(value = "/loan/{loanId}", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Generate camt.053 loan statement")
    public ResponseEntity<String> getLoanStatement(
            @Parameter(description = "Fineract loan ID") @PathVariable @Size(max = 64) String loanId,
            @Parameter(description = "Start date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "End date")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        String statementXml = statementService.generateLoanStatement(loanId, fromDate, toDate);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(statementXml);
    }
}
