# dummy_internal_server.py
import socket

HOST = "127.0.0.1"
PORT = 12345
s = socket.socket()
s.bind((HOST, PORT))
s.listen(1)
print("Dummy internal server listening...")
conn, addr = s.accept()
print("Connection from", addr)
while True:
    data = conn.recv(1024)
    if not data:
        break
    conn.sendall(b"Internal> " + data)
conn.close()