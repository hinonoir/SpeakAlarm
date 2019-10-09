package com.hino.speakalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.realm.Realm
import io.realm.kotlin.where
import java.util.*

class DirectBootReceiver : BroadcastReceiver() {
    private lateinit var calendar: Calendar // カレンダー
    private var setHour: Int? = null // セットする時間
    private var setMinute: Int? = null // セットする分
    private var musicVol: Int? = null // アラームの音量
    private var voiceVol: Int? = null // 声の音量
    private var speedVol: Float? = null // 読み上げるスピード
    private var vibrationCb: Boolean = false // バイブレーションのON・OFF
    private var speakText: String? = null // 読み上げるテキスト
    private var labelText: String? = null // アラームのラベル
    private lateinit var realm: Realm // Realm

    override fun onReceive(context: Context, intent: Intent) {
        // Realmのインスタンス取得
        realm = Realm.getDefaultInstance()
        // 「alarm」が「on」になってるレコードを取得
        val speakAlarmDb = realm.where<SpeakAlarm>()
            .equalTo("alarm", "on").findAll()
        // DBから各種設定を読み込み、曜日ごとのrequestCodeでアラームを設定する
        for (item in speakAlarmDb) {
            vibrationCb = item.vibration!!
            setHour = item.hour
            setMinute = item.minute
            musicVol = item.musicVol
            voiceVol = item.voiceVol
            speedVol = item.speedVol
            speakText = item.speakText
            labelText = item.labelText

            when {
                // repeatが"on"の場合の処理
                item.repeat == "on" -> {
                    if (item.rcMonday != null) setRepeatAlarmManager(context, 2, item.rcMonday!!)
                    if (item.rcTuesday != null) setRepeatAlarmManager(context, 3, item.rcTuesday!!)
                    if (item.rcWednesday != null) setRepeatAlarmManager(
                        context,
                        4,
                        item.rcWednesday!!
                    )
                    if (item.rcThursday != null) setRepeatAlarmManager(
                        context,
                        5,
                        item.rcThursday!!
                    )
                    if (item.rcFriday != null) setRepeatAlarmManager(context, 6, item.rcFriday!!)
                    if (item.rcSaturday != null) setRepeatAlarmManager(
                        context,
                        0,
                        item.rcSaturday!!
                    )
                    if (item.rcSunday != null) setRepeatAlarmManager(context, 1, item.rcSunday!!)
                }
                // repeatが"off"の場合の処理（アラームが1回のみの場合）
                item.repeat == "off" -> {
                    setOnceAlarmManager(context, item.requestCode)
                }
            }
        }

        realm.close() // realmのリソースを解放
    }

    // アラームセットのメソッド（繰り返しあり）
    private fun setRepeatAlarmManager(context: Context, dayOfWeek: Int, requestCode: Int) {

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmBroadcastReceiver::class.java)
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
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        calendar = Calendar.getInstance()
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
    private fun setOnceAlarmManager(context: Context, requestCode: Int) {

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmBroadcastReceiver::class.java)
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
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 時刻セット
        calendar = Calendar.getInstance()
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
}
