package com.example.speakalarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.android.synthetic.main.activity_alarm_boot.*
import java.util.*

class AlarmBootActivity : AppCompatActivity(), TextToSpeech.OnInitListener,
    AlarmDialog.Listener {
    private var preMusicVol: Int? = null // 端末の元の音量設定(アラーム音: メディアの音量)
    private var preVoiceVol: Int? = null // 端末の元の音量設定(テキスト読み上げ: 着信音の音量)
    private lateinit var am: AudioManager// AudioManager
    private var onCreateMark: Boolean? = null // onCreateからの起動かを判定
    private var tts: TextToSpeech? = null // TextToSpeech
    private lateinit var alarmUri: Uri // アラームを鳴らすUri
    private lateinit var rm: Ringtone // RingtoneManager
    private lateinit var vibrator: Vibrator // バイブレーション
    private var setVibration: Boolean = false // バイブレーションのON・OFF
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
    private var snoozeHandler: Handler? = null // Handler（遅延処理）
    private var runSnooze: Runnable? = null // 遅延処理でアラームを自動スヌーズ
    private var autoSnoozeCount: Int = 0 // 自動でスヌーズしたカウント（5回目でストップ）
    private lateinit var realm: Realm // Realm

    // アラームダイアログの「OK」をタップした処理
    override fun alarmStop() {
        rm.stop() // アラーム音停止
        // バイブレーションがONなら止める
        if (setVibration) {
            vibrator.cancel()
        }
        Handler().postDelayed({
            speakOut(setSpeakText) // テキスト読み上げ
        }, 200)

        // 繰り返す設定をしているか判定
        if (setRepeat == "on") {
            setRepeatAlarmManager() // アラームを7日後に再設定
        } else if (setRepeat == "off") {
            // 1回のみのアラームはDBの「alarm」を"off"にする
            val speakAlarmDb = realm.where<SpeakAlarm>()
                .equalTo("requestCode", setRequestCode).findFirst()
            realm.executeTransaction {
                speakAlarmDb?.alarm = "off"
            }
        }
        // アラーム自動停止をキャンセル（1分後のスヌーズ処理をキャンセル）
        snoozeHandler?.removeCallbacks(runSnooze!!)
    }

    // アラームダイアログの「スヌーズ」をタップした処理
    override fun snooze() {
        rm.stop() // アラーム音停止
        // バイブレーションがONなら止める
        if (setVibration) {
            vibrator.cancel()
        }
        Handler().postDelayed({
            speakOut(setSpeakText) // テキスト読み上げ
        }, 200)
        setSnoozeAlarmManager() // スヌーズセット
        // アラーム自動停止をキャンセル（1分後のスヌーズ処理をキャンセル）
        snoozeHandler?.removeCallbacks(runSnooze!!)
        // 4分後に自動でアクティビティを終了
        Handler().postDelayed({
            finish()
        }, 4 * 60 * 1000)
        Toast.makeText(this, "5分後にアラームが鳴ります", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_boot)
        // Realmのインスタンスを取得
        realm = Realm.getDefaultInstance()
        // オーディオマネージャー取得（初期化）
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 現在の端末の音量設定を格納(onDestroy()・onPause()・onStop()で元に戻す)
        getPreVolumeConfig()
        // onCreateから起動したことを確認する
        onCreateMark = true

        // アラームのデータを保存する
        alarmDataGet()
        // ラベル名を表示
        label.text = setLabelText
        // 読み上げるテキストを表示
        speakText.text = setSpeakText
        // OKボタンをタップしてアクティビティを終了する
        okBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                finish()
            }
        }

        // AlarmBroadcastReceiverのExtraの"onReceive"にtrueが指定されてるか確認
        if (intent?.getStringExtra("alarm") == "on") {
            when {
                // Android8.1(API27)以上
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                    val keyguardManager =
                        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    keyguardManager.requestDismissKeyguard(this, null)
                }
                // Android8.0(API26)以上
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
                    val keyguardManager =
                        getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    keyguardManager.requestDismissKeyguard(this, null)
                }
                // Android7.1.1(API25)以下
                else -> {
                    window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
                }
            }

            // 設定したアラームの音量
            musicVolumeConfig(setMusicVol!!)
            // 設定した声の音量
            voiceVolumeConfig(setVoiceVol!!)
            // TextToSpeechの初期化と設定
            tts = TextToSpeech(this, this)
            tts!!.setPitch(0.9F)
            speedConfig(setSpeedVol!!.toInt())

            // 「テキストの内容」をバンドルにセット
            val bundle = Bundle()
            bundle.putString("speakText", setSpeakText!!) // テキスト内容
            if (!setLabelText!!.isBlank()) {
                // ラベルがあればセット
                bundle.putString("labelText", setLabelText)
            }
            // アラームのダイアログを表示
            val dialog = AlarmDialog()
            dialog.arguments = bundle // バンドルをセット
            dialog.show(supportFragmentManager, "alarm_dialog")
            playAlarm()
            // trueならバイブレーションを鳴らす
            if (setVibration) {
                playVibration()
            }

            // 何も操作がなければ1分後に自動的にスヌーズにする（5回まで）
            if (autoSnoozeCount < 5) {
                snoozeHandler = Handler()
                runSnooze = Runnable {
                    autoSnoozeCount++ // カウントアップ
                    snooze() // スヌーズ処理
                    setTtsListener() // テキストを読み終えたらアクティビティを終了
                }
                snoozeHandler?.postDelayed(runSnooze!!, 60000)
            } else {
                val handler = Handler()
                handler.postDelayed({
                    alarmStop()
                }, 60000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // onCreate()から起動した場合は、アラームの音量設定を取得しない
        when (onCreateMark) {
            true -> onCreateMark = false
            false -> {
                // 現在の端末の音量設定を取得
                getPreVolumeConfig()
                if (intent?.getStringExtra("alarm") == "on") {
                    // アプリの音量設定に戻す（アラーム音）
                    musicVolumeConfig(setMusicVol!!)
                    // アプリのの音量設定に戻す（テキスト読み上げ音）
                    voiceVolumeConfig(setVoiceVol!!)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        preVolumeSet() // 元の音量設定に戻す
    }

    override fun onStop() {
        super.onStop()
        preVolumeSet() // 元の音量設定に戻す
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.shutdown() // ttsのリソースを解放
        realm.close()
        preVolumeSet() // 元の音量設定に戻す
    }

    // アラームの初期化・再生
    private fun playAlarm() {
        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        rm = RingtoneManager.getRingtone(this, alarmUri)
        rm.play()
    }

    // バイブレーションを鳴らす
    private fun playVibration() {
        // Vibratorを取得
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        when {
            // API26以上
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val vibrationEffect =
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            }
            // API25以下
            else -> {
                vibrator.vibrate(500)
            }
        }
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
                Toast.makeText(this, "サポートされていない、あるいは無効な文字が含まれています。", Toast.LENGTH_SHORT).show()
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
                finish()
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
    private fun alarmDataGet() {
        calendar = Calendar.getInstance()
        setRepeat = intent?.getStringExtra("repeat")
        setVibration = intent?.getStringExtra("vibration")!!.toBoolean()
        setRequestCode = intent?.getStringExtra("requestCode")!!.toInt()
        setDayOfWeek = intent?.getStringExtra("dayOfWeek")?.toInt()
        setHour = intent?.getStringExtra("hour")?.toInt()
        setMinute = intent?.getStringExtra("minute")?.toInt()
        setMusicVol = intent?.getStringExtra("musicVol")!!.toInt()
        setVoiceVol = intent?.getStringExtra("voiceVol")!!.toInt()
        setSpeedVol = intent?.getStringExtra("speedVol")!!.toFloat()
        setSpeakText = intent?.getStringExtra("speakText")
        setLabelText = intent?.getStringExtra("labelText")
        autoSnoozeCount = intent.getStringExtra("autoSnoozeCount")!!.toInt()
    }

    // アラームを1週間後に再設定
    private fun setRepeatAlarmManager() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
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
                this, setRequestCode!!, intent,
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
