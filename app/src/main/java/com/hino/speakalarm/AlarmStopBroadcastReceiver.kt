package com.hino.speakalarm

import android.annotation.TargetApi
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build


class AlarmStopBroadcastReceiver : BroadcastReceiver() {
    @TargetApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {

        val getRepeatResult = intent.getStringExtra("repeat") // 再設定のON取得
        val getVibrationResult = intent.getStringExtra("vibration")
        val getDayOfWeekResult = intent.getStringExtra("dayOfWeek") // 曜日取得
        val getHourResult = intent.getStringExtra("hour") // 時間取得
        val getMinuteResult = intent.getStringExtra("minute") // 分取得
        val getRequestCodeResult = intent.getStringExtra("requestCode") // リクエストコード取得
        val getMusicVolResult = intent.getStringExtra("musicVol") // アラームの音量
        val getVoiceVolResult = intent.getStringExtra("voiceVol") // 声の音量
        val getSpeedVolResult = intent.getStringExtra("speedVol") // 読み上げるスピード
        val getSpeakTextResult = intent.getStringExtra("speakText") // 読み上げるテキスト
        val getLabelTextResult = intent.getStringExtra("labelText") // アラームのラベル
        val getAutoSnoozeCount = intent.getStringExtra("autoSnoozeCount") // 自動スヌーズのカウント

        // SpeakTextServiceを開始する
        val serviceIntent = Intent(context, SpeakTextService::class.java)
        serviceIntent.putExtra("repeat", getRepeatResult)
        serviceIntent.putExtra("vibration", getVibrationResult)
        serviceIntent.putExtra("dayOfWeek", getDayOfWeekResult)
        serviceIntent.putExtra("hour", getHourResult)
        serviceIntent.putExtra("minute", getMinuteResult)
        serviceIntent.putExtra("requestCode", getRequestCodeResult)
        serviceIntent.putExtra("musicVol", getMusicVolResult)
        serviceIntent.putExtra("voiceVol", getVoiceVolResult)
        serviceIntent.putExtra("speedVol", getSpeedVolResult)
        serviceIntent.putExtra("speakText", getSpeakTextResult)
        serviceIntent.putExtra("labelText", getLabelTextResult)
        serviceIntent.putExtra("autoSnoozeCount", getAutoSnoozeCount)
        context.startService(serviceIntent)
    }
}
