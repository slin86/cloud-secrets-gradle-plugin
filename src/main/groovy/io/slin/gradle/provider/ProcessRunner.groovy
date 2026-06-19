package io.slin.gradle.provider

/**
 * Führt ein externes Kommando aus und greift stdout/stderr sauber ab.
 *
 * consumeProcessOutput VOR waitFor() vermeidet das berüchtigte
 * "java.io.IOException: Stream closed" bei Groovys Process-Handling.
 */
class ProcessRunner {

    static class Result {
        int exit
        String stdout
        String stderr
    }

    /**
     * @throws SecretResolveException wenn das Binary nicht gestartet werden kann.
     */
    static Result run(List<String> cmd) {
        def out = new StringBuilder()
        def err = new StringBuilder()
        Process process
        try {
            process = cmd.execute()
        } catch (IOException e) {
            throw new SecretResolveException("Kommando nicht startbar: ${cmd.first()} (${e.message})", e)
        }
        process.consumeProcessOutput(out, err)
        int exit = process.waitFor()
        return new Result(exit: exit, stdout: out.toString(), stderr: err.toString())
    }
}
