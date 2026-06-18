package slin.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Functional test with Gradle TestKit.
 */
class K8sSecretsPluginFunctionalTest extends Specification {

    @TempDir
    Path testProjectDir

    def "loads a Secret-Keys and logs them"() {
        given: "a kubectl-Stub, which returns a secret with an base64-value"
        def b64 = 'aWNoIGtvbW1lIGF1cyBkZW0gS2V5VmF1bHQ='
        Path stub = testProjectDir.resolve('kubectl-stub.sh')
        Files.writeString(stub, """#!/bin/bash
cat <<'JSON'
{ "data": { "test-name": "${b64}" } }
JSON
""")
        stub.toFile().setExecutable(true)

        and: "a build, which applies the plugin and fakes a bootRun-Task"
        Files.writeString(testProjectDir.resolve('settings.gradle'), "rootProject.name = 'itest'")
        Files.writeString(testProjectDir.resolve('build.gradle'), """
            plugins { id 'slin.k8s-secrets' }

            tasks.register('bootRun', JavaExec) {
                mainClass = 'NoOp'
                classpath = files()
                doLast {
                    systemProperties.each { k, v -> println "PROP \${k}=\${v}" }
                }
                // echte JVM nicht starten:
                jvmArgs = []
            }

            k8sSecrets {
                kubectl = '${stub.toAbsolutePath()}'
                secret { namespace = 'aname'; name = 'test-secret' }
            }
        """)

        when: "der bootRun-Task läuft (nur doFirst/doLast, kein echter JVM-Start nötig)"
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments('bootRun', '--stacktrace')
            .buildAndFail()

        then: "das Plugin-Log zeigt den korrekten Property-Namen"
        result.output.contains('[k8s-secrets] -> aname/test-secret')
        result.output.contains('aname_test-secret_test-name')
    }
}
