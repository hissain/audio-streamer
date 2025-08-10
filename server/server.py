# server.py
import socket
import threading
import struct
import time
import wave
import os

HOST = '0.0.0.0'
PORT = 8888
BUFFER_SIZE = 4096
SAVE_DIR = 'received_audio'
os.makedirs(SAVE_DIR, exist_ok=True)

def recv_line(conn):
    # Read bytes until newline
    line = bytearray()
    while True:
        ch = conn.recv(1)
        if not ch:
            return None
        if ch == b'\n':
            break
        line.extend(ch)
    return line.decode('ascii')

def recv_exact(conn, n):
    data = bytearray()
    while len(data) < n:
        chunk = conn.recv(n - len(data))
        if not chunk:
            return None
        data.extend(chunk)
    return bytes(data)

def handle_client(conn, addr):
    print(f'Client connected: {addr}')
    try:
        while True:
            header = recv_line(conn)
            if header is None:
                print('Client closed connection')
                break
            header = header.strip()
            if header == 'TEXT':
                # read 4-byte length
                raw = recv_exact(conn, 4)
                if raw is None:
                    break
                L = struct.unpack('>I', raw)[0]
                data = recv_exact(conn, L)
                if data is None:
                    break
                query = data.decode('utf-8', errors='replace')
                print(f'[TEXT] from {addr}: {query!r}')
                # simulate 1 second processing
                time.sleep(1.0)
                # Dummy manipulation: reverse the text (example)
                resp_text = f"server_echo: {query[::-1]}"
                resp_bytes = resp_text.encode('utf-8')
                # send response
                conn.sendall(b'TEXT_RESP\n')
                conn.sendall(struct.pack('>I', len(resp_bytes)))
                conn.sendall(resp_bytes)
            elif header == 'AUDIO_START':
                # read metadata lines until blank line
                meta = {}
                while True:
                    line = recv_line(conn)
                    if line is None:
                        return
                    line = line.strip()
                    if line == '':
                        break
                    if ':' in line:
                        k, v = line.split(':', 1)
                        meta[k.strip()] = v.strip()
                print(f'[AUDIO] meta: {meta}')
                # collect PCM chunks until AUDIO_STOP
                pcm_chunks = bytearray()
                while True:
                    # peek next header byte(s) — we must read 4 bytes which if it's header text is wrong.
                    # We'll read next 6 bytes to check whether it's 'AUDIO_STOP' (text line) or chunk length
                    # But easier approach: the client will send either a line starting with ASCII (A) or a 4-byte length.
                    # Here we try to recv 1 byte non-blocking? Simpler: use recv_line with timeout not available.
                    # Instead we rely on the protocol: streaming chunks are always preceded by 4-byte length,
                    # and stop is an ASCII header line which will start with 'A' and followed by '\n'.
                    # So we try to read 1 byte and if it's ASCII digit or not? Safer: read next 1 byte - if it's '\n' keep reading.
                    # Simpler and robust: client will not send newlines between chunks. So first read 4 bytes length.
                    raw = recv_exact(conn, 4)
                    if raw is None:
                        return
                    # We need to detect if raw is ASCII header text instead of length (rare). To detect, try to unpack.
                    # We'll assume it's length. Unpack:
                    N = struct.unpack('>I', raw)[0]
                    # If N looks too large (>100MB) maybe it's not a length — but skip for simplicity.
                    chunk = recv_exact(conn, N)
                    if chunk is None:
                        return
                    pcm_chunks.extend(chunk)
                    # peek into socket for next byte to see if it's 'A' (start of AUDIO_STOP) or next length.
                    # Use recv with MSG_PEEK if available
                    try:
                        peek = conn.recv(10, socket.MSG_PEEK)
                    except Exception:
                        peek = b''
                    if peek.startswith(b'AUDIO_STOP'):
                        # consume the line
                        _ = recv_line(conn)  # read the 'AUDIO_STOP\n' line
                        break
                    # else continue reading chunks
                # save PCM to WAV
                sample_rate = int(meta.get('sample_rate', '16000'))
                channels = int(meta.get('channels', '1'))
                sampwidth = 2  # pcm_s16 -> 2 bytes
                filename = os.path.join(SAVE_DIR, f'from_{addr[0].replace(".", "_")}_{int(time.time())}.wav')
                with wave.open(filename, 'wb') as wf:
                    wf.setnchannels(channels)
                    wf.setsampwidth(sampwidth)
                    wf.setframerate(sample_rate)
                    wf.writeframes(pcm_chunks)
                print(f'[AUDIO] saved to {filename} ({len(pcm_chunks)} bytes)')
                # after saving send back the WAV bytes
                with open(filename, 'rb') as f:
                    wav_bytes = f.read()
                conn.sendall(b'AUDIO_RESP\n')
                conn.sendall(struct.pack('>I', len(wav_bytes)))
                conn.sendall(wav_bytes)
            elif header == '':
                # ignore
                continue
            else:
                print('Unknown header:', header)
                # break or ignore
                break
    except Exception as e:
        print('Client handler error:', e)
    finally:
        try:
            conn.close()
        except:
            pass
        print(f'Client disconnected: {addr}')

def main():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        s.bind((HOST, PORT))
        s.listen(5)
        print(f'Server listening on {HOST}:{PORT}')
        while True:
            conn, addr = s.accept()
            t = threading.Thread(target=handle_client, args=(conn, addr), daemon=True)
            t.start()

if __name__ == '__main__':
    main()

