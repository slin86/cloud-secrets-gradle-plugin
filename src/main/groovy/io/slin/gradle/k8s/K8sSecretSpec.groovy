package io.slin.gradle.k8s

/** A single Kubernetes secret. */
class K8sSecretSpec {
    String namespace
    String name

    @Override
    String toString() { "K8sSecretSpec(namespace=${namespace}, name=${name})" }
}
