package io.slin.gradle

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class SecretFileServiceSpec extends Specification {

    @TempDir
    Path tmp

    def "env-Format schreiben und wieder lesen liefert dieselben Werte"() {
        given:
        def f = tmp.resolve('secrets.env').toFile()
        def svc = new SecretFileService(f, 'env', 3_600_000L)

        when:
        svc.write([FOO: 'bar', TEST_NAME: 'ich komme aus dem KeyVault'])
        def back = svc.read()

        then:
        f.exists()
        back['FOO'] == 'bar'
        back['TEST_NAME'] == 'ich komme aus dem KeyVault'
    }

    def "properties-Format schreiben und wieder lesen"() {
        given:
        def f = tmp.resolve('secrets.properties').toFile()
        def svc = new SecretFileService(f, 'properties', 3_600_000L)

        when:
        svc.write(['my.key': 'value=with=equals'])
        def back = svc.read()

        then:
        back['my.key'] == 'value=with=equals'
    }

    def "isFresh ist false bei fehlender Datei"() {
        expect:
        !new SecretFileService(tmp.resolve('nope.env').toFile(), 'env', 3_600_000L).isFresh()
    }

    def "isFresh ist false wenn Datei älter als maxAge"() {
        given:
        def f = tmp.resolve('old.env').toFile()
        def svc = new SecretFileService(f, 'env', 1_000L) // 1 Sekunde
        svc.write([A: '1'])
        f.setLastModified(System.currentTimeMillis() - 60_000L) // 1 Minute alt

        expect:
        !svc.isFresh()
    }

    def "isFresh ist true wenn Datei jung genug"() {
        given:
        def f = tmp.resolve('fresh.env').toFile()
        def svc = new SecretFileService(f, 'env', 3_600_000L)
        svc.write([A: '1'])

        expect:
        svc.isFresh()
    }
}
