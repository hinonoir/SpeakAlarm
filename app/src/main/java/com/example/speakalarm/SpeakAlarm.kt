// モデルクラス
package com.example.speakalarm

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

open class SpeakAlarm : RealmObject() {
    @PrimaryKey
    var id: Long = 0
    var date: Date = Date() // 作成日時
    var alarm: String = "" // アラームのON/OFF
    var repeat: String = "" // 曜日繰り返しON/OFF
    var vibration: String = "" // バイブON/OFF
    var requestCode: Int = 0 // リクエストコード
    var dayOfWeek: Int = 0 // 繰り返す曜日
    var hour: Int = 0 // 時刻（時間）
    var minute: Int = 0 // 時刻（分）
    var musicVol: Int = 0 // アラームの音量
    var voiceVol: Int = 0 // 読み上げる音量
    var speedVol: Float = 0F // 読み上げるスピード
    var speakText: String = "" // テキスト内容
    var labelText: String = "" // ラベル
    var autoSnoozeCount: Int = 0 // 自動でスヌーズした回数
}
