package org.fineract.iso20022.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FineractTransaction {

    private Long id;
    private TransactionType transactionType;
    private BigDecimal amount;
    private Currency currency;
    private List<Integer> date;
    private List<Integer> submittedOnDate;
    private boolean reversed;
    private Long accountId;
    private Long accountNo;
    private BigDecimal runningBalance;

    public String getCurrencyCode() {
        return currency != null ? currency.getCode() : null;
    }

    public String getTransactionTypeValue() {
        return transactionType != null ? transactionType.getValue() : null;
    }

    public boolean isDeposit() {
        return transactionType != null && transactionType.isDeposit();
    }

    public boolean isWithdrawal() {
        return transactionType != null && transactionType.isWithdrawal();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionType {
        private Long id;
        private String code;
        private String value;
        private boolean deposit;
        private boolean withdrawal;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Currency {
        private String code;
        private String name;
        private int decimalPlaces;
    }
}
