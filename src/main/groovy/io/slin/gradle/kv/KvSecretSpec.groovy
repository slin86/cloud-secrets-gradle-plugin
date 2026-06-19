package io.slin.gradle.kv

/**
 * A single Azure Key Vault secret.
 *
 * type json   means the value is a JSON object with key value pairs.
 *             Each key becomes its own variable.
 * type string means the value is a string. Variable name is the secret
 *             name in upper case (or explicit via envName).
 */
class KvSecretSpec {
    String name
    String type = 'json'
    String envName

    @Override
    String toString() { "KvSecretSpec(name=${name}, type=${type}, envName=${envName})" }
}
