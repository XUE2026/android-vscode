package com.vscode.android.core.terminal

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class TermuxBridgeService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    companion object {
        private var instance: TermuxBridgeService? = null

        fun initialize(context: Context): Boolean {
            return try {
                val intent = Intent(context, TermuxBridgeService::class.java)
                context.startForegroundService(intent)
                true
            } catch (e: Exception) {
                android.util.Log.e("TermuxBridge", "Failed to initialize", e)
                false
            }
        }

        fun getInstance(): TermuxBridgeService? = instance
    }
}