# slin-secrets Gradle Plugin

Loads secrets from Kubernetes and Azure Key Vault locally on the developer
machine and provides them as environment variables, either directly on tasks
(for example bootRun) or through a cached env or properties file.

No pod, no deployment. Both sources offer identical features, only the origin
of the secrets differs.

Plugin id: io.slin.secrets
Coordinates: io.slin.gradle:cloud-secrets-gradle-plugin:1.0.0

## Configuration

```gradle
plugins {
    id 'org.springframework.boot' version '4.1.0'
    id 'io.slin.secrets' version '1.1.0'
}

slinSecrets {
    // shared options (apply to k8s and kv)
    separator = '_'
    replaceHyphens = false

    // Feature 1: inject directly into task(s). Empty means off.
    tasks = ['bootRun']

    // Feature 2: use a cache file
    useFile = false
    targetEnvFile = null          // default: build/slin-secrets/secrets.env
    fileFormat = 'env'            // 'env' or 'properties'
    maxAge = '1h'                 // 30m, 2h, 1d, 45s

    // source: one is enough, both possible
    k8sSecrets {
        // kubectl = 'kubectl'
        includeNamespacePrefix = false   // namespace as prefix
        includeSecretNamePrefix = false  // secret name as prefix

        secret { namespace = 'kuma-v2'; name = 'test-secret' }
    }

    kvSecrets {
        azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
        // az = 'az'

        secret { name = 'app-config' }                              // type json (default)
        secret { name = 'token'; type = 'string'; envName = 'API_TOKEN' }
    }
}
```

### Variable names

k8s: the default is only the data key. Prefixes are optional.

| Flags | Example name |
|-------|--------------|
| default | test-name |
| includeNamespacePrefix | kuma-v2_test-name |
| includeSecretNamePrefix | test-secret_test-name |
| both | kuma-v2_test-secret_test-name |

kv:
* type json (default): the secret value is a JSON object. Every key becomes its
  own variable. The default secret is rootProject.name.
* type string: one variable, name is the secret name in upper case
  (for example another-secret becomes ANOTHER_SECRET) or explicit via envName.

## The two features (identical for both sources)

### Feature 1: injection into tasks
tasks = ['bootRun'] attaches the secrets as environment variables to the named
tasks (supports JavaExec and Exec). Empty means nothing happens.

### Feature 2: cache file with max age
useFile = true writes the secrets into a file. The flow is anchored in the build
through the syncSecretsFile task that the configured tasks depend on:

```
file missing            load and write
file older than maxAge  load and write
file fresh              do nothing
source unreachable (no network or cluster)   keep the existing file
```

With useFile = true the tasks listed in tasks are filled from the file instead
of from freshly loaded secrets.

### Manual task
```bash
./gradlew updateSecretsFile   # forces reload and write
./gradlew syncSecretsFile     # loads only when missing or too old
```

## Using in code

```yaml
# application.yaml (k8s default name)
keyvaultsecrettest: ${test-name:not found}
```

```java
@Value("${API_TOKEN}")
private String token;
```

## Build and test

```bash
gradle wrapper --gradle-version 8.14
./gradlew build test
```

## Publishing

### Test locally
```bash
./gradlew publishToMavenLocal
```
Consumer: mavenLocal() in pluginManagement.repositories of settings.gradle.

### JitPack (anonymous read, no token)
Just tag, JitPack builds itself:
```bash
git tag 1.0.0
git push origin 1.0.0
```
In the consumer project (settings.gradle):
```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri('https://jitpack.io') }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'io.slin.secrets') {
                useModule("com.github.slin86:cloud-secrets-gradle-plugin:${requested.version}")
            }
        }
    }
}
```
Build status: https://jitpack.io/#slin86/cloud-secrets-gradle-plugin

### Gradle Plugin Portal (optional)
In build.gradle enable the com.gradle.plugin-publish plugin plus website, vcsUrl
and tags, put the API key in ~/.gradle/gradle.properties, then run
./gradlew publishPlugins.

## How it works

1. Both providers (kubectl and az CLI) return a shared map of name to value.
2. External commands are read via consumeProcessOutput (no "Stream closed").
3. Task injection sets the values as environment variables (doFirst, before the JVM fork).
4. File caching writes env or properties and respects maxAge.

## Notes
* Values are masked in logs (first character and length only).
* kv uses the local Azure CLI (az login), k8s uses the active kubeconfig.
* gradle.properties.local is in .gitignore, put credentials there.
