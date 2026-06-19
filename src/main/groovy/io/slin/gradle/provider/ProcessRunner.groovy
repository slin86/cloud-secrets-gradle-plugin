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
        def out = new StringBuilder()
        def err = new StringBuilder()
        Process process
        try {
            process = cmd.execute()
        } catch (IOException e) {
            throw new io.slin.gradle.provider.SecretResolveException("Command not startable: ${cmd.first()} (${e.message})", e)
        }
        process.consumeProcessOutput(out, err)
        int exit = process.waitFor()
        return new Result(exit: exit, stdout: out.toString(), stderr: err.toString())
    }
}
