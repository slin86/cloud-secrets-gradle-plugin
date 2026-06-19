package io.slin.gradle.k8s

/**
 * Configuration package of the k8s variant.
 *
 *   k8sSecrets {
 *       kubectl = 'kubectl'                 // optional
 *       includeNamespacePrefix = false      // optional, namespace as prefix
 *       includeSecretNamePrefix = false     // optional, secret name as prefix
 *
 *       secret { namespace = 'kuma-v2'; name = 'test-secret' }
 *       secret { namespace = 'kuma';    name = 'andere-secrets' }
 *   }
 *
 * Default variable name is only the data key. The two prefix flags allow
 * prepending namespace and secret name.
 */
class K8sSecretsConfig {

    String kubectl = 'kubectl'
    boolean includeNamespacePrefix = false
    boolean includeSecretNamePrefix = false

    final List<io.slin.gradle.k8s.K8sSecretSpec> secrets = []

    void secret(Closure closure) {
        def spec = new io.slin.gradle.k8s.K8sSecretSpec()
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (!spec.namespace || !spec.name) {
            throw new IllegalArgumentException(
                "k8sSecrets.secret requires 'namespace' and 'name' (was: ${spec})")
        }
        secrets << spec
    }
}
