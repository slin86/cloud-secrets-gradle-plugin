# Example: save to file, Spring

Writes the secrets into build/slin-secrets/secrets.properties and lets Spring
Boot import the file via spring.config.import. The build itself does not inject
anything into the process.

## Create the secret

```bash
kubectl create namespace kuma-v2
kubectl create secret generic test-secret --from-literal=test='hello from file' -n kuma-v2
```

## Run

```bash
./gradlew bootRun
```

bootRun depends on syncSecretsFile, so the file is refreshed first (respecting
maxAge). Expected output contains:

```
Secret: hello from file
```

## Notes

* The optional: prefix in the import lets the app start even when the file is
  not there yet. In a pod the file does not exist and secrets come from real
  volume mounts, so the import line does no harm.
* Spring imports .properties and .yaml natively, not .env, so fileFormat must be
  properties for this path.
* Swap the k8sSecrets block for a kvSecrets block to use Key Vault instead.
