package io.slin.gradle

import org.gradle.api.Task
import org.gradle.process.ProcessForkOptions

/**
 * Hängt eine Map von Variablen als Umgebungsvariablen an einen Task.
 * Unterstützt werden Tasks, die ProcessForkOptions implementieren
 * (z.B. JavaExec wie bootRun, oder Exec).
 */
class TaskInjector {

    static void injectEnv(Task task, Map<String, String> entries, org.gradle.api.logging.Logger log) {
        if (!(task instanceof ProcessForkOptions)) {
            log.warn("[slin-secrets] Task '${task.name}' (${task.class.simpleName}) unterstützt keine " +
                "Umgebungsvariablen – übersprungen.")
            return
        }
        def forkable = task as ProcessForkOptions
        entries.each { k, v ->
            forkable.environment(k, v)
            log.lifecycle("[slin-secrets]   ${task.name} <- ${k} = ${mask(v)}")
        }
    }

    static String mask(String value) {
        if (value == null) return 'null'
        if (value.length() <= 2) return '***'
        return value[0] + '***' + "(${value.length()} chars)"
    }
}
