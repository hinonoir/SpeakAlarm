package com.hino.speakalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.util.*

class onOffService : Service() {
    private var vibrationCb: Boolean = false // バイブレーションのON・OFF
    private var setHour: Int? = null // セットする時間
    private var setMinute: Int? = null // セットする分
    private var musicVol: Int? = null // アラームの音量
    private var voiceVol: Int? = null // 声の音量
    private var speedVol: Float? = null // 読み上げるスピード
    private var speakText: String? = null // 読み上げるテキスト
    private var labelText: String? = null // アラームのラベル

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val rcMon: Int = intent!!.getIntExtra("rcMon", 0)
        val rcTue: Int = intent.getIntExtra("rcTue", 0)
        val rcWed: Int = intent.getIntExtra("rcWed", 0)
        val rcThu: Int = intent.getIntExtra("rcThu", 0)
        val rcFri: Int = intent.getIntExtra("rcFri", 0)
        val rcSat: Int = intent.getIntExtra("rcSat", 0)
        val rcSun: Int = intent.getIntExtra("rcSun", 0)
        val rcOnce: Int = intent.getIntExtra("rcOnce", 0)

        when {
            intent.getStringExtra("alarm") == "on" -> {
                // アラームに必要なデータをCustomRecyclerViewAdapterから取得する
                vibrationCb = intent.getBooleanExtra("vibration", false)
                setHour = intent.getIntExtra("hour", 0)
                setMinute = intent.getIntExtra("minute", 0)
                musicVol = intent.getIntExtra("musicVol", 0)
                voiceVol = intent.getIntExtra("voiceVol", 0)
                speedVol = intent.getFloatExtra("speedVol", 0F)
                speakText = intent.getStringExtra("speakText")
                labelText = intent.getStringExtra("labelText")

                if (rcMon != 0) setRepeatAlarmManager(2, rcMon)
                if (rcTue != 0) setRepeatAlarmManager(3, rcTue)
                if (rcWed != 0) setRepeatAlarmManager(4, rcWed)
                if (rcThu != 0) setRepeatAlarmManager(5, rcThu)
                if (rcFri != 0) setRepeatAlarmManager(6, rcFri)
                if (rcSat != 0) setRepeatAlarmManager(0, rcSat)
                if (rcSun != 0) setRepeatAlarmManager(1, rcSun)
                if (rcOnce != 0) setOnceAlarmManager(rcOnce) // アラーム1回のみ
            }
            // アラームのチェックが外れたら、アラームをキャンセルする
            intent.getStringExtra("alarm") == "off" -> {
                if (rcMon != 0) cancelAlarmManager(rcMon)
                if (rcTue != 0) cancelAlarmManager(rcTue)
                if (rcWed != 0) cancelAlarmManager(rcWed)
                if (rcThu != 0) cancelAlarmManager(rcThu)
                if (rcFri != 0) cancelAlarmManager(rcFri)
                if (rcSat != 0) cancelAlarmManager(rcSat)
                if (rcSun != 0) cancelAlarmManager(rcSun)
                if (rcOnce != 0) cancelAlarmManager(rcOnce)
            }
        }

        stopSelf()
        return START_STICKY
    }

    // アラームセットのメソッド（繰り返しあり）
    private fun setRepeatAlarmManager(dayOfWeek: Int, requestCode: Int) {

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        // アラームの設定をレシーバーに送る(アラーム再設定に利用)
        intent.putExtra("alarm", "on")
        intent.putExtra("repeat", "on")
        intent.putExtra("vibration", vibrationCb.toString())
        intent.putExtra("dayOfWeek", dayOfWeek.toString())
        intent.putExtra("hour", setHour!!.toString())
        intent.putExtra("minute", setMinute!!.toString())
        intent.putExtra("requestCode", requestCode.toString())
        intent.putExtra("musicVol", musicVol!!.toString())
        intent.putExtra("voiceVol", voiceVol!!.toString())
        intent.putExtra("speedVol", speedVol!!.toString())
        intent.putExtra("speakText", speakText!!)
        intent.putExtra("labelText", labelText)
        intent.putExtra("autoSnoozeCount", "0")
        val pending = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, setHour!!)
        calendar.set(Calendar.MINUTE, setMinute!!)
        calendar.set(Calendar.SECOND, 0)

        // 現在の曜日より過去の曜日なら、次の週に設定する
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
        }

        when {
            // API21以上
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val info = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
                am.setAlarmClock(info, pending)
            }
            // API19以上
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                am.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
            // API18以下
            else -> {
                am.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
        }
    }

    // アラームセットのメソッド（1回のみ）
    private fun setOnceAlarmManager(requestCode: Int) {

        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        // アラームの設定をレシーバーに送る
        intent.putExtra("alarm", "on")
        intent.putExtra("repeat", "off")
        intent.putExtra("vibration", vibrationCb.toString())
        intent.putExtra("hour", setHour!!.toString())
        intent.putExtra("minute", setMinute!!.toString())
        intent.putExtra("requestCode", requestCode.toString()) // requestCodeをレシーバーに送る
        intent.putExtra("musicVol", musicVol!!.toString())
        intent.putExtra("voiceVol", voiceVol!!.toString())
        intent.putExtra("speedVol", speedVol!!.toString())
        intent.putExtra("speakText", speakText!!)
        intent.putExtra("labelText", labelText)
        intent.putExtra("autoSnoozeCount", "0")
        val pending = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 時刻セット
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, setHour!!)
        calendar.set(Calendar.MINUTE, setMinute!!)
        calendar.set(Calendar.SECOND, 0)

        // セットした時間が現在より前だった場合、翌日に設定
        if (calendar.timeInMillis < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        when {
            // API21以上
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                val info = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
                am.setAlarmClock(info, pending)
            }
            // API19以上
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT -> {
                am.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
            // API18以下
            else -> {
                am.set(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
        }
    }

    // アラームキャンセルのメソッド
    private fun cancelAlarmManager(requestCode: Int) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, requestCode, intent, 0)
        am.cancel(pending)
    }
}
