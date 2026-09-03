package com.n0hana.echoes_server.config;

import java.util.Arrays;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.n0hana.echoes_server.model.DocumentType;
import com.n0hana.echoes_server.model.Terms;
import com.n0hana.echoes_server.repository.TermsRepository;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final TermsRepository termsRepository;

    @Bean
    public CommandLineRunner initTerms() {
        return args -> {
            Arrays.stream(DocumentType.values()).forEach(type -> {
                if (termsRepository.findTopByTypeOrderByTimestampDesc(type).isEmpty()) {
                    Terms terms = new Terms();
                    terms.setVersion("1.0.0");
                    terms.setContent(getDefaultContent(type));
                    terms.setType(type);
                    terms.setActive(true);
                    termsRepository.save(terms);
                }
            });
        };
    }

    private String getDefaultContent(DocumentType type) {
        return switch (type) {
            case TERMS_OF_USE -> """
                <h1>Terms of Use</h1>
                <p>Welcome to Echoes. By using our service, you agree to these terms.</p>
                <h2>1. Use of Service</h2>
                <p>You agree to use the service in accordance with all applicable laws.</p>
                <h2>2. Privacy</h2>
                <p>We collect and process your data as described in our Privacy Policy.</p>
                <h2>3. Account Security</h2>
                <p>You are responsible for maintaining the confidentiality of your account.</p>
                """;
            case PRIVACY_POLICY -> """
                <h1>Privacy Policy</h1>
                <p>This policy describes how we collect, use, and protect your personal information.</p>
                <h2>1. Information We Collect</h2>
                <p>We collect information you provide directly to us, such as name and email.</p>
                <h2>2. How We Use Information</h2>
                <p>We use your information to provide and improve our services.</p>
                """;
            case DATA_DELETION_POLICY -> """
                <h1>Data Deletion Policy</h1>
                <p>You can request deletion of your data at any time.</p>
                <h2>How to Request Deletion</h2>
                <p>Contact our support team to request data deletion.</p>
                """;
            case MARKETING_CONSENT -> """
                <h1>Marketing Consent</h1>
                <p>We would like to send you marketing communications about our services.</p>
                <p>You can opt-out at any time by contacting support or using the unsubscribe link.</p>
                """;
            case COOKIES_POLICY -> """
                <h1>Cookies Policy</h1>
                <p>We use cookies to enhance your experience.</p>
                <h2>Types of Cookies</h2>
                <p>We use essential cookies for authentication and optional cookies for analytics.</p>
                """;
        };
    }
}