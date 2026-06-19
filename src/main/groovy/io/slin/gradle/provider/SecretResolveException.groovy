package io.slin.gradle.provider

/**
 * Thrown when a secret source is unreachable
 * (kubectl or az not startable, exit code not zero, no network, no cluster).
 *
 * Consequence: an existing cache file is kept and not overwritten.
 * "If there is no internet, the file is never deleted."
 */
class SecretResolveException extends RuntimeException {
    SecretResolveException(String message) {
        super(message)
    }

    SecretResolveException(String message, Throwable cause) {
        super(message, cause)
    }
}
