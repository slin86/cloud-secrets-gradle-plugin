# Example: Key Vault source, task run, Spring

Loads an Azure Key Vault secret and injects it as an environment variable into
bootRun. The Spring application reads it with @Value.

## Prerequisites

```bash
az login
```

Set azureKeyVaultUrl in build.gradle to your vault, and make sure the secret
named test-secret exists in it.

## Run

```bash
./gradlew bootRun
```

Expected output contains:

```
Secret: <the value of test-secret>
```

## Notes

* type string produces one variable. The name is the secret name in upper case,
  or an explicit envName (here TEST_SECRET).
* For type json the secret value is a JSON object and every key becomes its own
  variable.
* The plugin is pulled from the repo source via includeBuild in settings.gradle.
