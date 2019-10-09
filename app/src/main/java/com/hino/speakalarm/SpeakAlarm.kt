// モデルクラス
package com.hino.speakalarm

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import java.util.*

/*
* モデルクラスの作成をするクラス
*/
open class SpeakAlarm : RealmObject() {
    @PrimaryKey
    var id: Int = 0
    var date: Date = Date() // 作成日時
    var alarm: String = "" // アラームのON/OFF
    var repeat: String = "" // 曜日繰り返しON/OFF
    var repeatText: String = "" // 繰り返す曜日（RecyclerViewに表示）
    var dayOfWeek: String? = ""// 繰り返す曜日の配列
    var requestCode: Int = 0 // 一番新しいrequestCodeを記憶（max()で検索できるように）
    var rcMonday: Int? = null // 月曜日のrequestCode
    var rcTuesday: Int? = null // 火曜日のrequestCode
    var rcWednesday: Int? = null // 水曜日のrequestCode
    var rcThursday: Int? = null // 木曜日のrequestCode
    var rcFriday: Int? = null // 金曜日のrequestCode
    var rcSaturday: Int? = null // 土曜日のrequestCode
    var rcSunday: Int? = null // 日曜日のrequestCode
    var hour: Int = 0 // 時刻（時間）
    var minute: Int = 0 // 時刻（分）
    var musicVol: Int = 0 // アラームの音量
    var voiceVol: Int = 0 // 読み上げる音量
    var speedVol: Float = 0F// 読み上げるスピード
    var speakText: String = "" // テキスト内容
    var labelText: String? = "" // ラベル
    var vibration: Boolean? = null // バイブON/OFF
}
