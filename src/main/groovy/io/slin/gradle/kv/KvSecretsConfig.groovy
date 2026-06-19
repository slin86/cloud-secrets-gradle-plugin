package io.slin.gradle.kv

/**
 * Konfigurations-Paket der KeyVault-Variante.
 *
 *   kvSecrets {
 *       azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
 *       az = 'az'                       // optional, Pfad zur Azure CLI
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
 * Liest über die Azure CLI ('az keyvault secret show'), nutzt also den
 * lokalen 'az login'. Kein SDK, keine zusätzlichen Abhängigkeiten.
 */
class KvSecretsConfig {

    String az = 'az'
    String azureKeyVaultUrl

    final List<KvSecretSpec> secrets = []

    void secret(Closure closure) {
        def spec = new KvSecretSpec()
        closure.delegate = spec
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.call()
        if (spec.type && !(spec.type in ['json', 'string'])) {
            throw new IllegalArgumentException(
                "kvSecrets.secret: type muss 'json' oder 'string' sein (war: ${spec.type})")
        }
        secrets << spec
    }
}
