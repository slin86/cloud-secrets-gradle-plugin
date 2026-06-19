package io.slin.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * End-to-end Tests der KeyVault-Variante mit einem az-Stub.
 *
 * Der Stub gibt – unabhängig von den Argumenten – einen festen 'value' aus,
 * so wie 'az keyvault secret show --query value -o tsv' es täte.
 */
class KvFunctionalSpec extends Specification {

    @TempDir
    Path dir

    private Path azStub(String value) {
        Path stub = dir.resolve('az-stub.sh')
        // Wert kann mehrzeilig/JSON sein -> per printf roh ausgeben
        Files.writeString(stub, "#!/bin/bash\ncat <<'VAL'\n${value}\nVAL\n")
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

    def "type=json expandiert alle Keys zu einzelnen Variablen"() {
        given:
        def stub = azStub('{"DB_USER":"appuser","DB_PASS":"s3cr3t"}')

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            slinSecrets {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                kvSecrets {
                    az = '${stub.toAbsolutePath()}'
                    azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
                    secret { name = 'app-config'; type = 'json' }
                }
            }
        """)

        when:
        runner('updateSecretsFile').build()

        then:
        def txt = dir.resolve('build/out/secrets.env').toFile().text
        txt.contains('DB_USER=appuser')
        txt.contains('DB_PASS=s3cr3t')
    }

    def "type=string nutzt envName"() {
        given:
        def stub = azStub('plain-string-value')

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            slinSecrets {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                kvSecrets {
                    az = '${stub.toAbsolutePath()}'
                    azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
                    secret { name = 'another-secret'; type = 'string'; envName = 'MY_ENV_NAME' }
                }
            }
        """)

        when:
        runner('updateSecretsFile').build()

        then:
        dir.resolve('build/out/secrets.env').toFile().text.contains('MY_ENV_NAME=plain-string-value')
    }

    def "Injection (useFile=false) setzt env var an einem Exec-Task"() {
        given:
        def stub = azStub('injected-value')

        // Exec-Task gibt die env var aus -> wir prüfen die Ausgabe.
        writeBuild("""
            plugins { id 'io.slin.secrets' }

            tasks.register('printIt', Exec) {
                commandLine 'bash', '-c', 'echo GOT=\$MY_ENV_NAME'
            }

            slinSecrets {
                useFile = false
                tasks = ['printIt']
                kvSecrets {
                    az = '${stub.toAbsolutePath()}'
                    azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
                    secret { name = 's'; type = 'string'; envName = 'MY_ENV_NAME' }
                }
            }
        """)

        when:
        def result = runner('printIt').build()

        then:
        result.output.contains('GOT=injected-value')
    }
}
