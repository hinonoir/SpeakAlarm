package com.hino.speakalarm

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import io.realm.RealmResults
import io.realm.kotlin.where
import java.util.*

/*
* RecyclerView.Adapterを継承するクラス。
* RealmResults<SpeakAlarm>を受け取り、
* one_resultの作成、件数の取得、speakAlarmオブジェクト取得を行う。
*/
class CustomRecyclerViewAdapter(
    realmResults: RealmResults<SpeakAlarm>
) : RecyclerView.Adapter<ViewHolder>() {
    private var rResults: RealmResults<SpeakAlarm> = realmResults

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 表示するレイアウトを設定
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.one_result, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return rResults.size
    }

    // one_result.xmlの処理
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val speakAlarm = rResults[position] // アラームの位置を取得
        val realm = Realm.getDefaultInstance()
        holder.onOffCheck?.isChecked = speakAlarm!!.alarm == "on" // アラームのON/OFF
        holder.label?.text = speakAlarm.labelText // ラベル
        // アラームの時刻
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, speakAlarm.hour)
        cal.set(Calendar.MINUTE, speakAlarm.minute)
        holder.time?.text = DateFormat.format("HH:mm", cal)
        holder.repeatText?.text = speakAlarm.repeatText // 繰り返す曜日
        holder.speakText?.text = speakAlarm.speakText // 読み上げるテキスト
        // タップされたらタップしたアラームの編集画面に移る
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, EditActivity::class.java)
            intent.putExtra("id", speakAlarm.id)
            it.context.startActivity(intent)
            doubleTapStop(it) // 連打防止
        }

        // アラームのON/OFFの処理
        holder.onOffCheck?.setOnClickListener {
            // ONまたはOFFにするrequestCodeをonOffServiceに渡す
            val intent = Intent(it.context, onOffService::class.java)
            getRequestCode(speakAlarm, intent)
            if (holder.onOffCheck?.isChecked!!) {
                // DBにonで登録
                realm.executeTransaction {
                    speakAlarm.alarm = "on"
                }
                // ONにするrequestCodeと共にonOffServiceに渡す
                intent.putExtra("alarm", "on")
                intent.putExtra("vibration", speakAlarm.vibration)
                intent.putExtra("hour", speakAlarm.hour)
                intent.putExtra("minute", speakAlarm.minute)
                intent.putExtra("musicVol", speakAlarm.musicVol)
                intent.putExtra("voiceVol", speakAlarm.voiceVol)
                intent.putExtra("speedVol", speakAlarm.speedVol)
                intent.putExtra("speakText", speakAlarm.speakText)
                intent.putExtra("labelText", speakAlarm.labelText)

            } else {
                // DBにoffで登録
                realm.executeTransaction {
                    speakAlarm.alarm = "off"
                }
                // OFFにするrequestCodeと共にonOffServiceに渡す
                intent.putExtra("alarm", "off")
            }
            it.context.startService(intent) // onOffService起動
            doubleTapStop(it) // 連打防止
        }

        // アラームを削除する処理
        holder.deleteBtn?.setOnClickListener {
            // アラームを削除する処理 ※確認のダイアログを表示する！！
            val builder = AlertDialog.Builder(it.context).create()
            if (!speakAlarm.labelText!!.isBlank()) {
                builder.setTitle("${speakAlarm.labelText}を削除しますか？")
            } else {
                builder.setTitle("このアラームを削除しますか？")
            }
            builder.setMessage(speakAlarm.speakText)
            builder.setButton(
                AlertDialog.BUTTON_POSITIVE,
                "削除"
            ) { _: DialogInterface?, _: Int ->
                // アラームがONならOFFにする
                val intent = Intent(it.context, onOffService::class.java)
                getRequestCode(speakAlarm, intent)
                intent.putExtra("alarm", "off")
                it.context.startService(intent)
                // タップしたアラームを削除する
                realm.executeTransaction {
                    realm.where<SpeakAlarm>()
                        .equalTo("id", speakAlarm.id)
                        .findFirst()?.deleteFromRealm()
                }
                // アラームの位置を取得して消す
                notifyItemRemoved(position)
                notifyDataSetChanged()
                Toast.makeText(it.context, "削除しました", Toast.LENGTH_SHORT).show()
            }
            builder.setButton(
                AlertDialog.BUTTON_NEGATIVE,
                "キャンセル"
            ) { _: DialogInterface?, _: Int ->
            }
            builder.show()
            doubleTapStop(it) // 連打防止
        }
    }

    // requestCode受け渡し
    private fun getRequestCode(speakAlarm: SpeakAlarm, intent: Intent) {
        if (speakAlarm.rcMonday != null) {
            intent.putExtra("rcMon", speakAlarm.rcMonday!!)
        }
        if (speakAlarm.rcTuesday != null) {
            intent.putExtra("rcTue", speakAlarm.rcTuesday!!)
        }
        if (speakAlarm.rcWednesday != null) {
            intent.putExtra("rcWed", speakAlarm.rcWednesday!!)
        }
        if (speakAlarm.rcThursday != null) {
            intent.putExtra("rcThu", speakAlarm.rcThursday!!)
        }
        if (speakAlarm.rcFriday != null) {
            intent.putExtra("rcFri", speakAlarm.rcFriday!!)
        }
        if (speakAlarm.rcSaturday != null) {
            intent.putExtra("rcSat", speakAlarm.rcSaturday!!)
        }
        if (speakAlarm.rcSunday != null) {
            intent.putExtra("rcSun", speakAlarm.rcSunday!!)
        }
        // 1回だけのパターン
        if ((speakAlarm.rcMonday == null) && (speakAlarm.rcTuesday == null) &&
            (speakAlarm.rcWednesday == null) && (speakAlarm.rcThursday == null) &&
            (speakAlarm.rcFriday == null) && (speakAlarm.rcSaturday == null) &&
            (speakAlarm.rcSunday == null)
        ) {
            intent.putExtra("rcOnce", speakAlarm.requestCode)
        }
    }

    // 連打防止メソッド
    private fun doubleTapStop(view: View) {
        view.isEnabled = false
        Handler().postDelayed({
            view.isEnabled = true
        }, 1000)
    }
}
