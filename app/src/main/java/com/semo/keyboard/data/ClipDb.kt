package com.semo.keyboard.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.semo.keyboard.domain.model.ClipItem

internal class ClipDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    class Row(val key: String, val kind: Int, val bytes: Long, val pinned: Boolean)

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE clips (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "key TEXT NOT NULL UNIQUE, " +
                "pinned INTEGER NOT NULL DEFAULT 0, " +
                "ts INTEGER NOT NULL, " +
                "bytes INTEGER NOT NULL DEFAULT 0, " +
                "kind INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX clips_order ON clips (ts DESC, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(limit: Int): List<ClipItem> {
        val out = ArrayList<ClipItem>()
        readableDatabase.rawQuery(
            "SELECT key, pinned FROM clips ORDER BY ts DESC, id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(ClipItem(c.getString(0), c.getInt(1) == 1))
        }
        return out
    }

    fun rows(): List<Row> {
        val out = ArrayList<Row>()
        readableDatabase.rawQuery(
            "SELECT key, kind, bytes, pinned FROM clips ORDER BY ts DESC, id DESC",
            null
        ).use { c ->
            while (c.moveToNext()) out.add(Row(c.getString(0), c.getInt(1), c.getLong(2), c.getInt(3) == 1))
        }
        return out
    }

    fun newestKey(): String? = readableDatabase.rawQuery(
        "SELECT key FROM clips ORDER BY ts DESC, id DESC LIMIT 1", null
    ).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    fun isPinned(key: String): Boolean = readableDatabase.rawQuery(
        "SELECT pinned FROM clips WHERE key = ?", arrayOf(key)
    ).use { c -> c.moveToFirst() && c.getInt(0) == 1 }

    fun exists(key: String): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM clips WHERE key = ?", arrayOf(key)
    ).use { c -> c.moveToFirst() }

    fun upsert(key: String, kind: Int, bytes: Long, pinned: Boolean? = null, ts: Long = System.currentTimeMillis()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("ts", ts)
                put("kind", kind)
                put("bytes", bytes)
                if (pinned != null) put("pinned", if (pinned) 1 else 0)
            }
            val updated = db.update("clips", values, "key = ?", arrayOf(key))
            if (updated == 0) {
                values.put("key", key)
                if (pinned == null) values.put("pinned", 0)
                db.insert("clips", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun tsOf(key: String): Long? = readableDatabase.rawQuery(
        "SELECT ts FROM clips WHERE key = ?", arrayOf(key)
    ).use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    fun setPinned(key: String, pinned: Boolean) {
        val values = ContentValues().apply { put("pinned", if (pinned) 1 else 0) }
        writableDatabase.update("clips", values, "key = ?", arrayOf(key))
    }

    fun delete(key: String) {
        writableDatabase.delete("clips", "key = ?", arrayOf(key))
    }

    fun deleteKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            keys.forEach { db.delete("clips", "key = ?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clear() {
        writableDatabase.delete("clips", null, null)
    }

    companion object {
        private const val NAME = "semo_clips.db"
        private const val VERSION = 1
    }
}
