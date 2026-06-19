package io.slin.gradle.provider

import org.gradle.api.logging.Logger

/**
 * Eine Quelle für Secrets. Liefert eine Map envName -&gt; value.
 * Die identischen Features (Task-Injection, File-Caching) konsumieren
 * ausschließlich diese Map – unabhängig davon, woher die Secrets stammen.
 */
interface SecretProvider {

    /**
     * Löst alle konfigurierten Secrets auf.
     *
     * @throws SecretResolveException wenn die Quelle nicht erreichbar ist
     *         (z.B. kein Internet / kein Cluster). Wichtig: dieser Fehler
     *         signalisiert "nicht erreichbar" und sorgt dafür, dass eine
     *         vorhandene Cache-Datei NICHT gelöscht/überschrieben wird.
     */
    Map<String, String> resolve(Logger log)

    /** Lesbarer Name der Quelle für Logs, z.B. "k8s" oder "keyvault". */
    String sourceName()
}
