package com.example.speakalarm

import android.content.Context
import android.media.AudioManager
import android.media.AudioManager.STREAM_MUSIC
import android.media.AudioManager.STREAM_NOTIFICATION
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
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

    private var am: AudioManager? = null // AndroidManager
    private var preMusicVol: Int? = null // 端末の元の音量設定(アラーム音: メディアの音量)
    private var preVoiceVol: Int? = null // 端末の元の音量設定(テキスト読み上げ: 着信音の音量)
    private var onCreateMark: Boolean? = null // onCreateからの起動かを判定
    private var tts: TextToSpeech? = null // TextToSpeech
    private var rm: Ringtone? = null // RingtoneManager
    private var vibrationCb: Boolean = false // バイブレーションのON・OFF

    // タイムピッカーダイアログから取得した数字を「時刻」にセットする
    override fun onSelected(hourOfDay: Int, minute: Int) {
        timeText.text = "%1$02d:%2$02d".format(hourOfDay, minute)
    }

    // TestDialogの「OK」をタップした処理
    override fun alarmStop() {
        rm?.stop() // アラーム音楽を停止
        speakOut(speakText.text.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // オーディオマネージャー取得
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // 現在の端末の音量設定を格納(onDestroy()・onPause()・onStop()で元に戻す)
        getPreVolumeConfig()
        // onCreateから起動したことを確認する
        onCreateMark = true

        // 「時刻」に現在時刻を最初に表示する
        timeText.text = getToday()
        // 「時刻」をタップ
        timeView.setOnClickListener {
            // TimePickerDialogを呼び出す
            val dialog = TimePickerFragment()
            dialog.show(supportFragmentManager, "time_dialog")
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
            if (tts?.isSpeaking!!) {
                // まだテキストを読み上げている途中なら停止する
                tts?.stop()
            } else {
                if (speakText.text.toString() == "") {
                    showToast("テキストが入力されていません")
                } else {
                    // アラーム音再生
                    playAlarm()
                    // ダイアログを呼び出す
                    val dialog = TestDialog()
                    dialog.show(supportFragmentManager, "タグ名")
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

        // OKボタンの処理
        okBtn.setOnClickListener {

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
            false -> getPreVolumeConfig() // 現在の音量設定を取得
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
        // ttsのリソースを解放する
        tts?.shutdown()
        preVolumeSet() // 元の音量設定に戻す
    }

    // Toastメソッド
    private fun showToast(msg: String) {
        val toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
        toast.show()
    }

    // 現在時刻を取得するメソッド
    private fun getToday(): String {
        val c = Calendar.getInstance()
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        return "%1$02d:%2$02d".format(hour, minute)
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
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
            }
            // API20以下
            else -> {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }

    // アラームの音楽を鳴らす（RingtoneManager）
    private fun playAlarm() {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        rm = RingtoneManager.getRingtone(baseContext, alarmUri)
        rm?.play()
    }

    // アラーム音の音量の設定
    private fun musicVolumeConfig(volume: Int) {
        // 音量設定
        am!!.setStreamVolume(STREAM_NOTIFICATION, volume, 0)
    }

    // テキストを読み上げる音量の設定
    private fun voiceVolumeConfig(volume: Int) {
        // 音量設定
        am!!.setStreamVolume(STREAM_MUSIC, volume, 0)
    }

    // 現在の端末の音量設定を格納(onDestroy()・onPause()・onStop()で元に戻す)
    private fun getPreVolumeConfig() {
        preMusicVol = am!!.getStreamVolume(STREAM_NOTIFICATION) // アラーム音
        preVoiceVol = am!!.getStreamVolume(STREAM_MUSIC) // テキスト読み上げ音
    }

    // アラーム音・テキスト読み上げ音の音量設定を戻す
    private fun preVolumeSet() {
        am!!.setStreamVolume(STREAM_NOTIFICATION, preMusicVol!!, 0)
        am!!.setStreamVolume(STREAM_MUSIC, preVoiceVol!!, 0)
    }

    // テキストを読み上げる速さの設定
    private fun speedConfig(speed: Int) {
        val speedInt: Float = speed.toFloat()
        val result = speedInt / 6
        tts?.setPitch(0.9F)
        tts?.setSpeechRate(result)
    }
}
