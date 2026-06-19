package io.slin.gradle.k8s

import groovy.json.JsonSlurper
import io.slin.gradle.SecretNaming
import io.slin.gradle.provider.ProcessRunner
import io.slin.gradle.provider.SecretProvider
import io.slin.gradle.provider.SecretResolveException
import org.gradle.api.logging.Logger

/**
 * Reads Kubernetes secrets via the local kubeconfig (kubectl get secret -o json),
 * decodes all base64 values in the data block and builds the variables from them.
 */
class K8sSecretProvider implements SecretProvider {

    final io.slin.gradle.k8s.K8sSecretsConfig config
    final String separator
    final boolean replaceHyphens

    K8sSecretProvider(io.slin.gradle.k8s.K8sSecretsConfig config, String separator, boolean replaceHyphens) {
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
            log.lifecycle("[slin-secrets] k8s loading ${spec.namespace}/${spec.name}")
            def cmd = [config.kubectl, 'get', 'secret', spec.name, '-n', spec.namespace, '-o', 'json']
            log.info("[slin-secrets]   cmd: ${cmd.join(' ')}")

            def r = ProcessRunner.run(cmd)
            if (r.exit != 0) {
                throw new SecretResolveException("kubectl exit=${r.exit}: ${r.stderr.trim()}")
            }

            def data = new JsonSlurper().parseText(r.stdout)?.data
            if (!data) {
                log.warn("[slin-secrets]   Secret ${spec.name} has no 'data' entries, skipped.")
                return
            }

            data.each { String key, String base64 ->
                def name = SecretNaming.k8s(
                    separator, replaceHyphens,
                    config.includeNamespacePrefix, config.includeSecretNamePrefix,
                    spec.namespace, spec.name, key)
                result[name] = new String(base64.decodeBase64(), 'UTF-8')
            }
        }
        return result
    }
}
