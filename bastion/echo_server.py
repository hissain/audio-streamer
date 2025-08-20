# echo_server.py
import socket
import threading

HOST = "0.0.0.0"
PORT = 9000

def handle_client(conn, addr):
    print(f"Connected by {addr}")
    try:
        while True:
            data = conn.recv(1024)
            if not data:
                break
            print(f"Received: {data.decode().strip()}")
            conn.sendall(data)  # Echo back
    finally:
        conn.close()
        print(f"Connection closed {addr}")

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind((HOST, PORT))
    server.listen(5)
    print(f"Echo server listening on {HOST}:{PORT}")
    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle_client, args=(conn, addr)).start()

if __name__ == "__main__":
    main()