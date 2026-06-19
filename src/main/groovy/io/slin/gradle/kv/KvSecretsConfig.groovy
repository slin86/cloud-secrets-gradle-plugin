package io.slin.gradle.kv

/**
 * Configuration package of the Key Vault variant.
 *
 *   kvSecrets {
 *       azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
 *       az = 'az'                       // optional, path to the Azure CLI
 *
 *       secret {
 *           name = 'my-app-config'      // default: rootProject.name
 *           type = 'json'               // default: json
 *       }
 *       secret {
 *           name = 'another-secret'
 *           type = 'string'
 *           envName = 'MY_ENV_NAME'     // optional
 *       }
 *   }
 *
 * Reads via the Azure CLI ('az keyvault secret show'), so it uses the local
 * 'az login'. No SDK, no extra dependencies.
 */
class KvSecretsConfig {

    String az = 'az'
    String azureKeyVaultUrl

    final List<io.slin.gradle.kv.KvSecretSpec> secrets = []

    void secret(Closure closure) {
        def spec = new io.slin.gradle.kv.KvSecretSpec()
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (spec.type && !(spec.type in ['json', 'string'])) {
            throw new IllegalArgumentException(
                "kvSecrets.secret: type must be 'json' or 'string' (was: ${spec.type})")
        }
        secrets << spec
    }
}
