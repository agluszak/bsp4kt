import org.junit.jupiter.api.Assertions.fail
import java.util.*
import java.util.function.Predicate
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import java.util.stream.Collectors
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName


class LogMessageAccumulator() : Handler() {
    private val records: MutableList<LogRecord> = ArrayList()
    private val registeredLoggers: MutableList<Logger> = ArrayList()
    fun registerTo(clazz: KClass<*>): Logger {
        return registerTo(clazz.jvmName)
    }

    fun registerTo(name: String?): Logger {
        val logger = Logger.getLogger(name)
        logger.useParentHandlers = false
        logger.addHandler(this)
        logger.level = Level.ALL
        registeredLoggers.add(logger)
        return logger
    }

    fun unregister() {
        for (logger: Logger in registeredLoggers) {
            logger.level = null
            logger.removeHandler(this)
            logger.useParentHandlers = true
        }
        registeredLoggers.clear()
    }

    fun getRecords(): List<LogRecord> {
        return records
    }

    fun findRecord(level: Level, message: String): LogRecord? {
        synchronized(records) {
            for (r: LogRecord in records) {
                if ((level == r.level) && (message == r.message)) return r
            }
            return null
        }
    }

    fun match(predicate: Predicate<LogRecord>?): Optional<LogRecord> {
        synchronized(records) { return records.stream().filter(predicate).findFirst() }
    }

    override fun publish(record: LogRecord) {
        synchronized(records) { records.add(record) }
    }

    override fun flush() {}

    @Throws(SecurityException::class)
    override fun close() {
    }

    @Throws(InterruptedException::class)
    fun await(predicate: Predicate<LogRecord>?) {
        val startTime = System.currentTimeMillis()
        while (!match(predicate).isPresent) {
            Thread.sleep(20)
            if (System.currentTimeMillis() - startTime > TIMEOUT) {
                fail(
                    "Timeout elapsed while waiting for specific record.\n"
                            + "Logged records:\n" + recordsToString()
                ) as Any
            }
        }
    }

    @Throws(InterruptedException::class)
    fun await(level: Level, message: String) {
        val startTime = System.currentTimeMillis()
        while (findRecord(level, message) == null) {
            Thread.sleep(20)
            if (System.currentTimeMillis() - startTime > TIMEOUT) {
                fail(
                    ("Timeout elapsed while waiting for " + level + ": \"" + message + "\"\n"
                            + "Logged records:\n" + recordsToString())
                ) as Any
            }
        }
    }

    private fun recordsToString(): String {
        synchronized(records) {
            if (records.isEmpty()) return "None"
            return records.stream()
                .map { r: LogRecord ->
                    r.level.toString() + ": " + r.message
                }.collect(Collectors.joining("\n"))
        }
    }

    companion object {
        private const val TIMEOUT: Long = 2000
    }
}

