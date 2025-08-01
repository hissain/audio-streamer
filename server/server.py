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
