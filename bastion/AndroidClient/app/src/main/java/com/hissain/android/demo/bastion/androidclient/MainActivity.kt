package com.hissain.android.demo.bastion.androidclient

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
                socket = Socket("192.168.0.103", 9000) // Replace with PC's LAN IP
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