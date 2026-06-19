package io.slin.gradle

import io.slin.gradle.k8s.K8sSecretsConfig
import io.slin.gradle.kv.KvSecretsConfig

/**
 * Top level configuration of the plugin.
 *
 *   secretsLoader {
 *       // shared options (apply to k8s and kv)
 *       separator = '_'
 *       replaceHyphens = false
 *
 *       // Feature 1: inject directly into task(s) as env vars. Empty means disabled.
 *       tasks = ['bootRun']
 *
 *       // Feature 2: use a cache file.
 *       useFile = false
 *       targetEnvFile = null          // default: build/slin-secrets/secrets.properties
 *       fileFormat = 'env'            // 'env' or 'properties'
 *       maxAge = '1h'                 // 30m, 2h, 1d, 45s
 *
 *       // source (one is enough, both possible)
 *       k8sSecrets { ... }
 *       kvSecrets  { ... }
 *   }
 */
class SecretsExtension {

    String separator = '_'
    boolean replaceHyphens = false

    /** Tasks to inject into. Empty means the feature is inactive. */
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
