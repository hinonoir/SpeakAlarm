package com.example.speakalarm

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

class AlarmSnoozeBroadcastReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.O)
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onReceive(context: Context, intent: Intent) {

        val serviceIntent = Intent(context, ForegroundService::class.java)
        serviceIntent.putExtra("snooze", "on")
        context.startService(serviceIntent)
    }
}
