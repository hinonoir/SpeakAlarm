package com.example.speakalarm

import android.app.AlertDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.TimePicker
import androidx.fragment.app.DialogFragment
import java.util.*

// タイムピッカーのダイアログ
class TimePickerFragment : DialogFragment(), TimePickerDialog.OnTimeSetListener {

    interface OnTimeSelectedListener {
        fun onSelected(hourOfDay: Int, minute: Int)
        fun onInit(status: Int)
    }

    private var listener: OnTimeSelectedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        when (context) {
            is OnTimeSelectedListener -> listener = context
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val c = Calendar.getInstance()
        // 現在の時刻（初期値）を取得
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        return TimePickerDialog(context, this, hour, minute, true)
    }

    override fun onTimeSet(view: TimePicker?, hourOfDay: Int, minute: Int) {
        listener?.onSelected(hourOfDay, minute)
    }
}

// テスト再生に表示するダイアログ
class TestDialog : DialogFragment() {

    interface Listener {
        fun alarmStop()
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
            listener?.alarmStop()
        }
        return builder.create()
    }
}
