package io.slin.gradle

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SecretFileServiceSpec extends Specification {

    @TempDir
    Path tmp

    def "writing and reading env format returns the same values"() {
        given:
        def f = tmp.resolve('secrets.env').toFile()
        def svc = new io.slin.gradle.SecretFileService(f, 'env', 3_600_000L)

        when:
        svc.write([FOO: 'bar', TEST_NAME: 'value from key vault'])
        def back = svc.read()

        then:
        f.exists()
        back['FOO'] == 'bar'
        back['TEST_NAME'] == 'value from key vault'
    }

    def "writing and reading properties format"() {
        given:
        def f = tmp.resolve('secrets.properties').toFile()
        def svc = new io.slin.gradle.SecretFileService(f, 'properties', 3_600_000L)

        when:
        svc.write(['my.key': 'value=with=equals'])
        def back = svc.read()

        then:
        back['my.key'] == 'value=with=equals'
    }

    def "isFresh is false for a missing file"() {
        expect:
        !new io.slin.gradle.SecretFileService(tmp.resolve('nope.env').toFile(), 'env', 3_600_000L).isFresh()
    }

    def "isFresh is false when the file is older than maxAge"() {
        given:
        def f = tmp.resolve('old.env').toFile()
        def svc = new io.slin.gradle.SecretFileService(f, 'env', 1_000L) // 1 second
        svc.write([A: '1'])
        f.setLastModified(System.currentTimeMillis() - 60_000L) // 1 minute old

        expect:
        !svc.isFresh()
    }

    def "isFresh is true when the file is young enough"() {
        given:
        def f = tmp.resolve('fresh.env').toFile()
        def svc = new io.slin.gradle.SecretFileService(f, 'env', 3_600_000L)
        svc.write([A: '1'])

        expect:
        svc.isFresh()
    }
}
