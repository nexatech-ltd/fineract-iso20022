package org.fineract.iso20022.security;

public final class DataMaskingUtil {

    private DataMaskingUtil() {}

    public static String maskAccount(String account) {
        if (account == null) return null;
        int len = account.length();
        if (len <= 4) return "****";
        return "*".repeat(len - 4) + account.substring(len - 4);
    }

    public static String maskIban(String iban) {
        if (iban == null) return null;
        int len = iban.length();
        if (len <= 6) return "****";
        return iban.substring(0, 2) + "*".repeat(len - 6) + iban.substring(len - 4);
    }

    public static String maskName(String name) {
        if (name == null) return null;
        if (name.length() <= 2) return "**";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    public static String maskXml(String xml) {
        if (xml == null) return null;
        return xml
                .replaceAll("(<IBAN>)([^<]+)(</IBAN>)", "$1****$3")
                .replaceAll("(<Id>)([^<]{5,})(</Id>)", "$1****$3")
                .replaceAll("(<Nm>)([^<]+)(</Nm>)", "$1****$3");
    }

    public static String sanitizeForLog(String value) {
        if (value == null) return null;
        return value.replaceAll("[\\r\\n]", "").replaceAll("[\\x00-\\x1f]", "");
    }
}
