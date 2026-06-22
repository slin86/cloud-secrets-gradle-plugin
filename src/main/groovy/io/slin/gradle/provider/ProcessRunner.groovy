package io.slin.gradle.provider

/**
 * Runs an external command and consumes stdout and stderr cleanly.
 *
 * Calling consumeProcessOutput before waitFor() avoids the well known
 * "java.io.IOException: Stream closed" with Groovy process handling.
 */
class ProcessRunner {

    static class Result {
        int exit
        String stdout
        String stderr
    }

    /**
     * Throws SecretResolveException when the binary cannot be started.
     */
    static Result run(List<String> cmd) {
        def launch = toPlatformCommand(cmd)
        def out = new StringBuilder()
        def err = new StringBuilder()
        Process process
        try {
            process = launch.execute()
        } catch (IOException e) {
            throw new SecretResolveException("Command not startable: ${cmd.first()} (${e.message})", e)
        }
        process.consumeProcessOutput(out, err)
        int exit = process.waitFor()
        return new Result(exit: exit, stdout: out.toString(), stderr: err.toString())
    }

    /**
     * On Windows tools like az are az.cmd, which Java cannot launch directly
     * because CreateProcess only appends .exe. Routing through cmd /c lets the
     * Windows shell resolve .cmd and .bat as well. On other systems the command
     * is returned unchanged.
     */
    static List<String> toPlatformCommand(List<String> cmd) {
        return toPlatformCommand(cmd, System.getProperty('os.name', ''))
    }

    static List<String> toPlatformCommand(List<String> cmd, String osName) {
        boolean windows = osName?.toLowerCase()?.contains('win')
        return windows ? (['cmd', '/c'] + cmd) : cmd
    }
}
