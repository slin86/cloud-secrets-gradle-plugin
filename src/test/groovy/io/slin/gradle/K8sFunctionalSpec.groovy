package io.slin.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * End to end tests of the k8s variant with a kubectl stub (no real cluster).
 */
class K8sFunctionalSpec extends Specification {

    @TempDir
    Path dir

    /** Creates a kubectl stub that returns a fixed secret JSON. */
    private Path kubectlStub(String json) {
        Path stub = dir.resolve('kubectl-stub.sh')
        Files.writeString(stub, "#!/bin/bash\ncat <<'JSON'\n${json}\nJSON\n")
        stub.toFile().setExecutable(true)
        return stub
    }

    /** kubectl stub that simulates an error (source unreachable). */
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

    def "updateSecretsFile writes all data keys (default: only key as name)"() {
        given:
        def b64 = 'aWNoIGtvbW1lIGF1cyBkZW0gS2V5VmF1bHQ=' // 'ich komme aus dem KeyVault'
        def stub = kubectlStub('{ "data": { "test-name": "' + b64 + '" } }')

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
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
        result.output.contains('Wrote')
        def f = dir.resolve('build/out/secrets.env').toFile()
        f.exists()
        f.text.contains('test-name=ich komme aus dem KeyVault')
    }

    def "prefix flags build namespace_secret_key"() {
        given:
        def stub = kubectlStub('{ "data": { "test-name": "dmFsdWU=" } }') // 'value'

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
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

    def "syncSecretsFile skips a fresh file"() {
        given:
        def stub = kubectlStub('{ "data": { "a": "dmFsdWU=" } }')
        def envFile = dir.resolve('build/out/secrets.env')
        Files.createDirectories(envFile.parent)
        Files.writeString(envFile, "a=existing\n") // fresh, just created

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
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
        result.output.contains('File is fresh')
        envFile.toFile().text.contains('a=existing') // unchanged
    }

    def "when the source is unreachable the existing file is kept"() {
        given:
        def failStub = kubectlFailStub()
        def envFile = dir.resolve('build/out/secrets.env')
        Files.createDirectories(envFile.parent)
        Files.writeString(envFile, "a=cached\n")
        envFile.toFile().setLastModified(System.currentTimeMillis() - 7_200_000L) // 2h old, stale

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
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
        result.output.contains('keeping existing file')
        envFile.toFile().text.contains('a=cached') // not deleted or overwritten
    }
}
