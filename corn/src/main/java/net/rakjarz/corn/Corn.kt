@file:Suppress("unused")

package net.rakjarz.corn

import android.content.Context
import android.util.Log
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.PrintWriter
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class Level {
    VERBOSE,
    INFO,
    DEBUG,
    WARN,
    ERROR;

    companion object {
        fun fromPriority(priority: Int): Level {
            return when(priority) {
                Log.INFO -> INFO
                Log.DEBUG -> DEBUG
                Log.WARN -> WARN
                Log.ERROR -> ERROR
                Log.VERBOSE -> VERBOSE
                else -> VERBOSE
            }
        }
    }
}

data class LogData(
    val level: Level,
    val tag: String,
    val message: String,
    val timestamp: Long,
)

@Suppress("SameParameterValue")
object Corn {
    private val logBuffer = PublishSubject.create<LogData>()
    private val disposables = CompositeDisposable()
    private var formatter: LogFormat = DefaultLogFormat()

    /**
     * ~1.66MB/~450kb gzipped.
     */
    private const val LOG_FILE_PREFIX = "insights"
    private const val LOG_FILE_MAX_SIZE_THRESHOLD = 5 * 1024 * 1024
    // Default log retention 90 days
    private val DEFAULT_LOG_FILE_RETENTION = TimeUnit.DAYS.toMillis(90)
    private const val LOG_FILE_NAME = "$LOG_FILE_PREFIX.log"
    private const val TEMP_LOG_FILE_NAME = "device_logs.gz"

    private lateinit var logsDir: String
    private var retentionMillis: Long = DEFAULT_LOG_FILE_RETENTION

    private var flush = BehaviorSubject.create<Long>()
    private var flushCompleted = BehaviorSubject.create<Long>()

    @JvmStatic
    @JvmOverloads
    fun initialize(
        context: Context,
        retentionMillis: Long = DEFAULT_LOG_FILE_RETENTION,
        logFormat: LogFormat = DefaultLogFormat()
    ) {
        this.retentionMillis = retentionMillis
        this.formatter = logFormat

        logsDir = try {
            getLogsDirectoryFromPath(context.filesDir.absolutePath)
        } catch (e: FileNotFoundException) {
            // Fallback to default path
            context.filesDir.absolutePath
        }
    }

    private fun getLogsDirectoryFromPath(fileDir: String): String {
        val dir = File(fileDir, "logs")
        if (!dir.exists() && !dir.mkdirs()) {
            throw FileNotFoundException("Unable to create logs file")
        }

        return dir.absolutePath
    }

    @JvmStatic fun cleanup() {
        flush { disposables.clear() }
    }

    @JvmStatic
    @JvmOverloads
    fun flush(onComplete: (() -> Unit)? = null) {
        onComplete?.run {
            println("subscribe to flush completion event")
            flushCompleted
                .take(1)
                .timeout(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .onErrorReturn { -1L }
                .filter { it >= 0 }
                .subscribe {
                    rotateLogs()
                    onComplete()
                }
        }

        flush.onNext(1L)
    }

    @JvmStatic
    @JvmOverloads
    fun rotateLogs(forced: Boolean = false) {
        rotateLogs(logsDir, LOG_FILE_NAME, forced)
    }

    private fun rotateLogs(path: String, fileName: String, forced: Boolean = false) {
        val file = Utils.getFile(path, fileName)
        if (!forced && file.length() < LOG_FILE_MAX_SIZE_THRESHOLD) return

        val compressed = Utils.getCompressedFile(logsDir, LOG_FILE_PREFIX)
        if (!Utils.compress(file, compressed)) {
            // Unable to compress file
            return
        }

        // Truncate the file to zero.
        PrintWriter(file).close()

        // Iterate over the gzipped files in the directory and delete the files outside the
        // retention period.
        val currentTime = System.currentTimeMillis()
        file.parentFile?.listFiles()
            ?.filter {
                it.extension.lowercase(Locale.ROOT) == "gz"
                        && it.lastModified() + DEFAULT_LOG_FILE_RETENTION < currentTime
            }?.map { it.delete() }
    }

    @JvmStatic
    @JvmOverloads
    fun log(level: Level, tag: String, message: String? = "", throwable: Throwable? = null) {
        val time = System.currentTimeMillis()
        var msg = ""
        if (message != null) {
            msg = message
        } else if (throwable != null) {
            msg = throwable.message ?: throwable.toString().slice(0..250)
        }

        val log = LogData(level = level, tag = tag, message = msg, timestamp = time)
        logBuffer.onNext(log)
    }

    @JvmStatic
    @JvmOverloads
    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        log(Level.fromPriority(priority), tag, message, throwable)
    }

    @JvmStatic
    fun getAllLogsAsStringList(): List<String> {
        return Single.fromCallable {
            var lines = listOf<String>()
            val logFile = File(logsDir, LOG_FILE_NAME)
            if (!logFile.exists()) {
                println("CORN: get logs err: log file not exists")
                return@fromCallable lines
            }
            if (!logFile.canRead()) {
                println("CORN: get logs err: log file is not readable")
                return@fromCallable lines
            }

            try {
                BufferedReader(FileReader(logFile)).use { reader ->
                    lines = reader.readLines()
                }
            } catch (e: Exception) {
                println("CORN: process read logs err: ${e.message}")
            }

            return@fromCallable lines
        }
            .subscribeOn(Schedulers.io())
            .blockingGet()
    }

    @JvmStatic
    @JvmOverloads
    fun getLogsAsStringList(offset: Int = 0, limit: Int = 200): List<String> {
        return Single.fromCallable {
            val lines = mutableListOf<String>()
            val logFile = File(logsDir, LOG_FILE_NAME)
            if (!logFile.exists()) {
                println("CORN: get logs err: log file not exists")
                return@fromCallable lines
            }
            if (!logFile.canRead()) {
                println("CORN: get logs err: log file is not readable")
                return@fromCallable lines
            }

            try {
                ReversedLinesFileReader(logFile).use { reader ->
                    var line: String?
                    val index = AtomicLong()
                    do {
                        line = reader.readLine()
                        if (index.incrementAndGet() < offset) continue
                        if (lines.size >= limit) break

                        line?.let { lines.add(it) }
                    } while (line != null)
                    reader.close()
                }
            } catch (e: Exception) {
                println("CORN: process read logs err: ${e.message}")
            }

            return@fromCallable lines
        }
            .subscribeOn(Schedulers.io())
            .blockingGet()

    }

    @JvmStatic
    @JvmOverloads
    fun clear(all: Boolean = true) {
        val rootDir = File(logsDir)
        if (!rootDir.exists() && !rootDir.isDirectory) return

        val files = rootDir.listFiles()
        files?.forEach {
            if (it.name.startsWith(LOG_FILE_PREFIX)) {
                println("CORN: clear log file detected name=${it.name} ext=${it.extension}")
                when(it.extension) {
                    "gz" -> { if (all) it.delete() }
                    "log" -> { PrintWriter(it).close() }
                    else -> {}
                }
            }
        }
    }

    fun collectReports(): MutableList<File> {
        val files = mutableListOf<File>()
        val rootDir = File(logsDir)
        if (!rootDir.exists() && !rootDir.isDirectory) return files

        // get all gz files
        rootDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(LOG_FILE_PREFIX)
                && file.extension.equals("gz", ignoreCase = true)) {
                files.add(file)
            }
        }

        val tempFile = File(logsDir, TEMP_LOG_FILE_NAME)
        // check if temp_log file exist
        if (tempFile.exists() && tempFile.isFile) tempFile.delete()

        // compress log file to temp file
        val logFile = File(logsDir, LOG_FILE_NAME)
        if (!logFile.exists()) return files

        if (logFile.length() <= 0) return files
        if (!Utils.compress(logFile, tempFile)) return files

        files.add(tempFile)
        return files
    }


    init {
        val processed = AtomicInteger(0)

        logBuffer
            .observeOn(Schedulers.computation())
            .doOnEach { if (processed.incrementAndGet() % 20 == 0) flush() }
            .buffer(flush.mergeWith(Observable.interval(5, TimeUnit.MINUTES)))
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeOn(Schedulers.io())
            .subscribe { logs ->
                try {
                    val f = Utils.getFile(logsDir, LOG_FILE_NAME)
                    Utils.writeLogsToFile(f, logs, formatter, false)

                    flushCompleted.onNext(f.length())
                } catch (e: Exception) {
                    println("Corn: flush err=${e.message}")
                }
            }
            .also { disposables.add(it) }

        flushCompleted
            .subscribeOn(Schedulers.io())
            .filter { fileSize -> fileSize > LOG_FILE_MAX_SIZE_THRESHOLD }
            .subscribe { rotateLogs(true) }
            .also { disposables.add(it) }
    }


}