package com.bstc.app

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray

object L { val hi = mutableStateOf(false) }
fun t(en: String, hi: String) = if (L.hi.value) hi else en

class Sj(val c: String, val en: String, val hi: String) { fun name() = t(en, hi) }
val SJ = listOf(
    Sj("MA", "Mental Ability", "मानसिक योग्यता"),
    Sj("GK", "Rajasthan GK", "राजस्थान सामान्य ज्ञान"),
    Sj("TA", "Teaching Aptitude", "शिक्षण अभिक्षमता"),
    Sj("EN", "English", "अंग्रेज़ी"),
    Sj("HI", "Hindi", "हिन्दी"),
    Sj("SA", "Sanskrit", "संस्कृत")
)
val ALL = Sj("ALL", "All Subjects", "सभी विषय")

class Q(val id: String, val subj: String, val topic: String, val q: String, val o: List<String>,
        val ans: Int, val expl: String, val diff: String, val src: String)
class T(val id: Int, val ts: Long, val kind: String, val total: Int, val c: Int, val w: Int, val s: Int)

class Bool(val sp: SharedPreferences, val k: String, d: Boolean) {
    var v by mutableStateOf(sp.getBoolean(k, d))
    fun set(x: Boolean) { v = x; sp.edit().putBoolean(k, x).apply() }
}

class Settings(ctx: Context) {
    val sp: SharedPreferences = ctx.getSharedPreferences("bstc", 0)
    val dark = Bool(sp, "dark", false)
    val sound = Bool(sp, "sound", true)
    val vib = Bool(sp, "vib", true)
    val hindi = Bool(sp, "hi", false)
    init { L.hi.value = hindi.v }
    fun setHindi(x: Boolean) { hindi.set(x); L.hi.value = x }
    fun resetAll() {
        sp.edit().clear().apply()
        dark.v = false; sound.v = true; vib.v = true; hindi.v = false; L.hi.value = false
    }
}

class Db(private val ctx: Context) : SQLiteOpenHelper(ctx, "bstc.db", null, 1) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE q(id TEXT PRIMARY KEY,subj TEXT,topic TEXT,q TEXT,a TEXT,b TEXT,c TEXT,d TEXT,ans INTEGER,expl TEXT,diff TEXT,src TEXT,year TEXT,shift TEXT,qno TEXT)")
        d.execSQL("CREATE TABLE tests(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,kind TEXT,total INTEGER,correct INTEGER,wrong INTEGER,skipped INTEGER)")
        val arr = JSONArray(ctx.assets.open("questions.json").bufferedReader().use { it.readText() })
        for (i in 0 until arr.length()) {
            val j = arr.getJSONObject(i); val o = j.getJSONArray("o")
            d.execSQL("INSERT INTO q VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(
                j.getString("id"), j.getString("subj"), j.getString("topic"), j.getString("q"),
                o.getString(0), o.getString(1), o.getString(2), o.getString(3), j.getInt("ans"),
                j.getString("expl"), j.getString("diff"), j.getString("src"),
                j.optString("year"), j.optString("shift"), j.optString("qno")))
        }
    }
    override fun onUpgrade(d: SQLiteDatabase, a: Int, b: Int) {}

    fun qs(where: String?, args: Array<String>?, limit: Int): List<Q> {
        val c = readableDatabase.rawQuery(
            "SELECT id,subj,topic,q,a,b,c,d,ans,expl,diff,src FROM q " +
                (if (where != null) "WHERE $where " else "") + "ORDER BY RANDOM() LIMIT $limit", args)
        val r = ArrayList<Q>()
        while (c.moveToNext()) r.add(Q(c.getString(0), c.getString(1), c.getString(2), c.getString(3),
            listOf(c.getString(4), c.getString(5), c.getString(6), c.getString(7)),
            c.getInt(8), c.getString(9), c.getString(10), c.getString(11)))
        c.close(); return r
    }
    fun pick(code: String, topic: String?, n: Int): List<Q> {
        val w = ArrayList<String>(); val a = ArrayList<String>()
        if (code != "ALL") { w.add("subj=?"); a.add(code) }
        if (topic != null) { w.add("topic=?"); a.add(topic) }
        return qs(if (w.isEmpty()) null else w.joinToString(" AND "), if (a.isEmpty()) null else a.toTypedArray(), n)
    }
    fun byIds(ids: List<String>): List<Q> = ids.mapNotNull { qs("id=?", arrayOf(it), 1).firstOrNull() }
    fun count(code: String): Int {
        val c = if (code == "ALL") readableDatabase.rawQuery("SELECT COUNT(*) FROM q", null)
        else readableDatabase.rawQuery("SELECT COUNT(*) FROM q WHERE subj=?", arrayOf(code))
        c.moveToFirst(); val n = c.getInt(0); c.close(); return n
    }
    fun topics(code: String): List<String> {
        val c = if (code == "ALL") readableDatabase.rawQuery("SELECT DISTINCT topic FROM q ORDER BY topic", null)
        else readableDatabase.rawQuery("SELECT DISTINCT topic FROM q WHERE subj=? ORDER BY topic", arrayOf(code))
        val r = ArrayList<String>(); while (c.moveToNext()) r.add(c.getString(0)); c.close(); return r
    }
    fun addTest(kind: String, total: Int, c: Int, w: Int, s: Int) {
        writableDatabase.execSQL("INSERT INTO tests(ts,kind,total,correct,wrong,skipped) VALUES(?,?,?,?,?,?)",
            arrayOf<Any?>(System.currentTimeMillis(), kind, total, c, w, s))
    }
    fun tests(limit: Int): List<T> {
        val c = readableDatabase.rawQuery("SELECT id,ts,kind,total,correct,wrong,skipped FROM tests ORDER BY id DESC LIMIT $limit", null)
        val r = ArrayList<T>()
        while (c.moveToNext()) r.add(T(c.getInt(0), c.getLong(1), c.getString(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6)))
        c.close(); return r
    }
    fun clearTests() { writableDatabase.execSQL("DELETE FROM tests") }
}

private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }

fun feedback(c: Context, st: Settings) {
    if (st.sound.v) try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 60) } catch (e: Exception) {}
    if (st.vib.v) try {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            c.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") (c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(30)
    } catch (e: Exception) {}
}
