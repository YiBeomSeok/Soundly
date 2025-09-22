package com.soundly.media.recorder.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface Sink {
    suspend fun write(bytes: ByteArray)
    suspend fun flush()
    suspend fun close()
}

interface FileBackedSink {
    val outputFile: File
}

class FileSink(private val target: File) : Sink, FileBackedSink {
    private val fos: FileOutputStream = FileOutputStream(target)
    override val outputFile: File get() = target

    override suspend fun write(bytes: ByteArray) {
        withContext(Dispatchers.IO) { fos.write(bytes) }
    }

    override suspend fun flush() {
        withContext(Dispatchers.IO) { fos.flush() }
    }

    override suspend fun close() {
        withContext(Dispatchers.IO) { fos.close() }
    }
}
