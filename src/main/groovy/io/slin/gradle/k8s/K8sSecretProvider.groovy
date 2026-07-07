package io.slin.gradle.k8s

import groovy.json.JsonSlurper
import io.slin.gradle.SecretNaming
import io.slin.gradle.provider.ProcessRunner
import io.slin.gradle.provider.SecretProvider
import org.gradle.api.logging.Logger

/**
 * Reads Kubernetes secrets via the local kubeconfig (kubectl get secret -o json),
 * decodes all base64 values in the data block and builds the variables from them.
 *
 * Each secret is resolved independently. If one secret cannot be read, it is
 * logged as a warning and skipped, the others are still returned. Resolution
 * never throws, so it never breaks the build.
 */
class K8sSecretProvider implements SecretProvider {

    final K8sSecretsConfig config
    final String separator
    final boolean replaceHyphens

    K8sSecretProvider(K8sSecretsConfig config, String separator, boolean replaceHyphens) {
        this.config = config
        this.separator = separator
        this.replaceHyphens = replaceHyphens
    }

    @Override
    String sourceName() { 'k8s' }

    @Override
    Map<String, String> resolve(Logger log) {
        def result = [:] as LinkedHashMap

        config.secrets.each { spec ->
            try {
                log.lifecycle("[slin-secrets] k8s loading ${spec.namespace}/${spec.name}")
                def cmd = [config.kubectl, 'get', 'secret', spec.name, '-n', spec.namespace, '-o', 'json']
                log.info("[slin-secrets]   cmd: ${cmd.join(' ')}")

                def r = ProcessRunner.run(cmd)
                if (r.exit != 0) {
                    log.warn("[slin-secrets]   skipping ${spec.namespace}/${spec.name}: kubectl exit=${r.exit}: ${r.stderr.trim()}")
                    return
                }

                def data = new JsonSlurper().parseText(r.stdout)?.data
                if (!data) {
                    log.warn("[slin-secrets]   ${spec.name} has no 'data' entries, skipped.")
                    return
                }

                data.each { String key, String base64 ->
                    def name = SecretNaming.k8s(
                        separator, replaceHyphens,
                        config.includeNamespacePrefix, config.includeSecretNamePrefix,
                        spec.namespace, spec.name, key)
                    result[name] = new String(base64.decodeBase64(), 'UTF-8')
                }
            } catch (Exception e) {
                log.warn("[slin-secrets]   skipping ${spec.namespace}/${spec.name}: ${e.message}")
            }
        }
        return result
    }
}
