package com.example.speakalarm

import io.realm.DynamicRealm
import io.realm.RealmMigration


class Migration : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        val realmSchema = realm.schema
        var oldVersion = oldVersion
        val userSchema = realmSchema.get("SpeakAlarm")

        if (oldVersion == 0L) {
            userSchema?.addField("weather", Boolean::class.java)
            oldVersion++
        }
    }
}
