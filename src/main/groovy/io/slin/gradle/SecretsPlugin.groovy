package io.slin.gradle

import io.slin.gradle.k8s.K8sSecretProvider
import io.slin.gradle.kv.KeyVaultSecretProvider
import io.slin.gradle.provider.SecretProvider
import io.slin.gradle.provider.SecretResolveException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * slin-secrets: Lädt Secrets aus Kubernetes und/oder Azure Key Vault und stellt
 * sie lokal bereit – als Umgebungsvariablen an Tasks und/oder über eine
 * gecachte env/properties-Datei.
 */
class SecretsPlugin implements Plugin<Project> {

    static final String TAG = '[slin-secrets]'

    @Override
    void apply(Project project) {
        def ext = project.extensions.create('slinSecrets', SecretsExtension)

        // Tasks immer registrieren (lazy), damit sie unabhängig von afterEvaluate existieren.
        def syncTask = project.tasks.register('syncSecretsFile') {
            it.group = 'slin secrets'
            it.description = 'Aktualisiert die Secret-Cache-Datei, wenn sie fehlt oder älter als maxAge ist.'
        }
        def updateTask = project.tasks.register('updateSecretsFile') {
            it.group = 'slin secrets'
            it.description = 'Erzwingt das Neuladen der Secrets und schreibt die Cache-Datei.'
        }

        project.afterEvaluate {
            def providers = buildProviders(project, ext)
            def fileService = buildFileService(project, ext)

            if (providers.isEmpty()) {
                project.logger.lifecycle("${TAG} Keine Quelle konfiguriert (weder k8sSecrets noch kvSecrets) – Plugin inaktiv.")
            }

            // ---- Task-Inhalte definieren ----
            syncTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) return
                    if (fileService.isFresh()) {
                        project.logger.lifecycle("${TAG} Datei frisch (Alter < maxAge) – kein Reload. (${fileService.file})")
                        return
                    }
                    refreshFile(project, providers, fileService, false)
                }
            }
            updateTask.configure { t ->
                t.doLast {
                    if (providers.isEmpty()) {
                        project.logger.warn("${TAG} Keine Quelle konfiguriert – nichts zu aktualisieren.")
                        return
                    }
                    refreshFile(project, providers, fileService, true)
                }
            }

            // ---- Feature 1 & 2: Injection in konfigurierte Tasks ----
            if (!ext.tasks.isEmpty() && !providers.isEmpty()) {
                ext.tasks.each { taskName ->
                    project.tasks.matching { it.name == taskName }.configureEach { target ->
                        if (ext.useFile) {
                            // Feature 2: Datei sicherstellen, dann Datei-Inhalt injizieren
                            target.dependsOn(syncTask)
                            target.doFirst {
                                def entries = fileService.read()
                                project.logger.lifecycle("${TAG} Injiziere ${entries.size()} Variable(n) aus Datei in '${taskName}'.")
                                TaskInjector.injectEnv(target, entries, project.logger)
                            }
                        } else {
                            // Feature 1: frisch auflösen und direkt injizieren
                            target.doFirst {
                                def entries = resolveAll(project, providers)
                                project.logger.lifecycle("${TAG} Injiziere ${entries.size()} Variable(n) direkt in '${taskName}'.")
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
            // Default-Secret = rootProject.name (type json), falls keins angegeben.
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

    /** Alle Provider auflösen und zu einer Map zusammenführen. */
    private Map<String, String> resolveAll(Project project, List<SecretProvider> providers) {
        def merged = [:] as LinkedHashMap
        providers.each { p -> merged.putAll(p.resolve(project.logger)) }
        return merged
    }

    /**
     * Lädt die Secrets und schreibt die Datei.
     * @param force bei false wird bei Quelle-nicht-erreichbar eine vorhandene Datei behalten.
     */
    private void refreshFile(Project project, List<SecretProvider> providers,
                             SecretFileService fileService, boolean force) {
        try {
            def entries = resolveAll(project, providers)
            fileService.write(entries)
            project.logger.lifecycle("${TAG} ${entries.size()} Variable(n) geschrieben: ${fileService.file}")
        } catch (SecretResolveException e) {
            if (fileService.exists()) {
                project.logger.warn("${TAG} Quelle nicht erreichbar (${e.message}) – behalte vorhandene Datei: ${fileService.file}")
                if (force) {
                    project.logger.warn("${TAG} (manueller update-Task, aber Datei wird zur Sicherheit nicht gelöscht)")
                }
            } else {
                project.logger.error("${TAG} Quelle nicht erreichbar und keine Cache-Datei vorhanden: ${e.message}")
                if (force) throw e
            }
        }
    }
}
