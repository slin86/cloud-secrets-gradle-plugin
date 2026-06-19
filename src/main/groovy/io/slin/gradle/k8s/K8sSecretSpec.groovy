package io.slin.gradle.k8s

/** Ein einzelnes Kubernetes Secret. */
class K8sSecretSpec {
    String namespace
    String name

    @Override
    String toString() { "K8sSecretSpec(namespace=${namespace}, name=${name})" }
}
