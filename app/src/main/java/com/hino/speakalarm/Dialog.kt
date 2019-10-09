package com.hino.speakalarm

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment

/*
* タイムピッカーのダイアログクラス。
* アラームの時刻指定に使用する。
*/
class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

    interface OnTimeSelectedListener {
        fun onSelected(hourOfDay: Int, minute: Int)
    }

    private var listener: OnTimeSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        when (context) {
            is OnTimeSelectedListener -> listener = context
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        // EditActivityからセットされている「時間」「分」を受け取る
        val bundle = arguments
        val hour = bundle!!.getInt("setHour")
        val minute = bundle!!.getInt("setMinute")
        return TimePickerDialog(context, this, hour, minute, true)
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        listener?.onSelected(hourOfDay, minute)
    }
}

/*
* アラームのテスト再生に表示するダイアログクラス。
*/
class TestDialog : DialogFragment() {

    interface Listener {
        fun testAlarmStop()
    }

    // リスナー用変数
    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        when (context) {
            // Listenerインターフェイスを実装したクラスだけを格納する
            is Listener -> listener = context
        }
    }

    // リスナー変数にアクティビティをセットする
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        this.isCancelable = false // ダイアログ外・バックキー無効
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle("テスト再生")
        builder.setMessage("「OK」をタップするとテキストを読み上げます")
        builder.setPositiveButton("OK") { _, _ ->
            listener?.testAlarmStop()
        }
        return builder.create()
    }
}

/*
* アラームの起動時に表示するダイアログクラス。（Android9以下）
* ラベル・テキストの内容を表示する。
* OKでアラームストップ、スヌーズで5分後にアラームを鳴らす。
*/
class AlarmDialog : DialogFragment() {

    interface Listener {
        fun alarmStop()
        fun snooze()
    }

    // リスナー用変数
    private var listener: Listener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        when (context) {
            // Listenerインターフェイスを実装したクラスだけを格納する
            is Listener -> listener = context
        }
    }

    // リスナー変数にアクティビティをセットする
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        this.isCancelable = false // ダイアログ外・バックキー無効
        // テキストの内容を取得
        val bundle = arguments
        val speakText = bundle!!.getString("speakText")
        val labelText = bundle.getString("labelText")
        val builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(labelText)
        builder.setMessage(speakText)
        builder.setPositiveButton("OK") { _, _ ->
            listener?.alarmStop()
        }
        builder.setNegativeButton("スヌーズ") { _, _ ->
            listener?.snooze()
        }
        return builder.create()
    }
}
