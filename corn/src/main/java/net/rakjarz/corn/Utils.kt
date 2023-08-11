package net.rakjarz.corn

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream

internal object Utils {
    fun getFile(path: String, name: String): File {
        val file = File(path, name)
        if (!file.exists() && !file.createNewFile()) {
            throw IOException("Unable to load log file")
        }

        if (!file.canWrite()) {
            throw IOException("Log file not writable")
        }

        return file
    }

    fun writeLogsToFile(file: File, logs: List<LogData>, format: LogFormat, indicate: Boolean) {
        FileWriter(file, true).use { fw ->
            logs.forEach { log -> fw.append(format.format(log)) }

            if (indicate) {
                fw.append(format.format(lineBreakInfoLog()))
            }

            fw.flush()
        }
    }

    private fun lineBreakInfoLog(): LogData {
        return LogData(
            level = Level.VERBOSE,
            tag = "Corn",
            message = "Flushing logs -- total processed",
            timestamp = System.currentTimeMillis()
        )
    }

    private val LOG_FILE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    fun compress(file: File, compressed: File): Boolean {
        try {
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
            println("Corn: compress err=${e.message}")

            return false
        }

        return true
    }

    fun getCompressedFile(logsDir: String, prefix: String): File {
        return File(logsDir, "${prefix}_${LOG_FILE_TIME_FORMAT.format(Date())}.gz")
    }
}