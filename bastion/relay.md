I’ll give you **all minimal components** (Python servers + Android client app) and then a **step-by-step testing guide** so you can go from simplest echo test → full SSH relay → real internal server communication.

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
