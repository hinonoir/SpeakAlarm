// モデルクラス
package com.example.speakalarm


import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

open class TextAlarm : RealmObject() {
    @PrimaryKey
    var id: Long = 0
    var dateTime: Date = Date()
    // 時間
    // 曜日
    // テキスト
}
