package io.slin.gradle

import spock.lang.Specification
import spock.lang.Unroll

class SecretNamingSpec extends Specification {

    @Unroll
    def "k8s name: ns=#ns secret=#secret -> #expected"() {
        expect:
        SecretNaming.k8s('_', false, includeNs, includeSecret, 'kuma-v2', 'test-secret', 'test-name') == expected

        where:
        includeNs | includeSecret | expected
        false     | false         | 'test-name'
        true      | false         | 'kuma-v2_test-name'
        false     | true          | 'test-secret_test-name'
        true      | true          | 'kuma-v2_test-secret_test-name'
        ns = includeNs
        secret = includeSecret
    }

    def "k8s name mit replaceHyphens ersetzt Bindestriche durch den Separator"() {
        expect:
        SecretNaming.k8s('_', true, true, true, 'kuma-v2', 'test-secret', 'test-name') ==
            'kuma_v2_test_secret_test_name'
    }

    def "kvString nutzt UPPERCASE des Secret-Namens als Default"() {
        expect:
        SecretNaming.kvString(null, 'another-secret') == 'ANOTHER_SECRET'
    }

    def "kvString nutzt envName falls gesetzt"() {
        expect:
        SecretNaming.kvString('MY_ENV_NAME', 'another-secret') == 'MY_ENV_NAME'
    }

    @Unroll
    def "maxAge '#spec' => #millis ms"() {
        expect:
        SecretFileService.parseMaxAgeMillis(spec) == millis

        where:
        spec   | millis
        '45s'  | 45_000L
        '30m'  | 1_800_000L
        '2h'   | 7_200_000L
        '1d'   | 86_400_000L
        '90'   | 5_400_000L      // nackte Zahl = Minuten
        null   | 3_600_000L      // default 1h
    }

    def "ungültiges maxAge wirft Exception"() {
        when:
        SecretFileService.parseMaxAgeMillis('abc')
        then:
        thrown(IllegalArgumentException)
    }
}
