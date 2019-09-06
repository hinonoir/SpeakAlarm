// Applicationクラスを継承するクラス（Realmの初期化・設定を行う）
package com.example.speakalarm

import android.app.Application
import io.realm.Realm
import io.realm.RealmConfiguration

class CustomApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Realm.init(this) // Realmライブラリの初期化
        val config = RealmConfiguration.Builder().build()
        Realm.setDefaultConfiguration(config) // RealmConfigurationの設定
    }
}
