package com.litegem.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val startBtn = findViewById<Button>(R.id.startBtn)
        val stopBtn = findViewById<Button>(R.id.stopBtn)

        startBtn.setOnClickListener {
            startForegroundService(Intent(this, InferenceService::class.java).apply {
                action = InferenceService.ACTION_START
            })
            statusText.text = "liteGem running on localhost:11434"
        }

        stopBtn.setOnClickListener {
            startService(Intent(this, InferenceService::class.java).apply {
                action = InferenceService.ACTION_STOP
            })
            statusText.text = "liteGem stopped"
        }

        statusText.text = "liteGem ready\nAPI: localhost:11434"
    }
}
