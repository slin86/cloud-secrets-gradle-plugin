package io.slin.gradle

import io.slin.gradle.k8s.K8sSecretProvider
import io.slin.gradle.kv.KeyVaultSecretProvider
import io.slin.gradle.provider.SecretProvider
import io.slin.gradle.provider.SecretResolveException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * slin-secrets: loads secrets from Kubernetes and Azure Key Vault and makes them
 * available locally as environment variables on tasks and via a cached
 * env or properties file.
 */
class SecretsPlugin implements Plugin<Project> {

    static final String TAG = '[slin-secrets]'

    @Override
    void apply(Project project) {
        def ext = project.extensions.create('secretsLoader', io.slin.gradle.SecretsExtension)

        // Register tasks lazily so they exist independently of afterEvaluate.
        def syncTask = project.tasks.register('syncSecretsFile') {
            it.group = 'slin secrets'
            it.description = 'Updates the secret cache file when it is missing or older than maxAge.'
        }
        def updateTask = project.tasks.register('updateSecretsFile') {
            it.group = 'slin secrets'
            it.description = 'Forces a reload of the secrets and writes the cache file.'
        }

        project.afterEvaluate {
            def providers = buildProviders(project, ext)
            def fileService = buildFileService(project, ext)

            if (providers.isEmpty()) {
                project.logger.lifecycle("${TAG} No source configured (neither k8sSecrets nor kvSecrets), plugin inactive.")
            }

            // define task bodies
            syncTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) return
                    if (fileService.isFresh()) {
                        project.logger.lifecycle("${TAG} File is fresh (age below maxAge), no reload. (${fileService.file})")
                        return
                    }
                    refreshFile(project, providers, fileService, false)
                }
            }
            updateTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) {
                        project.logger.warn("${TAG} No source configured, nothing to update.")
                        return
                    }
                    refreshFile(project, providers, fileService, true)
                }
            }

            // Feature 1 and 2: injection into configured tasks
            if (!ext.tasks.isEmpty() && !providers.isEmpty()) {
                ext.tasks.each { taskName ->
                    project.tasks.matching { it.name == taskName }.configureEach { target ->
                        if (ext.useFile) {
                            // Feature 2: ensure file is current, then inject its content
                            target.dependsOn(syncTask)
                            target.doFirst {
                                def entries = fileService.read()
                                project.logger.lifecycle("${TAG} Injecting ${entries.size()} variable(s) from file into '${taskName}'.")
                                io.slin.gradle.TaskInjector.injectEnv(target, entries, project.logger)
                            }
                        } else {
                            // Feature 1: resolve fresh and inject directly
                            target.doFirst {
                                def entries = resolveAll(project, providers)
                                project.logger.lifecycle("${TAG} Injecting ${entries.size()} variable(s) directly into '${taskName}'.")
                                io.slin.gradle.TaskInjector.injectEnv(target, entries, project.logger)
                            }
                        }
                    }
                }
            }
        }
    }

    private List<SecretProvider> buildProviders(Project project, io.slin.gradle.SecretsExtension ext) {
        def providers = [] as List<SecretProvider>

        if (ext.k8sConfigured && !ext.k8s.secrets.isEmpty()) {
            providers << new K8sSecretProvider(ext.k8s, ext.separator, ext.replaceHyphens)
        }

        if (ext.kvConfigured) {
            // Default secret is rootProject.name (type json) when none is given.
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

    private io.slin.gradle.SecretFileService buildFileService(Project project, io.slin.gradle.SecretsExtension ext) {
        File target
        if (ext.targetEnvFile) {
            target = project.file(ext.targetEnvFile)
        } else {
            def fileName = ext.fileFormat == 'properties' ? 'secrets.properties' : 'secrets.env'
            target = project.layout.buildDirectory.file("slin-secrets/${fileName}").get().asFile
        }
        return new io.slin.gradle.SecretFileService(target, ext.fileFormat, SecretFileService.parseMaxAgeMillis(ext.maxAge))
    }

    /** Resolves all providers and merges them into one map. */
    private Map<String, String> resolveAll(Project project, List<SecretProvider> providers) {
        def merged = [:] as LinkedHashMap
        providers.each { p -> merged.putAll(p.resolve(project.logger)) }
        return merged
    }

    /**
     * Loads the secrets and writes the file.
     * When force is false and the source is unreachable, an existing file is kept.
     */
    private void refreshFile(Project project, List<SecretProvider> providers,
                             io.slin.gradle.SecretFileService fileService, boolean force) {
        try {
            def entries = resolveAll(project, providers)
            fileService.write(entries)
            project.logger.lifecycle("${TAG} Wrote ${entries.size()} variable(s): ${fileService.file}")
        } catch (SecretResolveException e) {
            if (fileService.exists()) {
                project.logger.warn("${TAG} Source unreachable (${e.message}), keeping existing file: ${fileService.file}")
                if (force) {
                    project.logger.warn("${TAG} (manual update task, but the file is not deleted to be safe)")
                }
            } else {
                project.logger.error("${TAG} Source unreachable and no cache file present: ${e.message}")
                if (force) throw e
            }
        }
    }
}
