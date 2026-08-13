package com.github.jankoran90.showlyfin.data.uploader.api

import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * DROPSHIP F2 — wrapper nad [RequestBody], který v [writeTo] hlásí počet zapsaných bajtů přes
 * [onProgress]. Používá se pro multipart upload audioknihy, aby UI mohlo ukázat [LinearProgressIndicator].
 *
 * `total` vzato z delegovaného [RequestBody] ([contentLength]); pokud ho ContentResolver nezná (-1),
 * volající (VM) má svůj reálný total z `OpenableColumns.SIZE` a počítá poměr sám.
 *
 * ForwardingSink + okio.buffer zajistí, že callback vidí každý chunk, což projde síťovým bufferem.
 */
class CountingRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, total: Long) -> Unit,
) : RequestBody() {

    override fun contentType() = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val countingSink = object : ForwardingSink(sink) {
            private var totalWritten: Long = 0L
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                totalWritten += byteCount
                onProgress(totalWritten, contentLength())
            }
        }
        delegate.writeTo(countingSink.buffer())
    }
}
