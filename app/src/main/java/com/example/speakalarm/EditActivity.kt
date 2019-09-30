package com.example.speakalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioManager.STREAM_NOTIFICATION
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.*
import android.os.VibrationEffect.DEFAULT_AMPLITUDE
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_edit.*
import kotlinx.android.synthetic.main.dialog_dayofweek.view.*
import java.util.*

class EditActivity : AppCompatActivity(), TimePickerFragment.OnTimeSelectedListener,
    TextToSpeech.OnInitListener, TestDialog.Listener {

    // 曜日のチェックボックスの判定 ※今はfalseにしているが、データベースから引っ張ってくる
    private var mondayCb: Boolean = false
    private var tuesdayCb: Boolean = false
    private var wednesdayCb: Boolean = false
    private var thursdayCb: Boolean = false
    private var fridayCb: Boolean = false
    private var saturdayCb: Boolean = false
    private var sundayCb: Boolean = false

    private var preMusicVol: Int? = null // 端末の元の音量設定(アラーム音: メディアの音量)
    private var preVoiceVol: Int? = null // 端末の元の音量設定(テキスト読み上げ: 着信音の音量)
    private lateinit var calendar: Calendar // カレンダー
    private var setHour: Int? = null // セットする時間
    private var setMinute: Int? = null // セットする分
    private lateinit var am: AudioManager // AudioManager
    private var onCreateMark: Boolean? = null // onCreateからの起動かを判定
    private var tts: TextToSpeech? = null // TextToSpeech
    private lateinit var rm: Ringtone // RingtoneManager
    private var musicVol: Int? = null // アラームの音量
    private var voiceVol: Int? = null // 声の音量
    private var speedVol: Float? = null // 読み上げるスピード
    private lateinit var vibrator: Vibrator // バイブレーション
    private var vibrationCb: Boolean = false // バイブレーションのON・OFF
    private lateinit var realm: Realm // Realm

    // タイムピッカーダイアログから取得した数字を「時刻」にセットする
    override fun onSelected(hourOfDay: Int, minute: Int) {

        // 時間差分を取得
        val timeLagResult = TimeLagCalculation(hourOfDay, minute)
        val timeLagHour = timeLagResult.first
        val timeLagMinute = timeLagResult.second

        // 設定時刻を表示
        timeText.text = "%1$02d:%2$02d".format(hourOfDay, minute)
        // 時間差分を表示
        timeLagText.text =
            "%1$01d時間%2$02d分後に設定されています。".format(timeLagHour, timeLagMinute)

        // 設定時刻を格納
        setHour = hourOfDay
        setMinute = minute
    }

    // TestDialogの「OK」をタップした処理
    override fun testAlarmStop() {
        rm.stop() // アラーム音楽を停止
        // バイブレーションがONなら止める
        if (vibrationCb) {
            vibrator.cancel()
        }
        Handler().postDelayed({
            speakOut(speakText.text.toString()) // テキストを読み上げる
        }, 200)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        // Realmのインスタンス取得
        realm = Realm.getDefaultInstance()
        // オーディオマネージャー取得（初期化）
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 現在の端末の音量設定を格納(onDestroy()・onPause()・onStop()で元に戻す)
        getPreVolumeConfig()
        // onCreateから起動したことを確認する
        onCreateMark = true

        // カレンダーを取得
        calendar = Calendar.getInstance()
        // 「時刻」に現在時刻を最初に表示・設定する
        timeText.text = getToday()
        // 「時刻」をタップ
        timeView.setOnClickListener {
            // Bundleを生成し、「時間」「分」を格納
            val bundle = Bundle()
            bundle.putInt("setHour", setHour!!)
            bundle.putInt("setMinute", setMinute!!)

            // TimePickerDialogを呼び出す
            val fragment = TimePickerFragment()
            fragment.arguments = bundle // バンドルをセット
            fragment.show(supportFragmentManager, "time_dialog")

            // 連打防止
            doubleTapStop(it)
        }

        // 「繰り返し」をタップ
        dayofweekView.setOnClickListener {
            // customViewとなるViewを拡張（inflate）して作成
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_dayofweek, null)
            val monday = dialogView.findViewById<CheckBox>(R.id.monday)
            val tuesday = dialogView.findViewById<CheckBox>(R.id.tuesday)
            val wednesday = dialogView.findViewById<CheckBox>(R.id.wednesday)
            val thursday = dialogView.findViewById<CheckBox>(R.id.thursday)
            val friday = dialogView.findViewById<CheckBox>(R.id.friday)
            val saturday = dialogView.findViewById<CheckBox>(R.id.saturday)
            val sunday = dialogView.findViewById<CheckBox>(R.id.sunday)

            // 曜日選択のダイアログを呼び出す
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setView(dialogView).setTitle(R.string.dayofweek_title)
            val dayOfWeekDialog = dialogBuilder.show()

            // 曜日にチェックが入っているか判定
            if (mondayCb) monday.isChecked = true
            if (tuesdayCb) tuesday.isChecked = true
            if (wednesdayCb) wednesday.isChecked = true
            if (thursdayCb) thursday.isChecked = true
            if (fridayCb) friday.isChecked = true
            if (saturdayCb) saturday.isChecked = true
            if (sundayCb) sunday.isChecked = true

            // OKボタンの処理
            dialogView.okBtn.setOnClickListener {
                // チェックした曜日のテキストを繰り返しビューに反映する
                var result: String? = ""
                when (monday.isChecked) {
                    true -> {
                        result += " 月"
                        mondayCb = true
                    }
                    false -> mondayCb = false
                }
                when (tuesday.isChecked) {
                    true -> {
                        result += " 火"
                        tuesdayCb = true
                    }
                    false -> tuesdayCb = false
                }
                when (wednesday.isChecked) {
                    true -> {
                        result += " 水"
                        wednesdayCb = true
                    }
                    false -> wednesdayCb = false
                }
                when (thursday.isChecked) {
                    true -> {
                        result += " 木"
                        thursdayCb = true
                    }
                    false -> thursdayCb = false
                }
                when (friday.isChecked) {
                    true -> {
                        result += " 金"
                        fridayCb = true
                    }
                    false -> fridayCb = false
                }
                when (saturday.isChecked) {
                    true -> {
                        result += " 土"
                        saturdayCb = true
                    }
                    false -> saturdayCb = false
                }
                when (sunday.isChecked) {
                    true -> {
                        result += " 日"
                        sundayCb = true
                    }
                    false -> sundayCb = false
                }
                when (result) {
                    " 月 火 水 木 金 土 日" -> result = "毎日"
                    " 月 火 水 木 金" -> result = "平日"
                    " 土 日" -> result = "週末"
                    "" -> result = "1回のみ"
                }
                dayofweekText?.text = result // 繰り返しビューに挿入
                // 繰り返しビューに挿入
                dayOfWeekDialog.dismiss()
            }
            // キャンセルの処理
            dialogView.cancelBtn.setOnClickListener {
                dayOfWeekDialog.dismiss()
            }

            // 連打防止
            doubleTapStop(it)
        }

        // TextToSpeechの初期化、初期設定
        tts = TextToSpeech(this, this)
        tts?.setPitch(0.9F)
        speedConfig(speedSeekbar.progress)
        // テスト再生ボタン
        testPlayBtn.setOnClickListener {
            // 連打防止
            doubleTapStop(it)
            if (speakText.text.toString() == "") {
                showToast("テキストが入力されていません")
            } else {
                // アラーム音再生
                playAlarm()
                // バイブレーションにチェックが入っていたら鳴らす
                if (vibrationCb) {
                    playVibration()
                }
                // ダイアログを呼び出す
                val fragment = TestDialog()
                fragment.show(supportFragmentManager, "タグ名")

                if (tts?.isSpeaking!!) {
                    // まだテキストを読み上げている途中なら停止する
                    tts?.stop()
                }
            }
        }

        // アラーム音音量初期設定
        musicVolumeConfig(musicVolSeekbar.progress)
        // アラーム音音量のシークバー
        musicVolSeekbar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                //ツマミがドラッグされると呼ばれる
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    musicVolumeConfig(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            }
        )

        // テキスト読み上げ音量初期設定
        voiceVolumeConfig(voiceVolSeekbar.progress)
        // テキスト読み上げ音量のシークバー
        voiceVolSeekbar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                //ツマミがドラッグされると呼ばれる
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    voiceVolumeConfig(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            }
        )

        // 速さの初期設定
        speedConfig(speedSeekbar.progress)
        // 速さのシークバー
        speedSeekbar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                //ツマミがドラッグされると呼ばれる
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    // progressの数値をspeedConfig()に投げる
                    speedConfig(progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                }
            }
        )

        // バイブレーションのチェックボックス
        vibCheckbox.setOnClickListener {
            vibrationCb = vibCheckbox.isChecked
        }

        // アラームのラベル
        labelView.setOnClickListener {
            // TODO:編集時にあらかじめ表示する
        }

        // TODO:アラーム取り消しボタン（リリースの際に消す）
        alarmDeleteBtn.setOnClickListener {
            cancelAlarmManager()
        }

        // OKボタンの処理
        okBtn.setOnClickListener {
            // 読み上げるテキストが入力されていたらアラーム設定の処理を実行する
            if (!speakText.text.toString().isBlank()) {
                // 指定した時刻と曜日を設定する
                if (mondayCb) setRepeatAlarmManager(2, 3)
                if (tuesdayCb) setRepeatAlarmManager(3, 4)
                if (wednesdayCb) setRepeatAlarmManager(4, 5)
                if (thursdayCb) setRepeatAlarmManager(5, 6)
                if (fridayCb) setRepeatAlarmManager(6, 7)
                if (saturdayCb) setRepeatAlarmManager(0, 1)
                if (sundayCb) setRepeatAlarmManager(1, 2)

                // 繰り返す曜日にチェックがなかったら1回のみ鳴らす
                if ((!mondayCb) && (!tuesdayCb) && (!wednesdayCb) && (!thursdayCb) &&
                    (!fridayCb) && (!saturdayCb) && (!sundayCb)
                ) {
                    // 設定した時刻をセット
                    calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, setHour!!)
                    calendar.set(Calendar.MINUTE, setMinute!!)
                    calendar.set(Calendar.SECOND, 0)
                    setOnceAlarmManager()
                }

                // 時間差分を取得
                val timeLagResult = TimeLagCalculation(setHour!!, setMinute!!)
                val timeLagHour = timeLagResult.first
                val timeLagMinute = timeLagResult.second
                val configCompletionMsg =
                    "%1$01d時間%2$02d分後にアラームが鳴ります".format(timeLagHour, timeLagMinute)
                // 時間差分をトーストで表示
                showToast(configCompletionMsg)

                it.isEnabled // 連打防止
                finish()
            } else {
                // 読み上げるテキストが未入力ならアラーム設定の処理を実行しない
                showToast("読み上げるテキストが未入力です")
            }
        }

        // キャンセルの処理
        cancelBtn.setOnClickListener {
            finish()
        }

        // 親のScrollViewにタッチイベントを奪われないようにする処理
        speakText.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (speakText.hasFocus()) {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    when (event.action and MotionEvent.ACTION_MASK) {
                        MotionEvent.ACTION_SCROLL -> {
                            v.parent.requestDisallowInterceptTouchEvent(false)
                            return true
                        }
                    }
                }
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // onCreate()から起動した場合は、現在の音量設定を取得しない
        when (onCreateMark) {
            true -> onCreateMark = false
            false -> {
                // 現在の端末の音量設定を取得
                getPreVolumeConfig()
                // シークバーの音量設定に戻す（アラーム音）
                musicVolumeConfig(musicVolSeekbar.progress)
                // シークバーの音量設定に戻す（テキスト読み上げ音）
                voiceVolumeConfig(voiceVolSeekbar.progress)
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
        preVolumeSet() // 元の音量設定に戻す
        realm.close() // realmのリソースを解放
    }

    // Toastメソッド
    private fun showToast(msg: String) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast.show()
    }

    // 現在時刻を取得・セットするメソッド
    private fun getToday(): String {
        setHour = calendar.get(Calendar.HOUR_OF_DAY)
        setMinute = calendar.get(Calendar.MINUTE)
        return "%1$02d:%2$02d".format(setHour, setMinute)
    }

    // 時間差分を計算するメソッド
    private fun TimeLagCalculation(hourOfDay: Int, minute: Int): Pair<Int, Int> {
        calendar = Calendar.getInstance() // カレンダー情報取得
        // 現在の時間を取得
        val currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        // 設定時刻と現在時刻の時間差分を取得
        val resultHour = hourOfDay - currentHourOfDay
        val resultMinute = minute - currentMinute
        var timeLagHour: Int?
        var timeLagMinute: Int?

        // resultHourがマイナスか判別
        if (resultHour < 0) {
            timeLagHour = (24 - currentHourOfDay) + (hourOfDay)
        } else {
            timeLagHour = resultHour
        }

        // resultMinuteがマイナスか判別
        if (resultMinute < 0) {
            timeLagMinute = (60 - currentMinute) + (minute)

            if (resultHour < 0) {
                timeLagHour = (24 - currentHourOfDay) + (hourOfDay) - 1
            } else if (resultHour == 0) {
                timeLagHour = 23
            }

        } else {
            timeLagMinute = resultMinute
        }

        // 時間差分の「時間」「分」を返す
        return Pair(timeLagHour, timeLagMinute)
    }

    // 連打防止メソッド
    private fun doubleTapStop(view: View) {
        view.isEnabled = false
        Handler().postDelayed({
            view.isEnabled = true
        }, 1000)
    }

    // TextToSpeechの初期化判定
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "サポートされていない、あるいは無効な文字が含まれています。", Toast.LENGTH_SHORT).show()
            }
        }
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

    // アラームの音楽を鳴らす（RingtoneManager）
    private fun playAlarm() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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
                    VibrationEffect.createWaveform(
                        longArrayOf(500, 0),
                        intArrayOf(0, DEFAULT_AMPLITUDE), 0
                    )
                vibrator.vibrate(vibrationEffect)
            }
            // API25以下
            else -> {
                vibrator.vibrate(longArrayOf(500, 0), 0)
            }
        }
    }

    // アラーム音の音量の設定
    private fun musicVolumeConfig(volume: Int) {
        // 音量設定
        am.setStreamVolume(STREAM_NOTIFICATION, volume, 0)
        musicVol = volume
    }

    // テキストを読み上げる音量の設定
    private fun voiceVolumeConfig(volume: Int) {
        // 音量設定
        am.setStreamVolume(STREAM_MUSIC, volume, 0)
        voiceVol = volume
    }

    // 現在の端末の音量設定を格納(onDestroy()・onPause()・onStop()で元に戻す)
    private fun getPreVolumeConfig() {
        preMusicVol = am.getStreamVolume(STREAM_NOTIFICATION) // アラーム音
        preVoiceVol = am.getStreamVolume(STREAM_MUSIC) // テキスト読み上げ音
    }

    // アラーム音・テキスト読み上げ音の音量設定を戻す
    private fun preVolumeSet() {
        am.setStreamVolume(STREAM_NOTIFICATION, preMusicVol!!, 0)
        am.setStreamVolume(STREAM_MUSIC, preVoiceVol!!, 0)
    }

    // テキストを読み上げる速さの設定
    private fun speedConfig(speed: Int) {
        val speedInt: Float = speed.toFloat()
        val result = speedInt / 6
        tts?.setSpeechRate(result)
        speedVol = result
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
        intent.putExtra("speakText", speakText!!.text.toString())
        intent.putExtra("labelText", labelText.text.toString())
        intent.putExtra("autoSnoozeCount", "0")
        val pending = PendingIntent.getBroadcast(this, requestCode, intent, FLAG_UPDATE_CURRENT)

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
    private fun setOnceAlarmManager() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        // アラームの設定をレシーバーに送る
        intent.putExtra("alarm", "on")
        intent.putExtra("vibration", vibrationCb.toString())
        intent.putExtra("requestCode", "0") // requestCodeをレシーバーに送る
        intent.putExtra("musicVol", musicVol!!.toString())
        intent.putExtra("voiceVol", voiceVol!!.toString())
        intent.putExtra("speedVol", speedVol!!.toString())
        intent.putExtra("speakText", speakText!!.text.toString())
        intent.putExtra("labelText", labelText.text.toString())
        intent.putExtra("autoSnoozeCount", "0")
        val pending = PendingIntent.getBroadcast(this, 0, intent, FLAG_UPDATE_CURRENT)

        // 時刻セット
        calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, setHour!!)
        calendar.set(Calendar.MINUTE, setMinute!!)
        calendar.set(Calendar.SECOND, 0)

        // セットした時間が現在より前だった場合、翌日に設定
//        if (calendar.timeInMillis < System.currentTimeMillis()) {
//            calendar.add(Calendar.DAY_OF_YEAR, 1)
//        }

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

    // TODO:アラームキャンセルの処理
    // アラームキャンセルのメソッド
    private fun cancelAlarmManager() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmBroadcastReceiver::class.java)
        val pending = PendingIntent.getBroadcast(this, 0, intent, 0)
        am.cancel(pending)
    }
}
