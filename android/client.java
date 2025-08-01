import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import okhttp3.*
import okio.ByteString

class AudioWebSocketClient : WebSocketListener() {

    private lateinit var webSocket: WebSocket
    private val client = OkHttpClient()

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize,
        AudioTrack.MODE_STREAM
    )

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, this)
        client.dispatcher.executorService.shutdown()
        audioTrack.play()
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val audioBytes = bytes.toByteArray()
        audioTrack.write(audioBytes, 0, audioBytes.size)
        Log.d("WebSocket", "Played ${audioBytes.size} bytes")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        audioTrack.stop()
        audioTrack.release()
        webSocket.close(1000, null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        audioTrack.stop()
        audioTrack.release()
        Log.e("WebSocket", "Error: ${t.message}")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        audioTrack.stop()
        audioTrack.release()
        Log.i("WebSocket", "Closed: $reason")
    }
}
