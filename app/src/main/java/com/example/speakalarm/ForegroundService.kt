package com.example.speakalarm

import android.annotation.TargetApi
import android.app.*
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.*

// Android Q 以降はバックグラウンドからActivityを呼べないため、通知を出す。
class ForegroundService : Service(), TextToSpeech.OnInitListener {

    private lateinit var rm: Ringtone // RingtoneManager
    private lateinit var alarmUri: Uri

    private var tts: TextToSpeech? = null

    private var stopId: Int = 0
    private var isRunning: Boolean = false

    private var snoozeId: Int = 0

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        // TextToSpeechの初期化
        tts = TextToSpeech(this, this)
        // アラーム初期化
        alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        rm = RingtoneManager.getRingtone(this, alarmUri)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tts!!.setPitch(0.9F)
        tts!!.setSpeechRate(1.5F)

        val stopState: String = intent?.getStringExtra("alarm").toString()
        when (stopState) {
            "on" -> stopId = 1
            "off" -> stopId = 0
        }
        if (!isRunning && stopId == 1) {
            rm.play()
            fireNotification()
            isRunning = true
            this.stopId = 0
        } else if (isRunning && stopId == 0) {
            rm.stop()
            isRunning = false
            this.stopId = 0
            tts!!.speak("おはようございます", TextToSpeech.QUEUE_FLUSH, null, "")
            setTtsListener()
        } else if (!isRunning && stopId == 0) {
            isRunning = false
            this.stopId = 0
        } else if (isRunning && stopId == 1) {
            isRunning = true
            this.stopId = 1
        }

        // スヌーズ
        val snoozeState: String = intent?.getStringExtra("snooze").toString()
        when (snoozeState) {
            "on" -> snoozeId = 1
            "off" -> snoozeId = 0
        }
        if (snoozeId == 1) {
            rm.stop()
            isRunning = false
            this.stopId = 0

            tts!!.speak("おはようございます", TextToSpeech.QUEUE_FLUSH, null, "")

            val calendar = Calendar.getInstance()
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.SECOND, 10)
            setAlarmManager(calendar)
            snoozeId = 0
            setTtsListener()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        tts!!.shutdown() // ttsのリソースを解放
    }

    // 通知メソッド
    @TargetApi(Build.VERSION_CODES.O)
    private fun fireNotification(): Int {
        // NotificationManagerを取得
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // カテゴリー名（通知設定画面に表示される情報）
        val name = "通知のタイトル的情報を設定"
        // システムに登録するChannelのID
        val id = "casareal_foreground"
        // 通知の詳細情報（通知設定画面に表示される情報）
        val notifyDescription = "この通知の詳細情報を設定します"

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
        val stopPi = PendingIntent.getBroadcast(this, 0, alarmStopIntent, 0)
        val snoozeIntent = Intent(this, AlarmSnoozeBroadcastReceiver::class.java)
        val snoozePi = PendingIntent.getBroadcast(this, 0, snoozeIntent, 0)

        val notification = NotificationCompat.Builder(this, id)
            .setContentTitle("アラームのタイトルが入る")
            .setContentText("読み上げるテキストが入る")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .addAction(R.drawable.ic_launcher_background, "OK", stopPi)
            .addAction(R.drawable.ic_launcher_background, "スヌーズ", snoozePi)
            .build()

        notification.flags = Notification.FLAG_AUTO_CANCEL // サービス終了時に通知を消す
        notification.flags = Notification.FLAG_NO_CLEAR // 消えない通知

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

    // TTS初期化を確認
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts!!.language = Locale.JAPANESE
//            Toast.makeText(this, "サポートされていない、あるいは無効な文字が含まれています。", Toast.LENGTH_SHORT).show()
        }
    }

    // テキストの読み上げの始まりと終わりを取得するメソッド
    private fun setTtsListener() {
        tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onError(utteranceId: String) {
                Log.d("testError", "progress on Error $utteranceId")
            }

            override fun onStart(utteranceId: String) {
                Log.d("testStart", "progress on Start $utteranceId")
            }

            override fun onDone(utteranceId: String) {
                Log.d("testEnd", "progress on Done $utteranceId")
                stopSelf()
            }
        })
    }

    // スヌーズのアラームセットメソッド
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun setAlarmManager(calendar: Calendar) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        intent.putExtra("alarm", "on")
        val pending = PendingIntent.getBroadcast(this, 0, intent, FLAG_UPDATE_CURRENT)

        val info = AlarmManager.AlarmClockInfo(calendar.timeInMillis, null)
        am.setAlarmClock(info, pending)
    }
}
