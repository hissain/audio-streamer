# audio-streamer
This is a simple demo app for experimenting server client communication with text and audio data using WebSocket


## Server Part

Here’s a **minimal WebSocket server** in **Python** using `websockets` and `asyncio` that sends:

* 🔊 **dummy PCM audio data** (16-bit mono, 44.1 kHz)
* 💬 **text messages**
* at **random intervals**

---

### ✅ 1. **Install required Python packages**:

```bash
pip install websockets numpy
```

---

### ✅ 2. **Python WebSocket server (`server.py`)**:

```python
import asyncio
import websockets
import numpy as np
import random
import time

# Configuration
PORT = 8765
SAMPLE_RATE = 44100  # Hz
DURATION = 0.2       # seconds per audio chunk
TEXT_MESSAGES = ["Hello", "Streaming audio...", "Testing WebSocket", "Dummy message"]

def generate_dummy_audio():
    """Generate a sine wave as dummy PCM 16-bit mono audio."""
    frequency = random.choice([440, 660, 880])  # Hz
    t = np.linspace(0, DURATION, int(SAMPLE_RATE * DURATION), False)
    sine_wave = 0.5 * np.sin(2 * np.pi * frequency * t)
    audio = (sine_wave * 32767).astype(np.int16)
    return audio.tobytes()

async def stream(websocket, path):
    print(f"Client connected: {websocket.remote_address}")
    try:
        while True:
            # Randomly decide to send text or audio
            if random.random() < 0.3:
                text = random.choice(TEXT_MESSAGES)
                await websocket.send(text)
                print(f"Sent text: {text}")
            else:
                audio_chunk = generate_dummy_audio()
                await websocket.send(audio_chunk)
                print(f"Sent audio: {len(audio_chunk)} bytes")
            await asyncio.sleep(random.uniform(0.5, 2))  # Random delay
    except websockets.exceptions.ConnectionClosed:
        print("Client disconnected")

async def main():
    async with websockets.serve(stream, "0.0.0.0", PORT):
        print(f"WebSocket server running on ws://localhost:{PORT}")
        await asyncio.Future()  # Run forever

if __name__ == "__main__":
    asyncio.run(main())
```

---

### ✅ 3. **Run the server locally**:

```bash
python server.py
```

> Your Android client should connect to `ws://<your-PC-local-IP>:8765`
> (Replace `<your-PC-local-IP>` with actual IP if running on separate device.)

---

## Client Part (Android Class)

Here's how to **play real-time audio data** (e.g. PCM 16-bit) using `AudioTrack` from the received `ByteArray` in Kotlin:

---

### ✅ 1. **Assumptions**:

* Audio format: **PCM 16-bit**, **mono**, **44.1 kHz** sample rate.
* Byte data sent over WebSocket is **raw PCM** (not encoded like MP3/AAC).
* Audio data is streamed continuously.

---

### ✅ 2. **Updated `AudioWebSocketClient` with `AudioTrack` playback**:

```kotlin
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
```

---

### ✅ 3. **Usage in Activity or ViewModel**:

```kotlin
val client = AudioWebSocketClient()
client.connect("ws://<your-server-ip>:<port>/stream")
```

---

### ⚠️ Notes:

* Make sure server sends audio in exact format: **PCM 16-bit LE, mono, 44100 Hz**.
* This will not work for encoded formats like MP3/AAC without decoding.
* Use background thread if you want to buffer or process audio more robustly.
