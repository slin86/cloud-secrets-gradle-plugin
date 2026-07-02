# Example: save to file, no Spring

Writes the secrets into build/slin-secrets/secrets.properties and reads the file
with plain java.util.Properties. No Spring involved.

## Create the secret

```bash
kubectl create namespace kuma-v2
kubectl create secret generic test-secret --from-literal=test='hello from file' -n kuma-v2
```

## Run

```bash
./gradlew run
```

run depends on syncSecretsFile, so the file is refreshed first (respecting
maxAge). Expected output contains:

```
Count Secret Values: 1
Secret: hello from file
```

## Notes

* The application plugin provides the run task and starts io.slin.example.Main.
* The file path is relative to the project directory, which is the working
  directory of the run task.
* Swap the k8sSecrets block for a kvSecrets block to use Key Vault instead.
