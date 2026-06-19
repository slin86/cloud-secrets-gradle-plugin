package io.slin.gradle

/**
 * Baut die Namen der Umgebungsvariablen / Properties.
 * Bewusst seiteneffektfrei und statisch, damit unabhängig testbar.
 */
class SecretNaming {

    /** Ersetzt optional Bindestriche durch den Separator. */
    static String applyHyphens(String name, String separator, boolean replaceHyphens) {
        replaceHyphens ? name.replace('-', separator) : name
    }

    /**
     * k8s: Standardmäßig nur der data-Key. Namespace und Secret-Name
     * können optional als Präfix vorangestellt werden.
     */
    static String k8s(String separator, boolean replaceHyphens,
                      boolean includeNamespacePrefix, boolean includeSecretNamePrefix,
                      String namespace, String secretName, String key) {
        def parts = []
        if (includeNamespacePrefix) parts << namespace
        if (includeSecretNamePrefix) parts << secretName
        parts << key
        return applyHyphens(parts.join(separator), separator, replaceHyphens)
    }

    /**
     * KV type=string: Default ist der Secret-Name in UPPERCASE mit '-' -&gt; '_'.
     * Mit envName kann ein expliziter Name gesetzt werden.
     */
    static String kvString(String envName, String secretName) {
        if (envName) return envName
        return secretName.toUpperCase().replace('-', '_')
    }

    /**
     * KV type=json: jeder JSON-Key wird zu einer Variable. Der Key wird
     * unverändert übernommen (optional Bindestrich-Ersetzung).
     */
    static String kvJsonKey(String key, String separator, boolean replaceHyphens) {
        return applyHyphens(key, separator, replaceHyphens)
    }
}
