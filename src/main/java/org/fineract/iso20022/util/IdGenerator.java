package org.fineract.iso20022.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class IdGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private IdGenerator() {}

    public static String generateMessageId() {
        return "MSG" + LocalDateTime.now().format(DATE_FMT) + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String generateEndToEndId() {
        return "E2E" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String generateInstructionId() {
        return "INSTR" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String generateTransactionId() {
        return "TXN" + LocalDateTime.now().format(DATE_FMT) + UUID.randomUUID().toString().substring(0, 8);
    }
}
