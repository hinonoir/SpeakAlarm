package com.hino.speakalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/*
* アラーム起動時にブロードキャストを受け取るレシーバー。
* Android10はForegroundServiceを、Android9以下はAlarmBootActivityを呼び出す。
*/
class AlarmBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        val getAlarmResult = intent.getStringExtra("alarm") // アラームのON取得
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

        // API 29以上はForeground Serviceで通知を出す。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceIntent = Intent(context, ForegroundService::class.java)
            serviceIntent.putExtra("alarm", getAlarmResult)
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
            context.startForegroundService(serviceIntent)
        } else {
            // API 28以下はAlarmBootActivityを呼び出す
            val activityIntent = Intent(context, AlarmBootActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activityIntent.putExtra("alarm", getAlarmResult)
            activityIntent.putExtra("repeat", getRepeatResult)
            activityIntent.putExtra("vibration", getVibrationResult)
            activityIntent.putExtra("dayOfWeek", getDayOfWeekResult)
            activityIntent.putExtra("hour", getHourResult)
            activityIntent.putExtra("minute", getMinuteResult)
            activityIntent.putExtra("requestCode", getRequestCodeResult)
            activityIntent.putExtra("musicVol", getMusicVolResult)
            activityIntent.putExtra("voiceVol", getVoiceVolResult)
            activityIntent.putExtra("speedVol", getSpeedVolResult)
            activityIntent.putExtra("speakText", getSpeakTextResult)
            activityIntent.putExtra("labelText", getLabelTextResult)
            activityIntent.putExtra("autoSnoozeCount", getAutoSnoozeCount)
            context.startActivity(activityIntent)
        }
    }
}
