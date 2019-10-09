package com.hino.speakalarm

import android.annotation.TargetApi
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import java.util.*

/*
* バックグランドから通知を出すクラス
* ※Android Q 以降はバックグラウンドからActivityを呼べないため
*/
class ForegroundService : Service() {
    private var preMusicVol: Int? = null // 端末の元の音量設定(アラーム音: メディアの音量)
    private var preVoiceVol: Int? = null // 端末の元の音量設定(テキスト読み上げ: 着信音の音量)
    private lateinit var am: AudioManager// AudioManager
    private lateinit var alarmUri: Uri // アラームを鳴らすUri
    private lateinit var rm: Ringtone // RingtoneManager
    private lateinit var vibrator: Vibrator // バイブレーション
    private var setVibration: Boolean? = false // バイブレーションのON・OFF
    private lateinit var calendar: Calendar // カレンダー
    private var setRepeat: String? = null // 繰り返しのON・OFF
    private var setDayOfWeek: String? = null // 起動したアラームの曜日
    private var setHour: String? = null // 起動したアラームの時間
    private var setMinute: String? = null // 起動したアラームの分
    private var setRequestCode: Int? = null // 起動したアラームのrequestCode
    private var setMusicVol: Int? = null // アラームの音量
    private var setVoiceVol: String? = null // 声の音量
    private var setSpeedVol: String? = null // 読み上げるスピード
    private var setSpeakText: String? = null // 読み上げるテキスト
    private var setLabelText: String? = null // アラームのラベル
    private var snoozeHandler: Handler? = null // Handler（遅延処理）
    private var runSnooze: Runnable? = null // 遅延処理でアラームを自動スヌーズ
    private var autoSnoozeCount: Int = 0 // 自動でスヌーズしたカウント（5回目でストップ）

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // アラームを鳴らすUri
        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        // RingtoneManager
        rm = RingtoneManager.getRingtone(this, alarmUri)
        // オーディオマネージャー取得（初期化）
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 現在の端末の音量設定を格納(onDestroy()で元に戻す)
        getPreVolumeConfig()
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        // アラームのデータを保存する
        alarmDataGet(intent)
        // 設定したアラームの音量
        musicVolumeConfig(setMusicVol!!)

        if (intent.getStringExtra("alarm")?.toString() == "on") {
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
                        .putExtra("dayOfWeek", setDayOfWeek)
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
                }
                snoozeHandler?.postDelayed(runSnooze!!, 60000)
            } else {
                val handler = Handler()
                handler.postDelayed({
                    // アラームストップ
                    val alarmStopIntent = Intent(this, AlarmStopBroadcastReceiver::class.java)
                        .putExtra("repeat", setRepeat)
                        .putExtra("vibration", setVibration.toString())
                        .putExtra("dayOfWeek", setDayOfWeek)
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
                }, 60000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        rm.stop() // アラーム音ストップ
        if (setVibration!!) vibrator.cancel() // バイブレーションが鳴っていたら止める
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
            .putExtra("dayOfWeek", setDayOfWeek)
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
            .putExtra("dayOfWeek", setDayOfWeek)
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

    // アラームの設定を取得する
    private fun alarmDataGet(intent: Intent) {
        calendar = Calendar.getInstance()
        setRepeat = intent.getStringExtra("repeat")
        setVibration = intent.getStringExtra("vibration")?.toBoolean()
        setRequestCode = intent.getStringExtra("requestCode")!!.toInt()
        setDayOfWeek = intent.getStringExtra("dayOfWeek")
        setHour = intent.getStringExtra("hour")
        setMinute = intent.getStringExtra("minute")
        setMusicVol = intent.getStringExtra("musicVol")!!.toInt()
        setVoiceVol = intent.getStringExtra("voiceVol")
        setSpeedVol = intent.getStringExtra("speedVol")
        setSpeakText = intent.getStringExtra("speakText")
        setLabelText = intent.getStringExtra("labelText")
        autoSnoozeCount = intent.getStringExtra("autoSnoozeCount")!!.toInt()
    }
}
