package com.hissain.android.demo.hearingaid

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.IBinder
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private var service: SocketService? = null
    private var bound = false
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val micOptions = listOf(
        "MIC",
        "CAMCORDER",
        "VOICE_RECOGNITION",
        "VOICE_COMMUNICATION",
        "DEFAULT"
    )

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        // no-op; user must accept record permission
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as SocketService.LocalBinder
            service = b.getService()
            bound = true
            log("Service connected")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false; service = null; log("Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        // start and bind service
        val it = Intent(this, SocketService::class.java)
        startService(it)
        bindService(it, conn, Context.BIND_AUTO_CREATE)

        btn_connect.setOnClickListener {
            val sp = et_server.text.toString().trim()
            val parts = sp.split(":")
            if (parts.size != 2) { toast("server must be host:port"); return@setOnClickListener }
            val host = parts[0]; val port = parts[1].toInt()
            service?.connect(host, port, ::log) ?: toast("service not ready")
        }
        btn_disconnect.setOnClickListener {
            service?.disconnect(); log("Requested disconnect")
        }
        btn_send_text.setOnClickListener {
            val q = et_query.text.toString()
            if (q.isBlank()) { toast("Empty"); return@setOnClickListener }
            service?.sendTextQuery(q, ::log)
        }
        // spinner
        spinner_mic.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, micOptions)
        btn_start_audio.setOnClickListener {
            val sel = spinner_mic.selectedItem as String
            val src = when(sel) {
                "MIC" -> MediaRecorder.AudioSource.MIC
                "CAMCORDER" -> MediaRecorder.AudioSource.CAMCORDER
                "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else -> MediaRecorder.AudioSource.DEFAULT
            }
            service?.startAudioStream(16000, src, ::log)
        }
        btn_stop_audio.setOnClickListener {
            service?.stopAudioStream(); log("Requested stop audio")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(conn)
            bound = false
        }
    }

    private fun log(s: String) {
        runOnUiThread {
            tv_log.append("\n$s")
            // scroll to bottom
            val scrollAmount = tv_log.layout?.getLineTop(tv_log.lineCount) ?: 0
            if (scrollAmount > tv_log.height) tv_log.scrollTo(0, scrollAmount - tv_log.height)
        }
    }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}

