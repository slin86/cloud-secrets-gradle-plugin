package io.slin.gradle.provider

/**
 * Wird geworfen, wenn eine Secret-Quelle nicht erreichbar ist
 * (kubectl/az nicht startbar, Exit-Code != 0, kein Netz, kein Cluster).
 *
 * Folge: eine bereits existierende Cache-Datei wird beibehalten und nicht
 * überschrieben ("wenn kein Internet vorhanden ist, wird die Datei nie gelöscht").
 */
class SecretResolveException extends RuntimeException {
    SecretResolveException(String message) {
        super(message)
    }

    SecretResolveException(String message, Throwable cause) {
        super(message, cause)
    }
}
