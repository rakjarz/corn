@file:Suppress("unused")

package net.rakjarz.corn

import android.content.Context
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

enum class Level {
    VERBOSE,
    INFO,
    DEBUG,
    WARN,
    ERROR
}

data class Message(
    val level: Level,
    val tag: String,
    val message: String,
    val timestamp: Long,
)

@Suppress("SameParameterValue")
object Corn {
    private val logBuffer = PublishSubject.create<Message>()
    private val disposables = CompositeDisposable()
    private var formatter: LogFormat = DefaultLogFormat()

    /**
     * ~1.66MB/~450kb gzipped.
     */
    private const val LOG_FILE_MAX_SIZE_THRESHOLD = 5 * 1024 * 1024
    // Default log retention 90 days
    private val DEFAULT_LOG_FILE_RETENTION = TimeUnit.DAYS.toMillis(90)
    private const val LOG_FILE_NAME = "insights.log"

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

    fun flush(onComplete: (() -> Unit)? = null) {
        onComplete?.run {
            println("subscribe to flush completion event")
            flushCompleted
                .take(1)
                .timeout(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .onErrorReturn { -1L }
                .filter { it > 0 }
                .subscribe {
                    rotateLogs()
                    onComplete()
                }
        }

        flush.onNext(1L)
    }

    fun rotateLogs() {
        rotateLogs(logsDir, LOG_FILE_NAME)
    }

    private fun rotateLogs(path: String, fileName: String) {
        val file = Utils.getFile(path, fileName)

        if (!Utils.compress(file, logsDir)) {
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

    fun log(level: Level, tag: String, message: String? = "", throwable: Throwable? = null) {
        val time = System.currentTimeMillis()
        var msg = ""
        if (message != null) {
            msg = message
        } else if (throwable != null) {
            msg = throwable.message ?: throwable.toString().slice(0..250)
        }

        val log = Message(level = level, tag = tag, message = msg, timestamp = time)
        logBuffer.onNext(log)
    }

    init {
        val processed = AtomicInteger(0)

        logBuffer
            .observeOn(Schedulers.computation())
            .doOnEach { if (processed.incrementAndGet() % 20 == 0) flush() }
            .buffer(flush.mergeWith(Observable.interval(5, TimeUnit.SECONDS)))
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeOn(Schedulers.io())
            .subscribe { logs ->
                try {
                    val f = Utils.getFile(logsDir, LOG_FILE_NAME)
                    Utils.writeLogsToFile(f, logs, formatter, true)

                    flushCompleted.onNext(f.length())
                } catch (e: Exception) {
                    println("Corn: flush err=${e.message}")
                }
            }
            .also { disposables.add(it) }

        flushCompleted
            .subscribeOn(Schedulers.io())
            .filter { fileSize -> fileSize > LOG_FILE_MAX_SIZE_THRESHOLD }
            .subscribe { rotateLogs() }
            .also { disposables.add(it) }
    }


}