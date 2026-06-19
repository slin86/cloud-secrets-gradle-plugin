# k8s-secrets Gradle Plugin

Standalone Gradle plugin. Loads any number of Kubernetes secrets during local `bootRun` via the local kubeconfig (kubectl) and provides each contained `data` key as a system property.

**Property Name:** `namespace<SEP>secretName<SEP>key` → e.g. `aname_test-secret_test-name`

No pod, no deployment – runs entirely on the developer machine.

- Plugin ID: `slin.k8s-secrets`
- Coordinates: `slin.gradle:k8s-secrets-gradle-plugin:1.0.0`

## Build & Test

```bash
gradle wrapper --gradle-version 8.14 
./gradlew build
./gradlew test
```

## Using in the Consumer Project

### 1. Register Plugin Repository (`settings.gradle`)

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()                         // if testing via publishToMavenLocal
    }
}
```

### 2. Apply & Configure Plugin (`build.gradle`)

```gradle
plugins {
    id 'org.springframework.boot' version '4.1.0'
    id 'slin.k8s-secrets' version '1.0.0'
}

k8sSecrets {
    // kubectl = 'kubectl'      // optional
    // separator = '_'          // optional
    // replaceHyphens = false   // optional

    secret {
        namespace = 'aname'
        name = 'test-secret'
    }
    secret {
        namespace = 'aname'
        name = 'andere-secrets'
    }
}
```

All `data` keys from each secret are loaded automatically – you don't need to specify individual keys.

### 3. Use in Code

```yaml
# application.yaml
keyvaultsecrettest: ${aname_test-secret_test-name:not found}
```

```java
@Value("${aname_test-secret_test-name}")
private String secretValue;
```

### 4. Start

```bash
./gradlew bootRun
```

```
========== [k8s-secrets] Loading 1 Secret(s) ==========
[k8s-secrets] -> aname/test-secret
[k8s-secrets]    aname_test-secret_test-name = i***(26 chars)
[k8s-secrets] Done: 1 property(s) set from 1 secret(s).
```

## How It Works

1. `bootRun.doFirst` runs **before** the JVM fork
2. Plugin calls `kubectl get secret <name> -n <namespace> -o json`
3. Streams are cleanly consumed via `consumeProcessOutput` (prevents "Stream closed" errors)
4. Each base64 value in `.data` is decoded
5. `bootRun.systemProperty(name, value)` sets the property in the **forked** bootRun JVM
   (not in the Gradle JVM – this was the original pitfall)

## Notes

- Values are masked in the log (first character + length only)
- Hyphens in property names work fine with Spring `@Value` literal lookup.
  If issues arise: `replaceHyphens = true`.
- If kubectl is missing or the secret doesn't exist, it's logged and skipped – the build doesn't fail.
- Uses the active kubeconfig/active context. Before starting, you may need to
  `kubectl config use-context <ctx>`.
