package io.slin.example;

import java.io.FileInputStream;
import java.util.Properties;

public class TestService {

    public void printSecret() {
        var props = new Properties();

        try (var input = new FileInputStream("build/slin-secrets/secrets.properties")) {
            props.load(input);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String secret = props.getProperty("test");

        System.out.println("Count Secret Values: " + props.size());
        System.out.println("Secret: " + secret);
    }
}
