package com.hino.speakalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import io.realm.Realm
import io.realm.kotlin.where
import java.util.*

/*
* ForegroundServiceからReceiverを介して受け取ったデータを元に、
* テキストの読み上げ、アラームの再設定（繰り返しあり）、アラームのOFF（1回のみ）を行うクラス
*/
class SpeakTextService : Service(), TextToSpeech.OnInitListener {
    private var preMusicVol: Int? = null // 端末の元の音量設定(アラーム音: メディアの音量)
    private var preVoiceVol: Int? = null // 端末の元の音量設定(テキスト読み上げ: 着信音の音量)
    private lateinit var am: AudioManager// AudioManager
    private var tts: TextToSpeech? = null // TextToSpeech
    private lateinit var alarmUri: Uri // アラームを鳴らすUri
    private lateinit var rm: Ringtone // RingtoneManager
    private var setVibration: Boolean? = false // バイブレーションのON・OFF
    private lateinit var calendar: Calendar // カレンダー
    private var setRepeat: String? = null // 繰り返しのON・OFF
    private var setDayOfWeek: Int? = null // 起動したアラームの曜日
    private var setHour: Int? = null // 起動したアラームの時間
    private var setMinute: Int? = null // 起動したアラームの分
    private var setRequestCode: Int? = null // 起動したアラームのrequestCode
    private var setMusicVol: Int? = null // アラームの音量
    private var setVoiceVol: Int? = null // 声の音量
    private var setSpeedVol: Float? = null // 読み上げるスピード
    private var setSpeakText: String? = null // 読み上げるテキスト
    private var setLabelText: String? = null // アラームのラベル
    private var autoSnoozeCount: Int = 0 // 自動でスヌーズしたカウント（5回目でストップ）
    private lateinit var realm: Realm // Realm

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // TextToSpeechの初期化
        tts = TextToSpeech(this, this)
        tts!!.setPitch(0.9F)
        // アラームを鳴らすUri
        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        // RingtoneManager
        rm = RingtoneManager.getRingtone(this, alarmUri)
        // オーディオマネージャー取得（初期化）
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 現在の端末の音量設定を格納(onDestroy()で元に戻す)
        getPreVolumeConfig()
        // Realmのインスタンスを取得
        realm = Realm.getDefaultInstance()
        // ForegroundServiceを終了させる
        val foreIntent = Intent(this, ForegroundService::class.java)
        stopService(foreIntent)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val foreIntent = Intent(this, ForegroundService::class.java)
        stopService(foreIntent)

        // アラームのデータを保存する
        alarmDataGet(intent)
        // 設定したアラームの音量
        musicVolumeConfig(setMusicVol!!)
        // 設定した声の音量
        voiceVolumeConfig(setVoiceVol!!)
        // TextToSpeechの初期化と設定
        speedConfig(setSpeedVol!!.toInt())

        if (intent.getStringExtra("snooze")?.toString() == "on") {
            // スヌーズをしたときの処理
            setSnoozeAlarmManager()
            Toast.makeText(this, "5分後にアラームが鳴ります", Toast.LENGTH_SHORT).show()
        } else {
            // ストップしたときの処理
            // 繰り返す設定をしているか判定
            if (setRepeat == "on") {
                setRepeatAlarmManager(this) // アラームを7日後に再設定
            } else if (setRepeat == "off") {
                // 1回のみのアラームはDBの「alarm」を"off"にする
                val speakAlarmDb = realm.where<SpeakAlarm>()
                    .equalTo("requestCode", setRequestCode).findFirst()
                realm.executeTransaction {
                    speakAlarmDb?.alarm = "off"
                }
            }
        }

        Handler().postDelayed({
            speakOut(setSpeakText) // テキスト読み上げ
        }, 200)

        setTtsListener() // テキストを読み上げたらサービスを終了
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        tts!!.shutdown() // ttsのリソースを解放
        realm.close()
        preVolumeSet() // 元の音量設定に戻す
    }

    // アラーム音の音量の設定
    private fun musicVolumeConfig(volume: Int) {
        // 音量設定
        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, volume, 0)
    }

    // テキストを読み上げる音量の設定
    private fun voiceVolumeConfig(volume: Int) {
        // 音量設定
        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    // TextToSpeechの初期化判定
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.JAPANESE)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
//                Toast.makeText(this, "サポートされていない、あるいは無効な文字が含まれています。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // テキストの読み上げの始まりと終わりを取得するメソッド
    private fun setTtsListener() {
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onError(utteranceId: String) {
            }

            override fun onStart(utteranceId: String) {
            }

            override fun onDone(utteranceId: String) {
                stopSelf()
            }
        })
    }

    // テキストを読み上げるメソッド
    private fun speakOut(text: String?) {
        when {
            // API21以上
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP -> {
                tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
            }
            // API20以下
            else -> {
                // tts.speak(text, TextToSpeech.QUEUE_FLUSH, null) に
                // KEY_PARAM_UTTERANCE_ID を HashMap で設定
                val map = HashMap<String, String>()
                map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "messageID"
                tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, map)
            }
        }
    }

    // 現在の端末の音量設定を格納(onDestroy()・onPause()・onStop()で元に戻す)
    private fun getPreVolumeConfig() {
        preMusicVol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) // アラーム音
        preVoiceVol = am.getStreamVolume(AudioManager.STREAM_MUSIC) // テキスト読み上げ音
    }

    // アラーム音・テキスト読み上げ音の音量設定を戻す
    private fun preVolumeSet() {
        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, preMusicVol!!, 0)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, preVoiceVol!!, 0)
    }

    // テキストを読み上げる速さの設定
    private fun speedConfig(speed: Int) {
        val speedInt: Float = speed.toFloat()
        val result = speedInt / 6
        tts?.setSpeechRate(result)
    }

    // アラームの設定を取得する
    private fun alarmDataGet(intent: Intent) {
        calendar = Calendar.getInstance()
        setRepeat = intent.getStringExtra("repeat")
        setVibration = intent.getStringExtra("vibration")?.toBoolean()
        setRequestCode = intent.getStringExtra("requestCode")!!.toInt()
        setDayOfWeek = intent.getStringExtra("dayOfWeek")?.toInt()
        setHour = intent.getStringExtra("hour")?.toInt()
        setMinute = intent.getStringExtra("minute")?.toInt()
        setMusicVol = intent.getStringExtra("musicVol")!!.toInt()
        setVoiceVol = intent.getStringExtra("voiceVol")!!.toInt()
        setSpeedVol = intent.getStringExtra("speedVol")!!.toFloat()
        setSpeakText = intent.getStringExtra("speakText")
        setLabelText = intent.getStringExtra("labelText")
        autoSnoozeCount = intent.getStringExtra("autoSnoozeCount")!!.toInt()
    }

    // アラームを1週間後に再設定
    private fun setRepeatAlarmManager(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmBroadcastReceiver::class.java)
        // アラームの設定をレシーバーに送る(アラーム再設定に利用)
        intent.putExtra("alarm", "on")
        intent.putExtra("repeat", setRepeat)
        intent.putExtra("vibration", setVibration.toString())
        intent.putExtra("dayOfWeek", setDayOfWeek!!.toString())
        intent.putExtra("hour", setHour!!.toString())
        intent.putExtra("minute", setMinute!!.toString())
        intent.putExtra("requestCode", setRequestCode!!.toString())
        intent.putExtra("musicVol", setMusicVol!!.toString())
        intent.putExtra("voiceVol", setVoiceVol!!.toString())
        intent.putExtra("speedVol", setSpeedVol!!.toString())
        intent.putExtra("speakText", setSpeakText!!)
        intent.putExtra("labelText", setLabelText)
        intent.putExtra("autoSnoozeCount", "0")
        val pending =
            PendingIntent.getBroadcast(
                context, setRequestCode!!, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

        calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, setHour!!)
        calendar.set(Calendar.MINUTE, setMinute!!)
        calendar.set(Calendar.SECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, 7) // 指定した追加時間後にアラームが鳴る

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

    // スヌーズ設定メソッド
    private fun setSnoozeAlarmManager() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        // アラームの設定をレシーバーに送る(アラーム再設定に利用)
        intent.putExtra("alarm", "on")
        intent.putExtra("repeat", setRepeat)
        intent.putExtra("vibration", setVibration.toString())
        intent.putExtra("dayOfWeek", setDayOfWeek?.toString())
        intent.putExtra("hour", setHour?.toString())
        intent.putExtra("minute", setMinute?.toString())
        intent.putExtra("requestCode", setRequestCode!!.toString())
        intent.putExtra("musicVol", setMusicVol!!.toString())
        intent.putExtra("voiceVol", setVoiceVol!!.toString())
        intent.putExtra("speedVol", setSpeedVol!!.toString())
        intent.putExtra("speakText", setSpeakText!!)
        intent.putExtra("labelText", setLabelText)
        intent.putExtra("autoSnoozeCount", autoSnoozeCount.toString())

        val pending =
            PendingIntent.getBroadcast(
                this, setRequestCode!!, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )

        calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.MINUTE, 5) // 指定した追加時間後にアラームが鳴る

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
