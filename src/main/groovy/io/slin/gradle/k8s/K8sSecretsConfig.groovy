package io.slin.gradle.k8s

/**
 * Konfigurations-Paket der k8s-Variante.
 *
 *   k8sSecrets {
 *       kubectl = 'kubectl'                 // optional
 *       includeNamespacePrefix = false      // optional, Namespace als Präfix
 *       includeSecretNamePrefix = false     // optional, Secret-Name als Präfix
 *
 *       secret { namespace = 'kuma-v2'; name = 'test-secret' }
 *       secret { namespace = 'kuma';    name = 'andere-secrets' }
 *   }
 *
 * Default-Variablenname = nur der data-Key. Über die beiden Präfix-Flags
 * lassen sich Namespace und/oder Secret-Name voranstellen.
 */
class K8sSecretsConfig {

    String kubectl = 'kubectl'
    boolean includeNamespacePrefix = false
    boolean includeSecretNamePrefix = false

    final List<K8sSecretSpec> secrets = []

    void secret(Closure closure) {
        def spec = new K8sSecretSpec()
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (!spec.namespace || !spec.name) {
            throw new IllegalArgumentException(
                "k8sSecrets.secret benötigt 'namespace' und 'name' (war: ${spec})")
        }
        secrets << spec
    }
}
