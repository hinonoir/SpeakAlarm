package com.example.speakalarm

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationCompat
import io.realm.Realm
import io.realm.kotlin.where
import java.util.*


// Android Q 以降はバックグラウンドからActivityを呼べないため、通知を出す。
class ForegroundService : Service(), TextToSpeech.OnInitListener {
    private var stopId: Int = 0
    private var isRunning: Boolean = false
    private var snoozeId: Int = 0

    private var preMusicVol: Int? = null // 端末の元の音量設定(アラーム音: メディアの音量)
    private var preVoiceVol: Int? = null // 端末の元の音量設定(テキスト読み上げ: 着信音の音量)
    private lateinit var am: AudioManager// AudioManager
    private var tts: TextToSpeech? = null // TextToSpeech
    private lateinit var alarmUri: Uri // アラームを鳴らすUri
    private lateinit var rm: Ringtone // RingtoneManager
    private lateinit var vibrator: Vibrator // バイブレーション
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
    private var snoozeHandler: Handler? = null // Handler（遅延処理）
    private var runSnooze: Runnable? = null // 遅延処理でアラームを自動スヌーズ
    private var autoSnoozeCount: Int = 0 // 自動でスヌーズしたカウント（5回目でストップ）
    private lateinit var realm: Realm // Realm

    override fun onBind(intent: Intent?): IBinder? {
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
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // アラームのデータを保存する
        alarmDataGet(intent)
        // 設定したアラームの音量
        musicVolumeConfig(setMusicVol!!)
        // 設定した声の音量
        voiceVolumeConfig(setVoiceVol!!)
        // TextToSpeechの初期化と設定
        speedConfig(setSpeedVol!!.toInt())

        val stopState = intent.getStringExtra("alarm")?.toString()
        when (stopState) {
            "on" -> stopId = 1
            "off" -> stopId = 0
        }
        if (!isRunning && stopId == 1) {
            // アラーム起動
            playAlarm() // アラームを鳴らす
            // バイブレーションを鳴らす
            if (setVibration!!) playVibration()
            fireNotification() // 通知起動
            // 何も操作がなければ1分後に自動的にスヌーズにする（5回まで）
            if (autoSnoozeCount < 5) {
                snoozeHandler = Handler()
                runSnooze = Runnable {
                    autoSnoozeCount++ // カウントアップ
                    // スヌーズ実行
                    val snoozeIntent = Intent(this, AlarmSnoozeBroadcastReceiver::class.java)
                        .putExtra("repeat", setRepeat)
                        .putExtra("vibration", setVibration.toString())
                        .putExtra("dayOfWeek", setDayOfWeek?.toString())
                        .putExtra("hour", setHour!!.toString())
                        .putExtra("minute", setMinute!!.toString())
                        .putExtra("requestCode", setRequestCode!!.toString())
                        .putExtra("musicVol", setMusicVol!!.toString())
                        .putExtra("voiceVol", setVoiceVol!!.toString())
                        .putExtra("speedVol", setSpeedVol!!.toString())
                        .putExtra("speakText", setSpeakText!!)
                        .putExtra("labelText", setLabelText)
                        .putExtra("autoSnoozeCount", autoSnoozeCount.toString())
                    sendBroadcast(snoozeIntent)
                    isRunning = false
                    stopId = 0
                    snoozeId = 0
                    setTtsListener() // テキストを読み終えたらアクティビティを終了
                    stopSelf()
                }
                snoozeHandler?.postDelayed(runSnooze!!, 3000)
            } else {
                val handler = Handler()
                handler.postDelayed({
                    // アラームストップ
                    val alarmStopIntent = Intent(this, AlarmStopBroadcastReceiver::class.java)
                        .putExtra("repeat", setRepeat)
                        .putExtra("vibration", setVibration.toString())
                        .putExtra("dayOfWeek", setDayOfWeek?.toString())
                        .putExtra("hour", setHour!!.toString())
                        .putExtra("minute", setMinute!!.toString())
                        .putExtra("requestCode", setRequestCode!!.toString())
                        .putExtra("musicVol", setMusicVol!!.toString())
                        .putExtra("voiceVol", setVoiceVol!!.toString())
                        .putExtra("speedVol", setSpeedVol!!.toString())
                        .putExtra("speakText", setSpeakText!!)
                        .putExtra("labelText", setLabelText)
                        .putExtra("autoSnoozeCount", "0")
                    sendBroadcast(alarmStopIntent)
                    isRunning = false
                    stopId = 0
                    setTtsListener()
                    stopSelf()
                }, 60000)
            }
            isRunning = true
            stopId = 0

        } else if (isRunning && stopId == 0) {
            // アラーム停止
            rm.stop()
            // バイブレーションがONなら止める
            if (setVibration!!) vibrator.cancel()
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
            setTtsListener() // 読み終えたらサービスを終了
            isRunning = false
            stopId = 0
        }

        // スヌーズ
        val snoozeState = intent.getStringExtra("snooze")?.toString()
        when (snoozeState) {
            "on" -> snoozeId = 1
            "off" -> snoozeId = 0
        }

        if (snoozeId == 1) {
            rm.stop()
            // バイブレーションがONなら止める
            if (setVibration!!) vibrator.cancel()
            Handler().postDelayed({
                speakOut(setSpeakText) // テキスト読み上げ
            }, 200)
            setSnoozeAlarmManager() // スヌーズセット
            // アラーム自動停止をキャンセル（1分後のスヌーズ処理をキャンセル）
            snoozeHandler?.removeCallbacks(runSnooze!!)
            // 4分後に自動でアクティビティを終了
            Handler().postDelayed({
                stopSelf()
            }, 4 * 60 * 1000)

            setTtsListener() // 読み終えたらサービスを終了
            isRunning = false
            stopId = 0
            snoozeId = 0
            Toast.makeText(this, "5分後にアラームが鳴ります", Toast.LENGTH_SHORT).show()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        tts!!.shutdown() // ttsのリソースを解放
        realm.close()
        rm.stop()
        if (setVibration!!) vibrator.cancel()
        preVolumeSet() // 元の音量設定に戻す
        // タップしてアラームを停止させた場合、1分後のスヌーズ処理をキャンセル
        snoozeHandler?.removeCallbacks(runSnooze!!)
    }

    // 通知メソッド
    @TargetApi(Build.VERSION_CODES.O)
    private fun fireNotification(): Int {
        // NotificationManagerを取得
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // カテゴリー名（通知設定画面に表示される情報）
        val name = "指定時刻のアラーム"
        // システムに登録するChannelのID
        val id = "casareal_foreground"
        // 通知の詳細情報（通知設定画面に表示される情報）
        val notifyDescription = "指定時刻にアラームが通知されます"

        // Channelの取得と生成
        if (manager.getNotificationChannel(id) == null) {
            val mChannel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            mChannel.apply {
                description = notifyDescription
                setSound(null, null) // 通知音を鳴らさない
            }
            manager.createNotificationChannel(mChannel)
        }

        // アラームストップの処理をブロードキャストに伝える
        val alarmStopIntent = Intent(this, AlarmStopBroadcastReceiver::class.java)
            .putExtra("repeat", setRepeat)
            .putExtra("vibration", setVibration.toString())
            .putExtra("dayOfWeek", setDayOfWeek?.toString())
            .putExtra("hour", setHour!!.toString())
            .putExtra("minute", setMinute!!.toString())
            .putExtra("requestCode", setRequestCode!!.toString())
            .putExtra("musicVol", setMusicVol!!.toString())
            .putExtra("voiceVol", setVoiceVol!!.toString())
            .putExtra("speedVol", setSpeedVol!!.toString())
            .putExtra("speakText", setSpeakText!!)
            .putExtra("labelText", setLabelText)
            .putExtra("autoSnoozeCount", autoSnoozeCount.toString())
        val stopPi = PendingIntent.getBroadcast(
            this,
            setRequestCode!!,
            alarmStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // スヌーズの処理をブロードキャストに伝える
        val snoozeIntent = Intent(this, AlarmSnoozeBroadcastReceiver::class.java)
            .putExtra("repeat", setRepeat)
            .putExtra("vibration", setVibration.toString())
            .putExtra("dayOfWeek", setDayOfWeek?.toString())
            .putExtra("hour", setHour!!.toString())
            .putExtra("minute", setMinute!!.toString())
            .putExtra("requestCode", setRequestCode!!.toString())
            .putExtra("musicVol", setMusicVol!!.toString())
            .putExtra("voiceVol", setVoiceVol!!.toString())
            .putExtra("speedVol", setSpeedVol!!.toString())
            .putExtra("speakText", setSpeakText!!)
            .putExtra("labelText", setLabelText)
            .putExtra("autoSnoozeCount", autoSnoozeCount.toString())
        val snoozePi = PendingIntent.getBroadcast(
            this,
            setRequestCode!!,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, id)
            .setContentTitle(setLabelText)
            .setContentText(setSpeakText)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .addAction(R.drawable.ic_launcher_background, "OK", stopPi)
            .addAction(R.drawable.ic_launcher_background, "スヌーズ", snoozePi)

            .build()

        notification.flags = Notification.FLAG_AUTO_CANCEL // サービス終了時に通知を消す
        notification.flags = Notification.FLAG_NO_CLEAR // スライドしても消えない

        Thread(
            Runnable {
                (0..5).map {
                    Thread.sleep(1000)
                }
                stopForeground(STOP_FOREGROUND_DETACH)
            }).start()

        startForeground(1, notification)

        return START_STICKY
    }


    // アラームの初期化・再生
    private fun playAlarm() {
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
