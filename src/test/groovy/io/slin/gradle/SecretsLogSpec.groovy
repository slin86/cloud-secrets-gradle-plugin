package io.slin.gradle

import spock.lang.Specification

class SecretsLogSpec extends Specification {

    def "parseLevel maps #spec to #expected"() {
        expect:
        SecretsLog.parseLevel(spec) == expected

        where:
        spec    || expected
        null    || SecretsLog.Level.INFO
        'debug' || SecretsLog.Level.DEBUG
        'INFO'  || SecretsLog.Level.INFO
        'warn'  || SecretsLog.Level.WARN
        'error' || SecretsLog.Level.WARN
        'fatal' || SecretsLog.Level.WARN
    }

    def "parseLevel rejects unknown values"() {
        when:
        SecretsLog.parseLevel('verbose')

        then:
        thrown(IllegalArgumentException)
    }
}
