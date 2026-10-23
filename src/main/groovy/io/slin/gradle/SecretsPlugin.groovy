package io.slin.gradle

import io.slin.gradle.k8s.K8sSecretProvider
import io.slin.gradle.kv.KeyVaultSecretProvider
import io.slin.gradle.provider.SecretProvider
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * slin-secrets: loads secrets from Kubernetes and Azure Key Vault and makes them
 * available locally as environment variables on tasks and via a cached
 * env or properties file.
 *
 * Secret resolution is best effort and never breaks the build. Individual
 * secrets that cannot be read are logged and skipped, the rest are still used.
 */
class SecretsPlugin implements Plugin<Project> {

    static final String TAG = '[slin-secrets]'

    // The DSL block name. Must match every build.gradle, test and example.
    static final String EXTENSION_NAME = 'secretsLoader'

    @Override
    void apply(Project project) {
        def ext = project.extensions.create(EXTENSION_NAME, SecretsExtension)

        def syncTask = project.tasks.register('syncSecretsFile') {
            it.group = 'slin secrets'
            it.description = 'Updates the secret cache file when it is missing or older than maxAge.'
        }
        def updateTask = project.tasks.register('updateSecretsFile') {
            it.group = 'slin secrets'
            it.description = 'Forces a reload of the secrets and writes the cache file.'
        }

        project.afterEvaluate {
            def log = new SecretsLog(project.logger, ext.logLevel)
            def providers = buildProviders(project, ext)
            def fileService = buildFileService(project, ext)

            if (providers.isEmpty()) {
                log.info("${TAG} No source configured (neither k8sSecrets nor kvSecrets), plugin inactive.")
            }

            syncTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) return
                    if (fileService.isFresh()) {
                        log.info("${TAG} File is fresh (age below maxAge), no reload. (${fileService.file})")
                        return
                    }
                    refreshFile(log, providers, fileService)
                }
            }
            updateTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) {
                        log.warn("${TAG} No source configured, nothing to update.")
                        return
                    }
                    refreshFile(log, providers, fileService)
                }
            }

            if (!ext.tasks.isEmpty() && !providers.isEmpty()) {
                ext.tasks.each { taskName ->
                    project.tasks.matching { it.name == taskName }.configureEach { target ->
                        if (ext.useFile) {
                            target.dependsOn(syncTask)
                            target.doFirst {
                                def entries = readFileSafely(log, fileService)
                                log.info("${TAG} Injecting ${entries.size()} variable(s) from file into '${taskName}'.")
                                TaskInjector.injectEnv(target, entries, log)
                            }
                        } else {
                            target.doFirst {
                                def entries = resolveAll(log, providers)
                                log.info("${TAG} Injecting ${entries.size()} variable(s) directly into '${taskName}'.")
                                TaskInjector.injectEnv(target, entries, log)
                            }
                        }
                    }
                }
            }
        }
    }

    private List<SecretProvider> buildProviders(Project project, SecretsExtension ext) {
        def providers = [] as List<SecretProvider>

        if (ext.k8sConfigured && !ext.k8s.secrets.isEmpty()) {
            providers << new K8sSecretProvider(ext.k8s, ext.separator, ext.replaceHyphens)
        }

        if (ext.kvConfigured) {
            if (ext.kv.secrets.isEmpty()) {
                ext.kv.secret {
                    name = project.rootProject.name
                    type = 'json'
                }
            } else {
                ext.kv.secrets.each { s -> if (!s.name) s.name = project.rootProject.name }
            }
            providers << new KeyVaultSecretProvider(ext.kv, ext.separator, ext.replaceHyphens)
        }

        return providers
    }

    private SecretFileService buildFileService(Project project, SecretsExtension ext) {
        File target
        if (ext.targetEnvFile) {
            target = project.file(ext.targetEnvFile)
        } else {
            def fileName = ext.fileFormat == 'properties' ? 'secrets.properties' : 'secrets.env'
            target = project.layout.buildDirectory.file("slin-secrets/${fileName}").get().asFile
        }
        return new SecretFileService(target, ext.fileFormat, SecretFileService.parseMaxAgeMillis(ext.maxAge))
    }

    // Resolves all providers and merges them. Never throws, so it can never
    // break the build. A failing source only produces a warning.
    private Map<String, String> resolveAll(SecretsLog log, List<SecretProvider> providers) {
        def merged = [:] as LinkedHashMap
        providers.each { p ->
            try {
                merged.putAll(p.resolve(log))
            } catch (Exception e) {
                log.warn("${TAG} source ${p.sourceName()} failed: ${e.message}")
            }
        }
        return merged
    }

    private Map<String, String> readFileSafely(SecretsLog log, SecretFileService fileService) {
        try {
            return fileService.read()
        } catch (Exception e) {
            log.warn("${TAG} could not read cache file: ${e.message}")
            return [:] as LinkedHashMap
        }
    }

    // Writes the file from whatever could be resolved. If nothing resolved at
    // all, an existing file is kept. Never throws, the build is never broken.
    private void refreshFile(SecretsLog log, List<SecretProvider> providers, SecretFileService fileService) {
        def entries = resolveAll(log, providers)
        if (entries.isEmpty()) {
            if (fileService.exists()) {
                log.warn("${TAG} No secrets resolved, keeping existing file: ${fileService.file}")
            } else {
                log.warn("${TAG} No secrets resolved and no cache file present.")
            }
            return
        }
        try {
            fileService.write(entries)
            log.info("${TAG} Wrote ${entries.size()} variable(s): ${fileService.file}")
        } catch (Exception e) {
            log.warn("${TAG} could not write cache file: ${e.message}")
        }
    }
}
