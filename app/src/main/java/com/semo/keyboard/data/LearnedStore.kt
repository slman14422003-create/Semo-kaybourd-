package com.semo.keyboard.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class LearnedPrefs(private val map: Map<String, String>) {
    operator fun get(key: Preferences.Key<String>): String? = map[key.name]
}

internal class LearnedEditor(base: Map<String, String>) {
    private val values = HashMap(base)
    val dirty = HashMap<String, String?>()

    operator fun get(key: Preferences.Key<String>): String? = values[key.name]

    operator fun set(key: Preferences.Key<String>, value: String) {
        values[key.name] = value
        dirty[key.name] = value
    }

    fun remove(key: Preferences.Key<String>) {
        values.remove(key.name)
        dirty[key.name] = null
    }

    fun snapshot(): Map<String, String> = values
}

private class LearnedDb(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun readAll(): Map<String, String> {
        val out = HashMap<String, String>()
        readableDatabase.rawQuery("SELECT k, v FROM kv", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
        }
        return out
    }

    fun write(changes: Map<String, String?>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((k, v) in changes) {
                if (v == null) {
                    db.delete("kv", "k = ?", arrayOf(k))
                } else {
                    val cv = ContentValues(2)
                    cv.put("k", k)
                    cv.put("v", v)
                    db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val NAME = "semo_learned.db"
        const val VERSION = 1
    }
}

internal class LearnedStore private constructor(context: Context) {

    private val app = context.applicationContext
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, String>?>(null)
    private var db: LearnedDb? = null

    val prefs: Flow<LearnedPrefs> = flow {
        if (!app.isUserStorageUnlocked()) {
            emit(LearnedPrefs(emptyMap()))
            while (!app.isUserStorageUnlocked()) delay(1500)
        }
        load()
        emitAll(state.filterNotNull().map { LearnedPrefs(it) })
    }

    suspend fun snapshot(): Map<String, String> {
        load()
        return state.value ?: emptyMap()
    }

    suspend fun edit(transform: suspend (LearnedEditor) -> Unit) {
        load()
        mutex.withLock {
            val base = state.value ?: emptyMap()
            val editor = LearnedEditor(base)
            transform(editor)
            if (editor.dirty.isEmpty()) return
            val helper = db ?: return
            withContext(Dispatchers.IO) { helper.write(editor.dirty) }
            state.value = HashMap(editor.snapshot())
        }
    }

    private suspend fun load() {
        if (state.value != null) return
        mutex.withLock {
            if (state.value != null) return
            val loaded = runCatching { withContext(Dispatchers.IO) {
                val helper = LearnedDb(app)
                var rows = helper.readAll()
                if (rows[MIGRATED] == null) {
                    val legacy = runCatching { app.semoDataStore.data.first() }.getOrNull()
                    val moved = HashMap<String, String?>()
                    if (legacy != null) {
                        for (name in KEYS) {
                            if (rows.containsKey(name)) continue
                            legacy[stringPreferencesKey(name)]?.let { moved[name] = it }
                        }
                    }
                    moved[MIGRATED] = "1"
                    val written = runCatching { helper.write(moved) }.isSuccess
                    if (written) {
                        rows = helper.readAll()
                        runCatching {
                            app.semoDataStore.edit { p -> for (name in KEYS) p.remove(stringPreferencesKey(name)) }
                        }
                    }
                }
                db = helper
                rows
            } }.getOrDefault(emptyMap())
            state.value = loaded
        }
    }

    companion object {
        const val MIGRATED = "__migrated"

        val KEYS = listOf(
            "learned_words",
            "learned_bigrams",
            "ignored_corrections",
            "blocked_words",
            "learned_fixes",
            "learn_candidates",
            "rank_choices",
            "rank_profiles"
        )

        @Volatile
        private var instance: LearnedStore? = null

        fun get(context: Context): LearnedStore =
            instance ?: synchronized(this) {
                instance ?: LearnedStore(context).also { instance = it }
            }
    }
}
