# relay_server.py
import socket
import paramiko
import threading

# Bastion and internal server details
BASTION_HOST = "IP1"
BASTION_PORT = 22
BASTION_USER = "youruser"
PRIVATE_KEY = "/path/to/private/key"  # file path
INTERNAL_HOST = "IP2"
INTERNAL_PORT = 12345  # internal server port

# Relay server (PC)
RELAY_HOST = "0.0.0.0"
RELAY_PORT = 9000

def handle_client(client_sock):
    key = paramiko.RSAKey.from_private_key_file(PRIVATE_KEY)
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(BASTION_HOST, BASTION_PORT, username=BASTION_USER, pkey=key)

    transport = ssh.get_transport()
    chan = transport.open_channel("direct-tcpip",
                                  (INTERNAL_HOST, INTERNAL_PORT),
                                  ("127.0.0.1", 0))

    def forward(src, dst):
        try:
            while True:
                data = src.recv(1024)
                if not data:
                    break
                dst.sendall(data)
        finally:
            src.close()
            dst.close()

    threading.Thread(target=forward, args=(client_sock, chan)).start()
    threading.Thread(target=forward, args=(chan, client_sock)).start()

def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.bind((RELAY_HOST, RELAY_PORT))
    server.listen(5)
    print(f"Relay listening on {RELAY_HOST}:{RELAY_PORT}")
    while True:
        client_sock, addr = server.accept()
        print(f"New connection from {addr}")
        threading.Thread(target=handle_client, args=(client_sock,)).start()

if __name__ == "__main__":
    main()