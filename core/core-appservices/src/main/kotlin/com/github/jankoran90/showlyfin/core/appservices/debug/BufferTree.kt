package com.github.jankoran90.showlyfin.core.appservices.debug

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

data class LogEntry(
    val timestamp: Long,
    val priority: Int,
    val tag: String?,
    val message: String,
    val throwable: Throwable?,
)

class BufferTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        buffer.add(LogEntry(System.currentTimeMillis(), priority, tag, message, t))
        while (buffer.size > MAX_LINES) buffer.poll()
    }

    fun snapshot(): List<LogEntry> = buffer.toList()

    fun formatLog(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return buildString {
            snapshot().forEach { entry ->
                append(sdf.format(Date(entry.timestamp)))
                append(' ')
                append(priorityChar(entry.priority))
                append(' ')
                append(entry.tag ?: "?")
                append(": ")
                append(entry.message)
                append('\n')
                entry.throwable?.let { append(Log.getStackTraceString(it)).append('\n') }
            }
        }
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.VERBOSE -> 'V'
        Log.DEBUG -> 'D'
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> '?'
    }

    companion object {
        private const val MAX_LINES = 1000
        val INSTANCE = BufferTree()
        private val buffer = ConcurrentLinkedDeque<LogEntry>()
    }
}
