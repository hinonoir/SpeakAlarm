// Applicationクラスを継承するクラス（Realmの初期化・設定を行う）
package com.example.speakalarm

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration

class CustomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Realm.init(this) // Realmライブラリの初期化
        // カラム更新時にデータを削除 ※リリース時に「deleteRealmIfMigrationNeeded()」を消去
        val config = RealmConfiguration.Builder().deleteRealmIfMigrationNeeded().build()
        Realm.setDefaultConfiguration(config) // RealmConfigurationの設定
    }
}
