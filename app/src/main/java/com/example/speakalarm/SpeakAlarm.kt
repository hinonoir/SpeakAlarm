// モデルクラス
package com.example.speakalarm


import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

open class SpeakAlarm : RealmObject() {
    @PrimaryKey
    var id: Long = 0
    var date: Date = Date()
    // 時間
    // 曜日
    var speakText: String = ""
}
