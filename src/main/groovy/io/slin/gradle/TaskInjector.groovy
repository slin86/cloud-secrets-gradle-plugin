package io.slin.gradle

import org.gradle.api.Task
import org.gradle.process.ProcessForkOptions

/**
 * Attaches a map of variables as environment variables to a task.
 * Supported are tasks that implement ProcessForkOptions
 * (for example JavaExec like bootRun, or Exec).
 */
class TaskInjector {

    static void injectEnv(Task task, Map<String, String> entries, org.gradle.api.logging.Logger log) {
        if (!(task instanceof ProcessForkOptions)) {
            log.warn("[slin-secrets] Task '${task.name}' (${task.class.simpleName}) does not support " +
                "environment variables, skipped.")
            return
        }
        def forkable = task as ProcessForkOptions
        entries.each { k, v ->
            forkable.environment(k, v)
            log.lifecycle("[slin-secrets]   set ${k} on ${task.name} = ${mask(v)}")
        }
    }

    static String mask(String value) {
        if (value == null) return 'null'
        if (value.length() <= 2) return '***'
        return value[0] + '***' + "(${value.length()} chars)"
    }
}
