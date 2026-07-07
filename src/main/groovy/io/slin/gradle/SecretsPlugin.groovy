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

    @Override
    void apply(Project project) {
        def ext = project.extensions.create('slinSecrets', SecretsExtension)

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

            syncTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) return
                    if (fileService.isFresh()) {
                        project.logger.lifecycle("${TAG} File is fresh (age below maxAge), no reload. (${fileService.file})")
                        return
                    }
                    refreshFile(project, providers, fileService)
                }
            }
            updateTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) {
                        project.logger.warn("${TAG} No source configured, nothing to update.")
                        return
                    }
                    refreshFile(project, providers, fileService)
                }
            }

            if (!ext.tasks.isEmpty() && !providers.isEmpty()) {
                ext.tasks.each { taskName ->
                    project.tasks.matching { it.name == taskName }.configureEach { target ->
                        if (ext.useFile) {
                            target.dependsOn(syncTask)
                            target.doFirst {
                                def entries = readFileSafely(project, fileService)
                                project.logger.lifecycle("${TAG} Injecting ${entries.size()} variable(s) from file into '${taskName}'.")
                                TaskInjector.injectEnv(target, entries, project.logger)
                            }
                        } else {
                            target.doFirst {
                                def entries = resolveAll(project, providers)
                                project.logger.lifecycle("${TAG} Injecting ${entries.size()} variable(s) directly into '${taskName}'.")
                                TaskInjector.injectEnv(target, entries, project.logger)
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
    private Map<String, String> resolveAll(Project project, List<SecretProvider> providers) {
        def merged = [:] as LinkedHashMap
        providers.each { p ->
            try {
                merged.putAll(p.resolve(project.logger))
            } catch (Exception e) {
                project.logger.warn("${TAG} source ${p.sourceName()} failed: ${e.message}")
            }
        }
        return merged
    }

    private Map<String, String> readFileSafely(Project project, SecretFileService fileService) {
        try {
            return fileService.read()
        } catch (Exception e) {
            project.logger.warn("${TAG} could not read cache file: ${e.message}")
            return [:] as LinkedHashMap
        }
    }

    // Writes the file from whatever could be resolved. If nothing resolved at
    // all, an existing file is kept. Never throws, the build is never broken.
    private void refreshFile(Project project, List<SecretProvider> providers, SecretFileService fileService) {
        def entries = resolveAll(project, providers)
        if (entries.isEmpty()) {
            if (fileService.exists()) {
                project.logger.warn("${TAG} No secrets resolved, keeping existing file: ${fileService.file}")
            } else {
                project.logger.warn("${TAG} No secrets resolved and no cache file present.")
            }
            return
        }
        try {
            fileService.write(entries)
            project.logger.lifecycle("${TAG} Wrote ${entries.size()} variable(s): ${fileService.file}")
        } catch (Exception e) {
            project.logger.warn("${TAG} could not write cache file: ${e.message}")
        }
    }
}
