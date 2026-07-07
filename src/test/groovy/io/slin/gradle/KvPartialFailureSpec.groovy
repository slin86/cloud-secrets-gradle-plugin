package io.slin.gradle

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies that one inaccessible Key Vault secret does not break the build and
 * does not prevent the accessible secret from being loaded.
 */
class KvPartialFailureSpec extends Specification {

    @TempDir
    Path dir

    private Path azPartialStub() {
        Path stub = dir.resolve('az-partial.sh')
        Files.writeString(stub, '''#!/bin/bash
if [[ "$*" == *good-secret* ]]; then
  echo 'good-value'
  exit 0
else
  echo 'Caller is not authorized to perform action on resource' >&2
  exit 1
fi
''')
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

    def "one inaccessible secret is skipped, the accessible one is still written, build succeeds"() {
        given:
        def stub = azPartialStub()

        writeBuild("""
            plugins { id 'io.slin.secrets' }
            secretsLoader {
                useFile = true
                targetEnvFile = "build/out/secrets.env"
                kvSecrets {
                    az = '${stub.toAbsolutePath()}'
                    azureKeyVaultUrl = 'https://my-vault.vault.azure.net/'
                    secret { name = 'good-secret'; type = 'string'; envName = 'GOOD' }
                    secret { name = 'bad-secret';  type = 'string'; envName = 'BAD' }
                }
            }
        """)

        when:
        def result = runner('updateSecretsFile').build()

        then:
        result.output.contains('skipping bad-secret')
        def txt = dir.resolve('build/out/secrets.env').toFile().text
        txt.contains('GOOD=good-value')
        !txt.contains('BAD=')
    }
}
