package slin.gradle

/**
 * Configurations-DSL for k8s-secrets plugin.
 *
 * Example in build.gradle:
 *
 *   k8sSecrets {
 *       kubectl = 'kubectl'        // optional, default 'kubectl'
 *       separator = '_'            // optional, default '_'
 *       replaceHyphens = false     // optional, default false
 *
 *       secret {
 *           namespace = 'aname'
 *           name = 'test-secret'
 *       }
 *       secret {
 *           namespace = 'aname'
 *           name = 'andere-secrets'
 *       }
 *   }
 */
class K8sSecretsExtension {

    /** Path/Name of kubectl-Binaries. */
    String kubectl = 'kubectl'

    /** Separator for Property-Names: namespace SEP name SEP key */
    String separator = '_'

    /**
     * If true, hyphens in namespace/name/key will be replaced by the configured separator.
     */
    boolean replaceHyphens = false

    /** configured secrets. */
    final List<SecretSpec> secrets = []

    /** DSL-Methode: secret { namespace = ...; name = ... } */
    void secret(Closure closure) {
        def spec = new SecretSpec()
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()

        if (!spec.namespace || !spec.name) {
            throw new IllegalArgumentException(
                "k8sSecrets.secret needs 'namespace' and 'name' (war: ${spec})")
        }
        secrets << spec
    }
}
