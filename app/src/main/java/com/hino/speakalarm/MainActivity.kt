package com.hino.speakalarm

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.realm.Realm
import io.realm.Sort
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*

/*
* MainActivityのクラス。
* アラームの新規作成、作成済みのアラームの表示・ON/OFF・削除などを行う。
*/
class MainActivity : AppCompatActivity() {
    private lateinit var realm: Realm // Realm
    private lateinit var adapter: CustomRecyclerViewAdapter // Adapter
    private lateinit var layoutManager: RecyclerView.LayoutManager

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        // Realmのインスタンスを取得
        realm = Realm.getDefaultInstance()

        // アラーム新規作成ボタン
        fab.setOnClickListener {
            val intent = Intent(this, EditActivity::class.java)
            startActivity(intent)
            doubleTapStop(it)
        }
    }

    override fun onStart() {
        super.onStart()
        val realmResults = realm.where(SpeakAlarm::class.java)
            .findAll()
            .sort("id", Sort.DESCENDING)
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        adapter = CustomRecyclerViewAdapter(realmResults)
        recyclerView.adapter = this.adapter
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    // 連打防止メソッド
    private fun doubleTapStop(view: View) {
        view.isEnabled = false
        Handler().postDelayed({
            view.isEnabled = true
        }, 1000)
    }
}
