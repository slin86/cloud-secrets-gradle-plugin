# Example: k8s source, task run, Spring

Loads a Kubernetes secret and injects it as an environment variable into bootRun.
The Spring application reads it with @Value.

## Create the secret

```bash
kubectl create namespace kuma-v2
kubectl create secret generic test-secret --from-literal=test='hello from k8s' -n kuma-v2
```

## Run

```bash
./gradlew bootRun
```

Expected output contains:

```
Secret: hello from k8s
```

## Notes

* The variable name is the data key of the secret (here test). Enable
  includeNamespacePrefix or includeSecretNamePrefix in the k8sSecrets block to
  prepend the namespace or secret name.
* The plugin is pulled from the repo source via includeBuild in settings.gradle,
  so there is no version and no publish step.
