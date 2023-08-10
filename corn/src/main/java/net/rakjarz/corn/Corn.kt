@file:Suppress("unused")

package net.rakjarz.corn

import android.content.Context
import io.reactivex.rxjava3.core.BackpressureStrategy
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream

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

    /**
     * ~1.66MB/~450kb gzipped.
     */
    private const val LOG_FILE_MAX_SIZE_THRESHOLD = 5 * 1024 * 1024
    private lateinit var filePath: String
    private const val LOG_FILE_NAME = "insights.log"
    private val LOG_FILE_RETENTION = TimeUnit.DAYS.toMillis(14)
    private val LOG_FILE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    private val LOG_LINE_TIME_FORMAT = SimpleDateFormat("MMM-dd HH:mm:ss", Locale.US)

    private var flush = BehaviorSubject.create<Long>()
    private var flushCompleted = BehaviorSubject.create<Long>()

    @JvmStatic fun initialize(
        context: Context
    ) {
        filePath = try {
            getLogsDirectoryFromPath(context.filesDir.absolutePath)
        } catch (e: FileNotFoundException) {
            // Fallback to default path
            context.filesDir.absolutePath
        }

        // Plant Timber Here
    }

    private fun getLogsDirectoryFromPath(fileDir: String): String {
        val dir = File(fileDir, "logs")
        if (!dir.exists() && !dir.mkdirs()) {
            throw FileNotFoundException("Unable to create logs file")
        }

        return dir.absolutePath
    }

    @JvmStatic fun cleanup() {
        flush()
        disposables.clear()
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
        rotateLogs(filePath, LOG_FILE_NAME)
    }

    private fun rotateLogs(path: String, fileName: String) {
        val file = getFile(path, fileName)

        if (!compress(file)) {
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
                        && it.lastModified() + LOG_FILE_RETENTION < currentTime
            }?.map { it.delete() }
    }

    private fun compress(file: File): Boolean {
        try {
            val compressed = File(
                file.parentFile?.absolutePath ?: filePath,
                "${file.name.substringBeforeLast(".")}_${LOG_FILE_TIME_FORMAT.format(Date())}.gz"
            )

            BufferedInputStream(FileInputStream(file)).use { fis ->
                BufferedOutputStream(FileOutputStream(compressed)).use { fos ->
                    GZIPOutputStream(fos).use { gzos ->
                        val buffer = ByteArray(1024)
                        var length: Int

                        while (fis.read(buffer).also { length = it } != -1) {
                            gzos.write(buffer, 0, length)
                        }

                        gzos.finish()
                        gzos.close()
                    }
                }
            }
        } catch (e: IOException) {
            logException(e)

            return false
        }

        return true
    }

    private fun logException(e: Exception) {
        println("Corn err: ${e.message}")
    }

    private fun getFile(path: String, name: String): File {
        val file = File(path, name)
        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Unable to load log file")
        }

        if (!file.canWrite()) {
            throw IOException("Log file not writable")
        }

        return file
    }

    init {
        val processed = AtomicInteger(0)

        logBuffer
            .observeOn(Schedulers.computation())
            .doOnEach { if (processed.incrementAndGet() % 20 == 0) flush() }
            .buffer(flush.mergeWith(Observable.interval(5, TimeUnit.SECONDS)))
            .toFlowable(BackpressureStrategy.BUFFER)
            .subscribeOn(Schedulers.io())
            .subscribe {
                try {
                    // Open file
                    val f = getFile(filePath, LOG_FILE_NAME)

                    // Write to log
                    FileWriter(f, true).use { fw ->
                        // Write log lines to the file
                        it.forEach { (level, tag, message, timestamp) -> fw.append("${LOG_LINE_TIME_FORMAT.format(Date(timestamp))}\t${tag}\t${level}\t$message\n") }

                        // Write a line indicating the number of log lines proceed
//                        fw.append("${LOG_LINE_TIME_FORMAT.format(Date())}\t${Level.VERBOSE}\tFlushing logs -- total processed: $processed\n")

                        fw.flush()
                    }

                    // Validate file size
                    flushCompleted.onNext(f.length())
                } catch (e: Exception) {
                    logException(e)
                }
            }
            .also { disposables.add(it) }

        flushCompleted
            .subscribeOn(Schedulers.io())
            .filter { fileSize -> fileSize > LOG_FILE_MAX_SIZE_THRESHOLD }
            .subscribe { rotateLogs() }
            .also { disposables.add(it) }
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
}