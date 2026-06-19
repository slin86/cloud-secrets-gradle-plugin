package io.slin.gradle.kv

/**
 * Ein einzelnes Azure Key Vault Secret.
 *
 * type = 'json'   -&gt; Wert ist ein JSON-Objekt mit key:value-Paaren.
 *                    Jeder Key wird zu einer eigenen Variable.
 * type = 'string' -&gt; Wert ist ein String. Variablenname = Secret-Name in
 *                    UPPERCASE (oder explizit via envName).
 */
class KvSecretSpec {
    String name
    String type = 'json'
    String envName

    @Override
    String toString() { "KvSecretSpec(name=${name}, type=${type}, envName=${envName})" }
}
