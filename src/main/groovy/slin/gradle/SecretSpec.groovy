package slin.gradle

/**
 * Describes a single kubernetes secret.
 */
class SecretSpec {

    /** Kubernetes Namespace, z.B. "aname" */
    String namespace

    /** Name of secrets, z.B. "test-secret" */
    String name

    @Override
    String toString() {
        "SecretSpec(namespace=${namespace}, name=${name})"
    }
}
