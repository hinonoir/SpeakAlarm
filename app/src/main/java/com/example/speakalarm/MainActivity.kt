package com.example.speakalarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

open class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null

    private lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        realm = Realm.getDefaultInstance()

        fab.setOnClickListener {
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
        }

        // TextToSpeechの設定
        tts = TextToSpeech(this, this)
        tts!!.setPitch(0.7F)
        tts!!.setSpeechRate(1.5F)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    // ttsの初期化
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.JAPANESE)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "サポートされていない、あるいは無効な文字が含まれています。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // テキストを読み上げるメソッド
    fun speakOut(text: String?, id: Int) {
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
}
