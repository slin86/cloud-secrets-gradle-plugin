# Examples

Each folder is a standalone Gradle project that demonstrates one use case. All
of them pull the plugin directly from the source in this repository through
includeBuild in their settings.gradle, so there is no version and no publish
step. Changes to the plugin are picked up immediately.

| Example | Source | Delivery | Framework | Run |
|---------|--------|----------|-----------|-----|
| k8s-taskrun-spring | Kubernetes | env injection into bootRun | Spring | ./gradlew bootRun |
| kv-taskrun-spring | Key Vault | env injection into bootRun | Spring | ./gradlew bootRun |
| file-spring | Kubernetes | properties file + spring.config.import | Spring | ./gradlew bootRun |
| file-plain | Kubernetes | properties file + java.util.Properties | none | ./gradlew run |

## Prerequisites

* JDK 21
* For the Kubernetes examples: kubectl with a valid kubeconfig
* For the Key Vault example: the Azure CLI with az login

## How the plugin is wired

Every settings.gradle contains:

```gradle
pluginManagement {
    includeBuild('../..')
    repositories {
        gradlePluginPortal()
    }
}
```

includeBuild('../..') points at the plugin project at the repo root. The build
files then apply the plugin without a version:

```gradle
plugins {
    id 'io.slin.secrets'
}
```

## CI note

The examples call real kubectl or az, so they do not fully run without a cluster
or an az login. To verify them in CI without secrets, run a configuration only
task per example, for example ./gradlew tasks, which applies the plugin and
validates the DSL without loading any secrets.
