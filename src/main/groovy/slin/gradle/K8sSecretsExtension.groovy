package slin.gradle

/**
 * Konfigurations-DSL für das k8s-secrets Plugin.
 *
 * Beispiel in build.gradle:
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

    /** Pfad/Name des kubectl-Binaries. */
    String kubectl = 'kubectl'

    /** Trennzeichen für den Property-Namen: namespace SEP name SEP key */
    String separator = '_'

    /**
     * Wenn true, werden Bindestriche in namespace/name/key durch das
     * Trennzeichen ersetzt (praktisch, falls Spring @Value mit Bindestrichen zickt).
     */
    boolean replaceHyphens = false

    /** Alle konfigurierten Secrets. */
    final List<SecretSpec> secrets = []

    /** DSL-Methode: secret { namespace = ...; name = ... } */
    void secret(Closure closure) {
        def spec = new SecretSpec()
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
