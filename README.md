# slin-secrets Gradle Plugin

Lädt Secrets aus **Kubernetes** und/oder **Azure Key Vault** lokal auf den
Entwicklerrechner und stellt sie als Umgebungsvariablen bereit – entweder direkt
an Tasks (z.B. `bootRun`) oder über eine gecachte env/properties-Datei.

Kein Pod, kein Deployment. Beide Quellen bieten **identische Features**, nur der
Ursprung der Secrets unterscheidet sich.

- Plugin-ID: `io.slin.secrets`
- Koordinaten: `io.slin.gradle:k8s-secrets-gradle-plugin:1.0.0`

## Konfiguration

```gradle
plugins {
    id 'org.springframework.boot' version '4.1.0'
    id 'io.slin.secrets' version '1.0.0'
}

slinSecrets {
    // ---- gemeinsame Optionen (gelten für k8s UND kv) ----
    separator = '_'
    replaceHyphens = false

    // Feature 1: direkt in Task(s) injizieren. Leer = aus.
    tasks = ['bootRun']

    // Feature 2: Cache-Datei verwenden
    useFile = false
    targetEnvFile = null          // default: build/slin-secrets/secrets.env
    fileFormat = 'env'            // 'env' | 'properties'
    maxAge = '1h'                 // 30m, 2h, 1d, 45s ...

    // ---- Quelle: eine reicht, beide möglich ----
    k8sSecrets {
        // kubectl = 'kubectl'
        includeNamespacePrefix = false   // Namespace als Präfix
        includeSecretNamePrefix = false  // Secret-Name als Präfix

        secret { namespace = 'kuma-v2'; name = 'test-secret' }
    }

    kvSecrets {
        azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
        // az = 'az'

        secret { name = 'app-config' }                              // type=json (default)
        secret { name = 'token'; type = 'string'; envName = 'API_TOKEN' }
    }
}
```

### Variablennamen

**k8s** – Default ist nur der data-Key. Präfixe optional:

| Flags | Beispielname |
|-------|--------------|
| (default) | `test-name` |
| `includeNamespacePrefix` | `kuma-v2_test-name` |
| `includeSecretNamePrefix` | `test-secret_test-name` |
| beide | `kuma-v2_test-secret_test-name` |

**kv** –
- `type = 'json'` (default): Secret-Wert ist ein JSON-Objekt; jeder Key wird zu
  einer eigenen Variable. Default-Secret ist `rootProject.name`.
- `type = 'string'`: eine Variable, Name = Secret-Name in UPPERCASE
  (z.B. `another-secret` → `ANOTHER_SECRET`) oder explizit via `envName`.

## Die zwei Features (für beide Quellen identisch)

### Feature 1 – Injection in Tasks
`tasks = ['bootRun']` hängt die Secrets als Umgebungsvariablen an die genannten
Tasks (unterstützt JavaExec/Exec). Leer = nichts passiert.

### Feature 2 – Cache-Datei mit max-age
`useFile = true` schreibt die Secrets in eine Datei. Ablauf (im Build verankert
über den `syncSecretsFile`-Task, von dem konfigurierte Tasks abhängen):

```
Datei fehlt           -> laden & schreiben
Datei älter als maxAge -> laden & schreiben
Datei frisch          -> nichts tun
Quelle nicht erreichbar (kein Netz/Cluster) -> vorhandene Datei BEHALTEN
```

Bei `useFile = true` werden in `tasks` genannte Tasks aus der **Datei** befüllt
(statt aus frisch geladenen Secrets).

### Manueller Task
```bash
./gradlew updateSecretsFile   # erzwingt Neuladen & Schreiben
./gradlew syncSecretsFile     # lädt nur, wenn fehlend/zu alt
```

## Verwenden im Code

```yaml
# application.yaml (k8s default-Name)
keyvaultsecrettest: ${test-name:not found}
```

```java
@Value("${API_TOKEN}")
private String token;
```

## Build & Test

```bash
gradle wrapper --gradle-version 8.14
./gradlew build test
```

## Veröffentlichen

### Lokal testen
```bash
./gradlew publishToMavenLocal
```
Konsument: `mavenLocal()` in `pluginManagement.repositories` der `settings.gradle`.

### JitPack (anonym lesbar, kein Token)
Nur taggen – JitPack baut selbst:
```bash
git tag 1.0.0
git push origin 1.0.0
```
Im Konsumenten-Projekt (`settings.gradle`):
```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri('https://jitpack.io') }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'io.slin.secrets') {
                useModule("com.github.slin86:k8s-secrets-gradle-plugin:${requested.version}")
            }
        }
    }
}
```
Build-Status: https://jitpack.io/#slin86/k8s-secrets-gradle-plugin

### Gradle Plugin Portal (optional)
In `build.gradle` das `com.gradle.plugin-publish`-Plugin + `website`/`vcsUrl`/`tags`
aktivieren, API-Key in `~/.gradle/gradle.properties` legen, dann `./gradlew publishPlugins`.

## Wie es funktioniert

1. Beide Provider (kubectl / az CLI) liefern eine gemeinsame Map `name → value`.
2. Externe Kommandos werden über `consumeProcessOutput` gelesen (kein "Stream closed").
3. Task-Injection setzt die Werte als Umgebungsvariablen (`doFirst`, vor JVM-Fork).
4. File-Caching schreibt env/properties und respektiert `maxAge`.

## Hinweise
- Werte werden in Logs maskiert (erstes Zeichen + Länge).
- KV nutzt die lokale Azure CLI (`az login`), k8s die aktive kubeconfig.
- `gradle.properties.local` ist in `.gitignore` – Credentials dort ablegen.
