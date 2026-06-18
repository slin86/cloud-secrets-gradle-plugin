# k8s-secrets Gradle Plugin

Standalone Gradle-Plugin. Lädt beim lokalen `bootRun` beliebig viele Kubernetes
Secrets über die lokale kubeconfig (kubectl) und stellt jeden enthaltenen
`data`-Key als System Property bereit.

**Property-Name:** `namespace<SEP>secretName<SEP>key` → z.B. `aname_test-secret_test-name`

Kein Pod, kein Deployment – läuft komplett auf dem Entwicklerrechner.

- Plugin-ID: `slin.k8s-secrets`
- Koordinaten: `slin.gradle:k8s-secrets-gradle-plugin:1.0.0`

## Repo-Struktur

```
k8s-secrets-gradle-plugin/
├── settings.gradle
├── build.gradle
├── gradle.properties.example
├── .gitignore
└── src/
    ├── main/groovy/slin/gradle/
    │   ├── K8sSecretsPlugin.groovy      # Logik: kubectl lesen, decoden, systemProperty setzen
    │   ├── K8sSecretsExtension.groovy   # DSL-Konfiguration
    │   └── SecretSpec.groovy            # ein einzelnes Secret
    └── test/groovy/slin/gradle/
        └── K8sSecretsPluginFunctionalTest.groovy
```

## Bauen & Testen

```bash
gradle wrapper --gradle-version 8.14 
./gradlew build
./gradlew test
```

## Veröffentlichen

### Lokal (zum Ausprobieren)

```bash
./gradlew publishToMavenLocal
```

### Ins interne Repo (z.B. Azure Artifacts)

Credentials in `~/.gradle/gradle.properties` (siehe `gradle.properties.example`):

```properties
internalRepoUrl=https://pkgs.dev.azure.com/<org>/<project>/_packaging/<feed>/maven/v1
internalRepoUser=<user>
internalRepoToken=<pat>
```

Dann:

```bash
./gradlew publishAllPublicationsToInternalRepository
```

Dabei werden zwei Artefakte publiziert:
- das Plugin-JAR `slin.gradle:k8s-secrets-gradle-plugin`
- der Plugin-Marker `slin.k8s-secrets:slin.k8s-secrets.gradle.plugin`
  (den braucht der `plugins {}`-Block zur Auflösung)

## Im Konsumenten-Projekt verwenden

### 1. Plugin-Repository bekannt machen (`settings.gradle`)

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenLocal()                         // falls per publishToMavenLocal getestet
        maven {
            url = uri('https://pkgs.dev.azure.com/<org>/<project>/_packaging/<feed>/maven/v1')
            credentials {
                username = providers.gradleProperty('internalRepoUser').orNull
                password = providers.gradleProperty('internalRepoToken').orNull
            }
        }
    }
}
```

### 2. Plugin anwenden & konfigurieren (`build.gradle`)

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

Alle `data`-Keys jedes Secrets werden automatisch geladen – einzelne Keys
musst du nicht angeben.

### 3. Im Code nutzen

```yaml
# application.yaml
keyvaultsecrettest: ${aname_test-secret_test-name:nicht gefunden}
```

```java
@Value("${keyvaultsecrettest}")
private String secretValue;
```

### 4. Starten

```bash
./gradlew bootRun
```

```
========== [k8s-secrets] Loading 1 Secret(s) ==========
[k8s-secrets] -> aname/test-secret
[k8s-secrets]    aname_test-secret_test-name = i***(26 chars)
[k8s-secrets] Fertig: 1 Property(s) aus 1 Secret(s) gesetzt.
```

## Wie es funktioniert

1. `bootRun.doFirst` läuft **vor** dem JVM-Fork
2. Plugin ruft `kubectl get secret <name> -n <namespace> -o json` auf
3. Streams werden über `consumeProcessOutput` sauber abgegriffen (vermeidet "Stream closed")
4. Jeder base64-Wert in `.data` wird dekodiert
5. `bootRun.systemProperty(name, value)` setzt die Property im **geforkten** bootRun-JVM
   (nicht in der Gradle-JVM – das war der ursprüngliche Stolperstein)

## Hinweise

- Werte werden im Log maskiert (nur erstes Zeichen + Länge)
- Bindestriche im Property-Namen sind für Spring `@Value` mit literalem Lookup ok.
  Falls es zickt: `replaceHyphens = true`.
- Fehlt kubectl oder das Secret, wird geloggt und übersprungen – der Build bricht nicht ab.
- Nutzt die aktive kubeconfig/den aktiven Context. Vor dem Start ggf.
  `kubectl config use-context <ctx>` setzen.
```
