package org.fineract.iso20022.security;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import lombok.extern.slf4j.Slf4j;
import org.fineract.iso20022.exception.MessageParsingException;
import org.xml.sax.InputSource;

@Slf4j
public final class SecureXmlParser {

    private static final int MAX_XML_SIZE = 5 * 1024 * 1024; // 5 MB

    private SecureXmlParser() {}

    public static String sanitize(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new MessageParsingException("XML message cannot be null or empty");
        }

        if (xml.length() > MAX_XML_SIZE) {
            throw new MessageParsingException(
                    "XML payload exceeds maximum allowed size of " + (MAX_XML_SIZE / 1024) + " KB");
        }

        String upper = xml.toUpperCase();
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")
                || upper.contains("SYSTEM") || upper.contains("PUBLIC")) {
            throw new MessageParsingException(
                    "XML contains forbidden DTD/entity declarations (potential XXE attack)");
        }

        return xml;
    }

    public static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory;
        } catch (Exception e) {
            throw new MessageParsingException("Failed to configure secure XML parser", e);
        }
    }

    public static SAXParserFactory createSecureSaxParserFactory() {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            return factory;
        } catch (Exception e) {
            throw new MessageParsingException("Failed to configure secure SAX parser", e);
        }
    }

    public static void validateNoXxe(String xml) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (MessageParsingException e) {
            throw e;
        } catch (Exception e) {
            throw new MessageParsingException("XML validation failed: " + e.getMessage(), e);
        }
    }
}
