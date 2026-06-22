package io.slin.gradle

import io.slin.gradle.provider.ProcessRunner
import spock.lang.Specification

class ProcessRunnerSpec extends Specification {

    def "on Windows the command is wrapped in cmd /c"() {
        expect:
        ProcessRunner.toPlatformCommand(['az', 'keyvault', 'secret', 'show'], 'Windows 11') ==
                ['cmd', '/c', 'az', 'keyvault', 'secret', 'show']
    }

    def "on non Windows the command is returned unchanged"() {
        expect:
        ProcessRunner.toPlatformCommand(['kubectl', 'get', 'secret'], 'Linux') ==
                ['kubectl', 'get', 'secret']
        ProcessRunner.toPlatformCommand(['az', 'keyvault'], 'Mac OS X') ==
                ['az', 'keyvault']
    }
}
