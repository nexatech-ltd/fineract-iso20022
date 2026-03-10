package org.fineract.iso20022.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fineract.iso20022.model.dto.PaymentInitiationRequest;
import org.fineract.iso20022.model.dto.PaymentStatusResponse;
import org.fineract.iso20022.service.PaymentService;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Account Management", description = "ISO 20022 account management (acmt) endpoints")
public class AccountManagementController {

    private final PaymentService paymentService;

    @PostMapping(value = "/open", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process acmt.007 account opening request")
    public ResponseEntity<List<PaymentStatusResponse>> openAccount(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml).idempotencyKey(idempotencyKey).build();
        return ResponseEntity.ok(paymentService.processPayment(request));
    }

    @PostMapping(value = "/modify", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process acmt.008 account opening amendment request")
    public ResponseEntity<List<PaymentStatusResponse>> modifyAccount(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml).idempotencyKey(idempotencyKey).build();
        return ResponseEntity.ok(paymentService.processPayment(request));
    }

    @PostMapping(value = "/close", consumes = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Process acmt.019 account closing request")
    public ResponseEntity<List<PaymentStatusResponse>> closeAccount(
            @RequestBody @Size(max = 5242880) String xml,
            @RequestHeader(value = "Idempotency-Key", required = false) @Size(max = 128) String idempotencyKey) {
        PaymentInitiationRequest request = PaymentInitiationRequest.builder()
                .xmlMessage(xml).idempotencyKey(idempotencyKey).build();
        return ResponseEntity.ok(paymentService.processPayment(request));
    }
}
