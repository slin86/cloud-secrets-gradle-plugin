package io.slin.gradle.provider

import org.gradle.api.logging.Logger

/**
 * A source of secrets. Returns a map of envName to value.
 * The shared features (task injection, file caching) consume only this map,
 * regardless of where the secrets originate.
 */
interface SecretProvider {

    /**
     * Resolves all configured secrets.
     *
     * Throws SecretResolveException when the source is unreachable
     * (for example no network or no cluster). This signals "unreachable"
     * and ensures that an existing cache file is not deleted or overwritten.
     */
    Map<String, String> resolve(Logger log)

    /** Readable name of the source for logs, for example "k8s" or "keyvault". */
    String sourceName()
}
