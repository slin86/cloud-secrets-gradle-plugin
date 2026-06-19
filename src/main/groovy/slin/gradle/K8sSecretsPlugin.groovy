package slin.gradle

import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec

/**
 * Loads before 'bootRun' Kubernetes Secrets for local configured kubeconfig (kubectl) and provides each contained
 * data key as a System Property.
 *
 * Property-Name: namespace SEPARATOR secretName SEPARATOR key
 * z.B. aname_test-secret_test-name
 */
class K8sSecretsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def ext = project.extensions.create('k8sSecrets', K8sSecretsExtension)

        project.afterEvaluate {
            def bootRun = project.tasks.findByName('bootRun')
            if (!(bootRun instanceof JavaExec)) {
                project.logger.warn("[k8s-secrets] No 'bootRun' (JavaExec) Task found.")
                return
            }

            bootRun.doFirst { task ->
                loadAll(project, ext, task as JavaExec)
            }
        }
    }

    private void loadAll(Project project, K8sSecretsExtension ext, JavaExec bootRun) {
        def log = project.logger
        log.lifecycle("")
        log.lifecycle("========== [k8s-secrets] Loading ${ext.secrets.size()} Secret(s) ==========")

        if (ext.secrets.isEmpty()) {
            log.lifecycle("[k8s-secrets] no Secrets configured.")
            return
        }

        int totalKeys = 0
        ext.secrets.each { spec ->
            totalKeys += loadOne(ext, bootRun, spec, log)
        }

        log.lifecycle("[k8s-secrets] Done: ${totalKeys} Property(s) ${ext.secrets.size()} Secret(s) are set.")
        log.lifecycle("===============================================================")
        log.lifecycle("")
    }

    private int loadOne(K8sSecretsExtension ext, JavaExec bootRun, SecretSpec spec, log) {
        log.lifecycle("[k8s-secrets] -> ${spec.namespace}/${spec.name}")

        def cmd = [ext.kubectl, 'get', 'secret', spec.name, '-n', spec.namespace, '-o', 'json']
        log.info("[k8s-secrets]    cmd: ${cmd.join(' ')}")

        def stdout = new StringBuilder()
        def stderr = new StringBuilder()

        Process process
        try {
            process = cmd.execute()
        } catch (IOException e) {
            log.error("[k8s-secrets]    kubectl could not be started: ${e.message}")
            return 0
        }
        process.consumeProcessOutput(stdout, stderr)
        int exit = process.waitFor()

        if (exit != 0) {
            log.error("[k8s-secrets]    kubectl exit=${exit}: ${stderr.toString().trim()}")
            return 0
        }

        def json = new JsonSlurper().parseText(stdout.toString())
        def data = json?.data
        if (!data) {
            log.warn("[k8s-secrets]    Secret has no 'data' entries – secret ignored.")
            return 0
        }

        int count = 0
        data.each { String key, String base64 ->
            def decoded = new String(base64.decodeBase64(), 'UTF-8')
            def propName = buildPropertyName(ext, spec, key)
            bootRun.systemProperty(propName, decoded)
            log.lifecycle("[k8s-secrets]    ${propName} = ${mask(decoded)}")
            count++
        }
        return count
    }

    private String buildPropertyName(K8sSecretsExtension ext, SecretSpec spec, String key) {
        def parts = [spec.namespace, spec.name, key]
        def name = parts.join(ext.separator)
        if (ext.replaceHyphens) {
            name = name.replace('-', ext.separator)
        }
        return name
    }

    private String mask(String value) {
        if (value == null) return 'null'
        if (value.length() <= 2) return '***'
        return value[0] + '***' + "(${value.length()} chars)"
    }
}
