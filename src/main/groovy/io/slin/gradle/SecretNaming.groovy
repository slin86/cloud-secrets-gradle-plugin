package io.slin.gradle

/**
 * Builds the names of the environment variables and properties.
 * Intentionally side effect free and static so it can be tested in isolation.
 */
class SecretNaming {

    /** Optionally replaces hyphens with the separator. */
    static String applyHyphens(String name, String separator, boolean replaceHyphens) {
        replaceHyphens ? name.replace('-', separator) : name
    }

    /**
     * k8s: by default only the data key. Namespace and secret name can
     * optionally be prepended as a prefix.
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
     * KV type string: default is the secret name in upper case with '-' to '_'.
     * An explicit name can be set via envName.
     */
    static String kvString(String envName, String secretName) {
        if (envName) return envName
        return secretName.toUpperCase().replace('-', '_')
    }

    /**
     * KV type json: every JSON key becomes a variable. The key is taken
     * as is (with optional hyphen replacement).
     */
    static String kvJsonKey(String key, String separator, boolean replaceHyphens) {
        return applyHyphens(key, separator, replaceHyphens)
    }
}
