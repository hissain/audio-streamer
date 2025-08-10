package com.hissain.android.demo.hearingaid

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.media.*
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext

class SocketService : Service(), CoroutineScope {
    private val TAG = "SocketService"
    private val binder = LocalBinder()
    private var socket: Socket? = null
    private var outStream: OutputStream? = null
    private var inStream: InputStream? = null
    private val connected = AtomicBoolean(false)
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

    inner class LocalBinder : Binder() {
        fun getService(): SocketService = this@SocketService
    }

    override fun onBind(intent: Intent?): IBinder? = binder
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        job.cancel()
    }

    fun connect(host: String, port: Int, onLog: (String)->Unit) {
        launch {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s
                outStream = s.getOutputStream()
                inStream = s.getInputStream()
                connected.set(true)
                onLog("Connected to $host:$port")
                // start reader loop
                readerLoop(onLog)
            } catch (e: Exception) {
                onLog("Connect error: ${e.message}")
                disconnect()
            }
        }
    }

    fun disconnect() {
        connected.set(false)
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun readerLoop(onLog: (String)->Unit) {
        launch {
            val ins = inStream ?: return@launch
            try {
                val br = BufferedInputStream(ins)
                while (connected.get()) {
                    // read a header line
                    val header = readLineASCII(br) ?: break
                    when (header.trim()) {
                        "TEXT_RESP" -> {
                            val lenb = readExact(br, 4) ?: break
                            val L = ByteBuffer.wrap(lenb).order(ByteOrder.BIG_ENDIAN).int
                            val data = readExact(br, L) ?: break
                            val text = String(data, Charsets.UTF_8)
                            onLog("TEXT_RESP: $text")
                        }
                        "AUDIO_RESP" -> {
                            val lenb = readExact(br, 4) ?: break
                            val L = ByteBuffer.wrap(lenb).order(ByteOrder.BIG_ENDIAN).int
                            val data = readExact(br, L) ?: break
                            onLog("Received AUDIO_RESP (${data.size} bytes). Playing...")
                            playWavFromBytes(data, onLog)
                        }
                        else -> {
                            onLog("Unknown header from server: $header")
                        }
                    }
                }
            } catch (e: Exception) {
                onLog("Reader error: ${e.message}")
            } finally {
                disconnect()
                onLog("Disconnected by reader")
            }
        }
    }

    private fun readLineASCII(ins: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = ins.read()
            if (b == -1) return null
            if (b == '\n'.code) break
            sb.append(b.toChar())
        }
        return sb.toString()
    }
    private fun readExact(ins: InputStream, n: Int): ByteArray? {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = ins.read(buf, read, n - read)
            if (r == -1) return null
            read += r
        }
        return buf
    }

    // send text query
    fun sendTextQuery(text: String, onLog: (String)->Unit) {
        launch {
            try {
                val out = outStream ?: throw IOException("Not connected")
                val bytes = text.toByteArray(Charsets.UTF_8)
                val header = "TEXT\n"
                out.write(header.toByteArray(Charsets.US_ASCII))
                out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(bytes.size).array())
                out.write(bytes)
                out.flush()
                onLog("Sent TEXT: $text")
            } catch (e: Exception) {
                onLog("Send text error: ${e.message}")
            }
        }
    }

    // audio streaming
    private var audioJob: Job? = null
    private var isStreaming = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun startAudioStream(sampleRate: Int, audioSource: Int, onLog: (String)->Unit) {
        if (isStreaming.get()) {
            onLog("Already streaming")
            return
        }
        audioJob = launch @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
            try {
                val out = outStream ?: throw IOException("Not connected")
                // send AUDIO_START and metadata
                val meta = "AUDIO_START\nsample_rate:$sampleRate\nchannels:1\nformat:pcm_s16\n\n"
                out.write(meta.toByteArray(Charsets.US_ASCII))
                out.flush()
                // setup AudioRecord
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBuf, sampleRate * 2) // some room
                val recorder = AudioRecord.Builder()
                    .setAudioSource(audioSource)
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                val buf = ByteArray(2048)
                recorder.startRecording()
                isStreaming.set(true)
                onLog("Audio recording started")
                while (isStreaming.get()) {
                    val r = recorder.read(buf, 0, buf.size)
                    if (r > 0) {
                        // send chunk: 4-byte BE length + bytes
                        out.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(r).array())
                        out.write(buf, 0, r)
                        out.flush()
                    } else {
                        delay(10)
                    }
                }
                recorder.stop()
                recorder.release()
                onLog("Stopped recording; sending AUDIO_STOP")
                out.write("AUDIO_STOP\n".toByteArray(Charsets.US_ASCII))
                out.flush()
            } catch (e: Exception) {
                onLog("Audio streaming error: ${e.message}")
            } finally {
                isStreaming.set(false)
            }
        }
    }

    fun stopAudioStream() {
        isStreaming.set(false)
        audioJob?.cancel()
    }

    // play wav bytes using AudioTrack
    private fun playWavFromBytes(bytes: ByteArray, onLog: (String)->Unit) {
        launch {
            try {
                // parse WAV header minimally to extract sample rate and channels and data offset
                val stream = ByteArrayInputStream(bytes)
                val dis = DataInputStream(stream)
                val riff = ByteArray(4); dis.readFully(riff) // "RIFF"
                val _size = Integer.reverseBytes(dis.readInt())
                val wave = ByteArray(4); dis.readFully(wave) // "WAVE"
                // read chunks until "fmt " and "data"
                var fmtSampleRate = 16000
                var fmtChannels = 1
                var fmtBits = 16
                var dataBytes: ByteArray? = null
                while (dis.available() > 0) {
                    val chunkId = ByteArray(4); dis.readFully(chunkId)
                    val chunkSize = Integer.reverseBytes(dis.readInt())
                    val id = String(chunkId, Charsets.US_ASCII)
                    val chunkData = ByteArray(chunkSize)
                    dis.readFully(chunkData)
                    if (id == "fmt ") {
                        val ba = ByteBuffer.wrap(chunkData).order(ByteOrder.LITTLE_ENDIAN)
                        val audioFormat = ba.short.toInt() // 1 = PCM
                        val channels = ba.short.toInt()
                        fmtChannels = channels
                        fmtSampleRate = ba.int
                        fmtBits = ba.short.toInt() // actually next fields; safe fallback
                    } else if (id == "data") {
                        dataBytes = chunkData
                        break
                    }
                }
                if (dataBytes == null) {
                    onLog("No data chunk in WAV")
                    return@launch
                }
                val channelConfig = if (fmtChannels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
                val audioFormat = if (fmtBits == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
                val minBuf = AudioTrack.getMinBufferSize(fmtSampleRate, channelConfig, audioFormat)
                val at = AudioTrack(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                    AudioFormat.Builder().setSampleRate(fmtSampleRate).setEncoding(audioFormat).setChannelMask(channelConfig).build(),
                    maxOf(minBuf, dataBytes.size),
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                at.play()
                var offset = 0
                while (offset < dataBytes.size) {
                    val toWrite = minOf(2048, dataBytes.size - offset)
                    at.write(dataBytes, offset, toWrite)
                    offset += toWrite
                }
                at.stop()
                at.release()
                onLog("Playback finished")
            } catch (e: Exception) {
                onLog("Playback error: ${e.message}")
            }
        }
    }
}
