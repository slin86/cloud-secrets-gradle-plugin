package io.slin.example;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    // The plugin sets the Key Vault secret as the environment variable TEST_SECRET.
    @Value("${TEST_SECRET:not found}")
    private String secret;

    @PostConstruct
    public void init() {
        System.out.println("Secret: " + secret);
    }
}
