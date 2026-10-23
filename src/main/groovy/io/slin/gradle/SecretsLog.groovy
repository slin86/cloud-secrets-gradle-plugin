package io.slin.gradle

import org.gradle.api.logging.Logger

/**
 * Plugin log output filtered by the configured logLevel.
 *
 *   debug   loaded keys with type and every injected variable (masked)
 *   info    loaded keys only (default)
 *   warn    only warnings
 *   error   same as warn
 *   fatal   same as warn
 *
 * Secret values are always masked, regardless of the level.
 * Messages that pass the filter are written as lifecycle, so they are
 * visible without --info. Warnings are always written.
 */
class SecretsLog {

    enum Level { DEBUG, INFO, WARN }

    final Logger logger
    final Level level

    SecretsLog(Logger logger, Level level) {
        this.logger = logger
        this.level = level
    }

    SecretsLog(Logger logger, String level) {
        this(logger, parseLevel(level))
    }

    boolean isDebugEnabled() { level == Level.DEBUG }

    void debug(String msg) {
        if (level == Level.DEBUG) logger.lifecycle(msg)
    }

    void info(String msg) {
        if (level <= Level.INFO) logger.lifecycle(msg)
    }

    void warn(String msg) {
        logger.warn(msg)
    }

    /** Technical details (for example the executed command), only with gradle --info. */
    void trace(String msg) {
        logger.info(msg)
    }

    static Level parseLevel(String spec) {
        if (!spec) return Level.INFO
        switch (spec.trim().toLowerCase()) {
            case 'debug': return Level.DEBUG
            case 'info':  return Level.INFO
            case 'warn':
            case 'error':
            case 'fatal': return Level.WARN
            default:
                throw new IllegalArgumentException(
                    "Invalid logLevel: '${spec}' (allowed: debug, info, warn, error, fatal)")
        }
    }
}
