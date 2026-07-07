package io.slin.gradle.kv

import groovy.json.JsonSlurper
import io.slin.gradle.SecretNaming
import io.slin.gradle.provider.ProcessRunner
import io.slin.gradle.provider.SecretProvider
import org.gradle.api.logging.Logger

/**
 * Reads Azure Key Vault secrets via the Azure CLI:
 *   az keyvault secret show --id VAULTURL/secrets/NAME --query value -o tsv
 *
 * Each secret is resolved independently. If one secret cannot be read (for
 * example missing permissions), it is logged as a warning and skipped, the
 * others are still returned. Resolution never throws, so it never breaks the build.
 */
class KeyVaultSecretProvider implements SecretProvider {

    final KvSecretsConfig config
    final String separator
    final boolean replaceHyphens

    KeyVaultSecretProvider(KvSecretsConfig config, String separator, boolean replaceHyphens) {
        this.config = config
        this.separator = separator
        this.replaceHyphens = replaceHyphens
    }

    @Override
    String sourceName() { 'keyvault' }

    @Override
    Map<String, String> resolve(Logger log) {
        def result = [:] as LinkedHashMap

        if (!config.azureKeyVaultUrl) {
            log.warn("[slin-secrets] kvSecrets.azureKeyVaultUrl is not set, skipping key vault.")
            return result
        }
        def base = config.azureKeyVaultUrl.endsWith('/') ? config.azureKeyVaultUrl : config.azureKeyVaultUrl + '/'

        config.secrets.each { spec ->
            try {
                def id = "${base}secrets/${spec.name}"
                log.lifecycle("[slin-secrets] keyvault loading ${spec.name} (type=${spec.type})")
                def cmd = [config.az, 'keyvault', 'secret', 'show', '--id', id.toString(), '--query', 'value', '-o', 'tsv']
                log.info("[slin-secrets]   cmd: ${cmd.join(' ')}")

                def r = ProcessRunner.run(cmd)
                if (r.exit != 0) {
                    log.warn("[slin-secrets]   skipping ${spec.name}: az exit=${r.exit}: ${r.stderr.trim()}")
                    return
                }

                def value = r.stdout.trim()

                if (spec.type == 'string') {
                    def name = SecretNaming.kvString(spec.envName, spec.name)
                    result[name] = value
                } else {
                    def parsed
                    try {
                        parsed = new JsonSlurper().parseText(value)
                    } catch (Exception e) {
                        log.warn("[slin-secrets]   skipping ${spec.name}: value is not valid JSON (type json).")
                        return
                    }
                    if (!(parsed instanceof Map)) {
                        log.warn("[slin-secrets]   skipping ${spec.name}: value is not a JSON object (type json).")
                        return
                    }
                    parsed.each { k, v ->
                        def name = SecretNaming.kvJsonKey(k.toString(), separator, replaceHyphens)
                        result[name] = v?.toString()
                    }
                }
            } catch (Exception e) {
                log.warn("[slin-secrets]   skipping ${spec.name}: ${e.message}")
            }
        }
        return result
    }
}
