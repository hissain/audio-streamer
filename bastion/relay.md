
I’ll give you **all minimal components** (Python servers + Android client app) and then a **step-by-step testing guide** so you can go from simplest echo test → full SSH relay → real internal server communication.

---

# 🔹 Components

## 1. Python Echo Server (test basic Android ↔ PC socket)

```python
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
```

---

## 2. Python Relay Server (PC ↔ Bastion ↔ Internal)

```python
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
```

---

## 3. Minimal Android Client (Kotlin)

### `MainActivity.kt`

```kotlin
package com.example.simplerelayclient

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var socket: Socket
    private lateinit var writer: PrintWriter
    private lateinit var reader: BufferedReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.editText)
        val sendBtn = findViewById<Button>(R.id.sendBtn)
        val output = findViewById<TextView>(R.id.textView)

        thread {
            try {
                socket = Socket("YOUR_PC_IP", 9000) // Replace with PC's LAN IP
                writer = PrintWriter(socket.getOutputStream(), true)
                reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (true) {
                    val line = reader.readLine() ?: break
                    runOnUiThread { output.append("\nServer: $line") }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { output.append("\nError: ${e.message}") }
            }
        }

        sendBtn.setOnClickListener {
            val msg = input.text.toString()
            thread { writer.println(msg) }
        }
    }
}
```

### `activity_main.xml`

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <EditText
        android:id="@+id/editText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="Type message"/>

    <Button
        android:id="@+id/sendBtn"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Send"/>

    <TextView
        android:id="@+id/textView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Output"/>
</LinearLayout>
```

---

# 🔹 Step-by-Step Testing Guide

### ✅ Stage 1: Verify Android ↔ PC Socket (Echo Test)

1. Run `python echo_server.py` on your PC → It will listen on `0.0.0.0:9000`.
2. Get your PC’s **LAN IP** (`ipconfig` on Windows, `ifconfig`/`ip a` on Linux).
3. In `MainActivity.kt`, set:

   ```kotlin
   socket = Socket("192.168.X.Y", 9000)
   ```

   (replace with your PC’s IP).
4. Run the Android app (same WiFi as PC).
5. Type text → Send → You should see it echoed back.

---

### ✅ Stage 2: Test PC Relay with Dummy Internal Server

Before connecting real internal server, simulate one locally.

Run this **dummy internal echo server** on your bastion-reachable host:

```python
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
```

Then start `relay_server.py` on your PC.
Now when Android sends → message goes:

**Android → PC Relay → Bastion → Dummy internal server → back**

---

### ✅ Stage 3: Connect to Real Internal Server

* Replace `INTERNAL_HOST` and `INTERNAL_PORT` in `relay_server.py` with your actual internal server.
* Ensure your **SSH private key** path and user are correct.
* Run `relay_server.py`.
* Connect Android → PC → Bastion → Internal server.

---

# 🔹 Summary

* **Stage 1** proves Android socket works.
* **Stage 2** proves relay logic works with a dummy server.
* **Stage 3** connects real system.

---
---


Here’s a **shell command checklist** for running everything step by step. Follow this order carefully.

---

# 🔹 Stage 1: Test Android ↔ PC Echo

### On your **PC**:

```bash
# 1. Run echo server
python3 echo_server.py
```

Output should show:

```
Echo server listening on 0.0.0.0:9000
```

### On your **Android client**:

* In `MainActivity.kt`, replace:

  ```kotlin
  socket = Socket("YOUR_PC_IP", 9000)
  ```

  with your PC’s LAN IP, e.g. `192.168.1.50`.
* Run app → Type a message → Press **Send**.
* In PC terminal you should see:

  ```
  Connected by ('192.168.1.101', 54321)
  Received: hello
  ```
* On Android app, you should see:

  ```
  Server: hello
  ```

✅ If this works, sockets are fine.

---

# 🔹 Stage 2: Test PC Relay with Dummy Internal Server

### On your **internal/test machine** (or same PC if testing locally):

```bash
# Run dummy internal server
python3 dummy_internal_server.py
```

Output:

```
Dummy internal server listening...
```

### On your **PC** (relay machine):

```bash
# Run relay server (this will connect to bastion & forward traffic)
python3 relay_server.py
```

Output:

```
Relay listening on 0.0.0.0:9000
```

### On your **Android app**:

* Keep `socket = Socket("YOUR_PC_IP", 9000)` same as before.
* Run app → Send message.
* On **internal server console**, you should see:

  ```
  Connection from 127.0.0.1
  ```
* On Android, you should see response:

  ```
  Server: Internal> your_message
  ```

✅ This confirms relay works.

---

# 🔹 Stage 3: Test With Real Internal Server (via Bastion)

### On your **PC**:

1. Edit `relay_server.py`:

   ```python
   BASTION_HOST = "IP1"
   BASTION_PORT = 22
   BASTION_USER = "youruser"
   PRIVATE_KEY = "/path/to/private/key"
   INTERNAL_HOST = "IP2"
   INTERNAL_PORT = 12345
   ```

   Replace with real bastion/internal details.

2. Run:

   ```bash
   python3 relay_server.py
   ```

   Output:

   ```
   Relay listening on 0.0.0.0:9000
   ```

### On your **Android app**:

* Keep `socket = Socket("YOUR_PC_IP", 9000)`.
* Send text from Android.
* If everything is correct, you should get responses from the **real internal server**.

---

# 🔹 Quick Checklist Summary

```bash
# Stage 1
python3 echo_server.py      # Run on PC
# Test with Android app

# Stage 2
python3 dummy_internal_server.py   # Run on internal/test machine
python3 relay_server.py            # Run on PC
# Test with Android app

# Stage 3
# Update relay_server.py with real bastion & internal details
python3 relay_server.py
# Test with Android app
```

---
