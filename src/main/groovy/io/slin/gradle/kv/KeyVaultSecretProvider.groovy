package io.slin.gradle.kv

import groovy.json.JsonSlurper
import io.slin.gradle.SecretNaming
import io.slin.gradle.provider.ProcessRunner
import io.slin.gradle.provider.SecretProvider
import io.slin.gradle.provider.SecretResolveException
import org.gradle.api.logging.Logger

/**
 * Liest Azure Key Vault Secrets über die Azure CLI:
 *   az keyvault secret show --id &lt;vaultUrl&gt;secrets/&lt;name&gt; --query value -o tsv
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
        if (!config.azureKeyVaultUrl) {
            throw new SecretResolveException("kvSecrets.azureKeyVaultUrl ist nicht gesetzt.")
        }
        def base = config.azureKeyVaultUrl.endsWith('/') ? config.azureKeyVaultUrl : config.azureKeyVaultUrl + '/'

        def result = [:] as LinkedHashMap

        config.secrets.each { spec ->
            def id = "${base}secrets/${spec.name}"
            log.lifecycle("[slin-secrets] keyvault -> ${spec.name} (type=${spec.type})")
            def cmd = [config.az, 'keyvault', 'secret', 'show', '--id', id.toString(), '--query', 'value', '-o', 'tsv']
            log.info("[slin-secrets]   cmd: ${cmd.join(' ')}")

            def r = ProcessRunner.run(cmd)
            if (r.exit != 0) {
                throw new SecretResolveException("az exit=${r.exit}: ${r.stderr.trim()}")
            }

            def value = r.stdout.trim()

            if (spec.type == 'string') {
                def name = SecretNaming.kvString(spec.envName, spec.name)
                result[name] = value
            } else {
                // json (default): jeder Key -> eine Variable
                def parsed
                try {
                    parsed = new JsonSlurper().parseText(value)
                } catch (Exception e) {
                    throw new SecretResolveException(
                        "Secret '${spec.name}' ist als type=json konfiguriert, enthält aber kein gültiges JSON.", e)
                }
                if (!(parsed instanceof Map)) {
                    throw new SecretResolveException(
                        "Secret '${spec.name}' (type=json) ist kein JSON-Objekt mit key:value-Paaren.")
                }
                parsed.each { k, v ->
                    def name = SecretNaming.kvJsonKey(k.toString(), separator, replaceHyphens)
                    result[name] = v?.toString()
                }
            }
        }
        return result
    }
}
