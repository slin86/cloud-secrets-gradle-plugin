package slin.gradle

/**
 * Beschreibt ein einzelnes Kubernetes Secret, das geladen werden soll.
 */
class SecretSpec {

    /** Kubernetes Namespace, z.B. "aname" */
    String namespace

    /** Name des Secrets, z.B. "test-secret" */
    String name

    @Override
    String toString() {
        "SecretSpec(namespace=${namespace}, name=${name})"
    }
}
