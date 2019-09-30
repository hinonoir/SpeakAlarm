package com.example.speakalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmStopBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        val serviceIntent = Intent(context, ForegroundService::class.java)
        serviceIntent.putExtra("alarm", "off")
        context.startService(serviceIntent)
    }
}
