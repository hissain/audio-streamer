package com.hissain.android.demo.hearingaid

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.IBinder
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hissain.android.demo.hearingaid.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var service: SocketService? = null
    private var bound = false
    private val micOptions = listOf("MIC","CAMCORDER","VOICE_RECOGNITION","VOICE_COMMUNICATION","DEFAULT")

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
        // Handle result if needed
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as SocketService.LocalBinder).getService()
            bound = true
            log("Service connected")
            binding.btnConnect.isEnabled = true // Enable once ready
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            log("Service disconnected")
            binding.btnConnect.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Disable connect until bound
        binding.btnConnect.isEnabled = false

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }

        val it = Intent(this, SocketService::class.java)
        startService(it)
        bindService(it, conn, Context.BIND_AUTO_CREATE)

        binding.btnConnect.setOnClickListener {
            val sp = binding.etServer.text.toString().trim()
            val parts = sp.split(":")
            if (parts.size != 2) { toast("server must be host:port"); return@setOnClickListener }
            val host = parts[0]
            val port = parts[1].toInt()
            service?.connect(host, port, ::log) ?: toast("service not ready")
        }
        binding.btnDisconnect.setOnClickListener {
            service?.disconnect(); log("Requested disconnect")
        }
        binding.btnSendText.setOnClickListener {
            val q = binding.etQuery.text.toString()
            if (q.isBlank()) { toast("Empty"); return@setOnClickListener }
            service?.sendTextQuery(q, ::log)
        }
        binding.spinnerMic.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, micOptions)
        binding.btnStartAudio.setOnClickListener {
            val sel = binding.spinnerMic.selectedItem as String
            val src = when(sel) {
                "MIC" -> MediaRecorder.AudioSource.MIC
                "CAMCORDER" -> MediaRecorder.AudioSource.CAMCORDER
                "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
                else -> MediaRecorder.AudioSource.DEFAULT
            }
            service?.startAudioStream(16000, src, ::log)
        }
        binding.btnStopAudio.setOnClickListener {
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
            binding.tvLog.append("\n$s")
            val scrollAmount = binding.tvLog.layout?.getLineTop(binding.tvLog.lineCount) ?: 0
            if (scrollAmount > binding.tvLog.height) binding.tvLog.scrollTo(0, scrollAmount - binding.tvLog.height)
        }
    }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
}

