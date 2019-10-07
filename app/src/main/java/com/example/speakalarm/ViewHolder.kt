package com.example.speakalarm

import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.one_result.view.*

class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var onOffCheck: CheckBox? = null
    var label: TextView? = null
    var time: TextView? = null
    var repeatText: TextView? = null
    var speakText: TextView? = null
    var deleteBtn: ImageView? = null

    init {
        // ビューホルダーのプロパティとレイアウトのViewの対応
        onOffCheck = itemView.onOffCheck
        label = itemView.label
        time = itemView.time
        repeatText = itemView.repeat
        speakText = itemView.speakText
        deleteBtn = itemView.deleteBtn
    }
}
