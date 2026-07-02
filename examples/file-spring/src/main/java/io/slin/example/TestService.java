package io.slin.example;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    // Property test comes from the imported properties file (k8s data key test).
    @Value("${test:not found}")
    private String secret;

    @PostConstruct
    public void init() {
        System.out.println("Secret: " + secret);
    }
}
