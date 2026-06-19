package io.slin.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end Tests der k8s-Variante mit einem kubectl-Stub (kein echter Cluster).
 */
class K8sFunctionalSpec extends Specification {

    @TempDir
    Path dir

    /** legt einen kubectl-Stub an, der ein festes Secret-JSON liefert. */
    private Path kubectlStub(String json) {
        Path stub = dir.resolve('kubectl-stub.sh')
        Files.writeString(stub, "#!/bin/bash\ncat <<'JSON'\n${json}\nJSON\n")
        stub.toFile().setExecutable(true)
        return stub
    }

    /** kubectl-Stub, der einen Fehler simuliert (Quelle nicht erreichbar). */
    private Path kubectlFailStub() {
        Path stub = dir.resolve('kubectl-fail.sh')
        Files.writeString(stub, "#!/bin/bash\necho 'Unable to connect to the server' >&2\nexit 1\n")
        stub.toFile().setExecutable(true)
        return stub
    }

    private void writeBuild(String body) {
        Files.writeString(dir.resolve('settings.gradle'), "rootProject.name = 'itest'")
        Files.writeString(dir.resolve('build.gradle'), body)
    }

    private GradleRunner runner(String... args) {
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .withArguments((args as List) + '--stacktrace')
    }

    def "updateSecretsFile schreibt alle data-Keys (default: nur key als Name)"() {
        given:
        def b64 = 'aWNoIGtvbW1lIGF1cyBkZW0gS2V5VmF1bHQ=' // 'ich komme aus dem KeyVault'
        def stub = kubectlStub('{ "data": { "test-name": "' + b64 + '" } }')

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            slinSecrets {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                k8sSecrets {
                    kubectl = '${stub.toAbsolutePath()}'
                    secret { namespace = 'kuma-v2'; name = 'test-secret' }
                }
            }
        """)

        when:
        def result = runner('updateSecretsFile').build()

        then:
        result.output.contains('geschrieben')
        def f = dir.resolve('build/out/secrets.env').toFile()
        f.exists()
        f.text.contains('test-name=ich komme aus dem KeyVault')
    }

    def "Präfix-Flags erzeugen namespace_secret_key"() {
        given:
        def stub = kubectlStub('{ "data": { "test-name": "dmFsdWU=" } }') // 'value'

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            slinSecrets {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                k8sSecrets {
                    kubectl = '${stub.toAbsolutePath()}'
                    includeNamespacePrefix = true
                    includeSecretNamePrefix = true
                    secret { namespace = 'kuma-v2'; name = 'test-secret' }
                }
            }
        """)

        when:
        runner('updateSecretsFile').build()

        then:
        dir.resolve('build/out/secrets.env').toFile().text.contains('kuma-v2_test-secret_test-name=value')
    }

    def "syncSecretsFile überspringt frische Datei"() {
        given:
        def stub = kubectlStub('{ "data": { "a": "dmFsdWU=" } }')
        def envFile = dir.resolve('build/out/secrets.env')
        Files.createDirectories(envFile.parent)
        Files.writeString(envFile, "a=existing\n") // frisch (gerade erstellt)

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            slinSecrets {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                maxAge = '1h'
                k8sSecrets {
                    kubectl = '${stub.toAbsolutePath()}'
                    secret { namespace = 'ns'; name = 's' }
                }
            }
        """)

        when:
        def result = runner('syncSecretsFile').build()

        then:
        result.output.contains('Datei frisch')
        envFile.toFile().text.contains('a=existing') // unverändert
    }

    def "bei Quelle-nicht-erreichbar bleibt die vorhandene Datei erhalten"() {
        given:
        def failStub = kubectlFailStub()
        def envFile = dir.resolve('build/out/secrets.env')
        Files.createDirectories(envFile.parent)
        Files.writeString(envFile, "a=cached\n")
        envFile.toFile().setLastModified(System.currentTimeMillis() - 7_200_000L) // 2h alt -> stale

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            slinSecrets {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                maxAge = '1h'
                k8sSecrets {
                    kubectl = '${failStub.toAbsolutePath()}'
                    secret { namespace = 'ns'; name = 's' }
                }
            }
        """)

        when:
        def result = runner('syncSecretsFile').build()

        then:
        result.output.contains('behalte vorhandene Datei')
        envFile.toFile().text.contains('a=cached') // NICHT gelöscht/überschrieben
    }
}
