package io.slin.gradle

import io.slin.gradle.k8s.K8sSecretsConfig
import io.slin.gradle.kv.KvSecretsConfig

/**
 * Top-Level-Konfiguration des Plugins.
 *
 *   slinSecrets {
 *       // --- gemeinsame Optionen (gelten für k8s UND kv) ---
 *       separator = '_'
 *       replaceHyphens = false
 *
 *       // Feature 1: direkt in Task(s) injizieren (env vars).
 *       // Leer = deaktiviert.
 *       tasks = ['bootRun']
 *
 *       // Feature 2: Cache-Datei verwenden.
 *       useFile = false
 *       targetEnvFile = null          // default: build/slin-secrets/secrets.env
 *       fileFormat = 'env'            // 'env' | 'properties'
 *       maxAge = '1h'                 // 30m, 2h, 1d, 45s ...
 *
 *       // --- Quelle (eine reicht, beide möglich) ---
 *       k8sSecrets { ... }
 *       kvSecrets  { ... }
 *   }
 */
class SecretsExtension {

    String separator = '_'
    boolean replaceHyphens = false

    /** Tasks, in die injiziert wird. Leer => Feature inaktiv. */
    List<String> tasks = []

    boolean useFile = false
    String targetEnvFile = null
    String fileFormat = 'env'
    String maxAge = '1h'

    final K8sSecretsConfig k8s = new K8sSecretsConfig()
    final KvSecretsConfig kv = new KvSecretsConfig()

    boolean k8sConfigured = false
    boolean kvConfigured = false

    void k8sSecrets(Closure closure) {
        closure.delegate = k8s
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        k8sConfigured = true
    }

    void kvSecrets(Closure closure) {
        closure.delegate = kv
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        kvConfigured = true
    }
}
