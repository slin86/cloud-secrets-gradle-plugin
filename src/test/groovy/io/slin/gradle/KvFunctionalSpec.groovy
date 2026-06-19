package io.slin.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * End to end tests of the Key Vault variant with an az stub.
 *
 * The stub prints a fixed value regardless of the arguments, just like
 * 'az keyvault secret show --query value -o tsv' would.
 */
class KvFunctionalSpec extends Specification {

    @TempDir
    Path dir

    private Path azStub(String value) {
        Path stub = dir.resolve('az-stub.sh')
        // value can be multi line or JSON, print it raw
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

    def "type json expands all keys into separate variables"() {
        given:
        def stub = azStub('{"DB_USER":"appuser","DB_PASS":"s3cr3t"}')

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
                useFile = true
                targetEnvFile = "build/out/secrets.properties"
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
        def txt = dir.resolve('build/out/secrets.properties').toFile().text
        txt.contains('DB_USER=appuser')
        txt.contains('DB_PASS=s3cr3t')
    }

    def "type string uses envName"() {
        given:
        def stub = azStub('plain-string-value')

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
                useFile = true
                targetEnvFile = "build/out/secrets.properties"
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
        dir.resolve('build/out/secrets.properties').toFile().text.contains('MY_ENV_NAME=plain-string-value')
    }

    def "injection with useFile false sets an env var on an Exec task"() {
        given:
        def stub = azStub('injected-value')

        // The Exec task prints the env var so we can check the output.
        writeBuild("""
            plugins { id 'io.slin.secrets' }

            tasks.register('printIt', Exec) {
                commandLine 'bash', '-c', 'echo GOT=\$MY_ENV_NAME'
            }

            secretsLoader {
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
