package net.rakjarz.corn

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DefaultLogFormat : LogFormat {
    private val logLineDataFormat = SimpleDateFormat("MMM-dd HH:mm:ss", Locale.US)

    override fun format(log: LogData): String {
        return "${logLineDataFormat.format(Date(log.timestamp))} ${log.tag.replace(" ", "-")} ${log.level} ${log.message}\n"
    }
}
