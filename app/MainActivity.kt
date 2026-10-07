package com.bstc.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Scr {
    object Tabs : Scr()
    class Quiz(val title: String, val qs: List<Q>, val mock: Boolean, val kind: String,
               val ans: List<Int>? = null, val idx: Int = 0) : Scr()
    class Pdf(val file: String, val title: String) : Scr()
    class Res(val total: Int, val c: Int, val w: Int, val s: Int, val kind: String) : Scr()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { App(this) } }
}

@Composable
fun App(c: Context) {
    val st = remember { Settings(c) }
    val db = remember { Db(c) }
    var scr by remember { mutableStateOf<Scr>(Scr.Tabs) }
    var tab by remember { mutableStateOf(0) }
    var ver by remember { mutableStateOf(0) }
    MaterialTheme(colorScheme = if (st.dark.v) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            BackHandler(scr !is Scr.Tabs) { ver++; scr = Scr.Tabs }
            when (val s = scr) {
                is Scr.Tabs -> Tabs(c, st, db, tab, { tab = it }, ver, { ver++ }, { scr = it })
                is Scr.Quiz -> QuizScreen(c, st, db, s, { ver++; scr = Scr.Tabs }, { scr = it })
                is Scr.Pdf -> PdfScreen(c, s.file, s.title) { scr = Scr.Tabs }
                is Scr.Res -> ResScreen(s) { ver++; scr = Scr.Tabs }
            }
        }
    }
}

@Composable
fun Tabs(c: Context, st: Settings, db: Db, tab: Int, setTab: (Int) -> Unit, ver: Int,
         bump: () -> Unit, go: (Scr) -> Unit) {
    val items = listOf("🏠" to t("Home", "होम"), "❓" to "MCQs", "📝" to t("Papers", "पेपर"),
        "🎯" to t("Mock", "मॉक"), "📊" to t("Results", "परिणाम"), "⚙️" to t("Settings", "सेटिंग"))
    Scaffold(bottomBar = {
        NavigationBar {
            items.forEachIndexed { i, p ->
                NavigationBarItem(selected = tab == i, onClick = { setTab(i) },
                    icon = { Text(p.first, fontSize = 20.sp) },
                    label = { Text(p.second, fontSize = 10.sp, maxLines = 1) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Home(c, st, db, ver, setTab, go)
                1 -> McqTab(db, go)
                2 -> PapersTab(go)
                3 -> MockTab(db, go)
                4 -> ResultsTab(db, ver)
                else -> SettingsTab(st, db, bump)
            }
        }
    }
}

@Composable
fun Sec(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp); content()
        }
    }
}

@Composable
fun Chips(items: List<Pair<String, String>>, sel: String, on: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (k, l) -> FilterChip(selected = sel == k, onClick = { on(k) }, label = { Text(l) }) }
    }
}

val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
fun kindName(k: String) = when (k) {
    "Full" -> t("Full Mock", "फुल मॉक"); "Subject" -> t("Subject Mock", "विषयवार मॉक")
    "Custom" -> t("Custom Mock", "कस्टम मॉक"); else -> t("Practice", "अभ्यास")
}

@Composable
fun Home(c: Context, st: Settings, db: Db, ver: Int, setTab: (Int) -> Unit, go: (Scr) -> Unit) {
    val cont = remember(ver) { st.sp.getString("cont", null) }
    val recent = remember(ver) { db.tests(3) }
    val all = remember(ver) { db.tests(100000) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BSTC", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("आपकी BSTC तैयारी, एक ही जगह।")
        Sec(t("Quick Start", "क्विक स्टार्ट")) {
            Button({ go(Scr.Quiz(t("Quick Start", "क्विक स्टार्ट"), db.pick("ALL", null, 20), false, "Practice")) },
                Modifier.fillMaxWidth()) { Text(t("20 mixed questions", "20 मिश्रित प्रश्न")) }
        }
        if (cont != null) Sec(t("Continue Test", "टेस्ट जारी रखें")) {
            val j = JSONObject(cont)
            Button({
                val ids = j.getJSONArray("ids"); val a = j.getJSONArray("ans")
                go(Scr.Quiz(j.getString("title"), db.byIds(List(ids.length()) { ids.getString(it) }), true,
                    j.getString("kind"), List(a.length()) { a.getInt(it) }, j.getInt("i")))
            }, Modifier.fillMaxWidth()) { Text(j.getString("title")) }
        }
        Sec(t("Today's Practice", "आज का अभ्यास")) {
            Button({ go(Scr.Quiz(t("Today's Practice", "आज का अभ्यास"), db.pick("ALL", null, 10), false, "Practice")) },
                Modifier.fillMaxWidth()) { Text(t("10 questions", "10 प्रश्न")) }
        }
        Sec(t("Recent Tests", "हाल के टेस्ट")) {
            if (recent.isEmpty()) Text(t("No tests yet.", "अभी कोई टेस्ट नहीं।"))
            recent.forEach { Text("${kindName(it.kind)} • ${it.c}/${it.total} • ${fmt.format(Date(it.ts))}") }
        }
        Sec(t("My Progress", "मेरी प्रगति")) {
            val pa = st.sp.getInt("pa", 0); val pc = st.sp.getInt("pc", 0)
            Text(t("Practice answered: $pa (correct $pc)", "अभ्यास में उत्तर: $pa (सही $pc)"))
            Text(t("Tests taken: ${all.size}", "दिए गए टेस्ट: ${all.size}"))
        }
    }
}

@Composable
fun McqTab(db: Db, go: (Scr) -> Unit) {
    var sel by remember { mutableStateOf<Sj?>(null) }
    var n by remember { mutableStateOf("10") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val s = sel
        if (s == null) {
            Text("MCQs", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            (SJ + ALL).forEach { sj ->
                Card(Modifier.fillMaxWidth().clickable { sel = sj }) {
                    Row(Modifier.padding(16.dp)) {
                        Text(sj.name(), Modifier.weight(1f), fontSize = 17.sp)
                        Text("${db.count(sj.c)}")
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ sel = null }) { Text("←") }
                Text(s.name(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(t("Questions per round", "प्रति राउंड प्रश्न"))
            Chips(listOf("10" to "10", "20" to "20", "50" to "50"), n) { n = it }
            Button({ go(Scr.Quiz(s.name(), db.pick(s.c, null, n.toInt()), false, "Practice")) },
                Modifier.fillMaxWidth()) { Text(t("All topics (mixed)", "सभी टॉपिक (मिश्रित)")) }
            if (s.c != "ALL") db.topics(s.c).forEach { tp ->
                OutlinedButton({ go(Scr.Quiz("${s.name()} • $tp", db.pick(s.c, tp, n.toInt()), false, "Practice")) },
                    Modifier.fillMaxWidth()) { Text(tp) }
            }
        }
    }
}

@Composable
fun PapersTab(go: (Scr) -> Unit) {
    val p = listOf("paper_2020.pdf" to "2020", "paper_2021.pdf" to "2021", "paper_2022.pdf" to "2022",
        "paper_2023.pdf" to "2023", "paper_2024.pdf" to "2024", "paper_2025_s1.pdf" to "2025 — 1st Shift",
        "paper_2025_s2.pdf" to "2025 — 2nd Shift", "syllabus.pdf" to t("Syllabus 2026-27", "पाठ्यक्रम 2026-27"))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Old Papers", "पुराने पेपर"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        p.forEach { (f, n) ->
            Card(Modifier.fillMaxWidth().clickable { go(Scr.Pdf(f, n)) }) {
                Text("📄  $n", Modifier.padding(18.dp), fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun MockTab(db: Db, go: (Scr) -> Unit) {
    var lang by remember { mutableStateOf("HI") }
    var sub by remember { mutableStateOf("MA") }
    var cs by remember { mutableStateOf("ALL") }
    var cn by remember { mutableStateOf("25") }
    val subs = SJ.map { it.c to it.name() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("Mock Tests", "मॉक टेस्ट"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Sec(t("Full Mock Test", "फुल मॉक टेस्ट")) {
            Text(t("Pattern: GK 50, Mental Ability 50, Teaching Aptitude 50, English 20, Language 30. 3 marks each, no negative marking. Fewer questions are used if the bank has fewer.",
                "पैटर्न: GK 50, मानसिक योग्यता 50, शिक्षण अभिक्षमता 50, अंग्रेज़ी 20, भाषा 30। प्रत्येक 3 अंक, नकारात्मक अंकन नहीं। बैंक में कम प्रश्न होने पर उतने ही लिए जाते हैं।"), fontSize = 13.sp)
            Chips(listOf("HI" to t("Hindi", "हिन्दी"), "SA" to t("Sanskrit", "संस्कृत")), lang) { lang = it }
            Button({
                val l = ArrayList<Q>()
                listOf("GK" to 50, "MA" to 50, "TA" to 50, "EN" to 20, lang to 30).forEach { (k, q) -> l.addAll(db.pick(k, null, q)) }
                go(Scr.Quiz(t("Full Mock", "फुल मॉक"), l, true, "Full"))
            }, Modifier.fillMaxWidth()) { Text(t("Start", "शुरू करें")) }
        }
        Sec(t("Subject-wise Mock", "विषयवार मॉक")) {
            Chips(subs, sub) { sub = it }
            Button({
                val q = when (sub) { "EN" -> 20; "HI", "SA" -> 30; else -> 50 }
                go(Scr.Quiz(SJ.first { it.c == sub }.name(), db.pick(sub, null, q), true, "Subject"))
            }, Modifier.fillMaxWidth()) { Text(t("Start", "शुरू करें")) }
        }
        Sec(t("Custom Mock", "कस्टम मॉक")) {
            Chips(subs + ("ALL" to ALL.name()), cs) { cs = it }
            Chips(listOf("10", "25", "50", "100").map { it to it }, cn) { cn = it }
            Button({ go(Scr.Quiz(t("Custom Mock", "कस्टम मॉक"), db.pick(cs, null, cn.toInt()), true, "Custom")) },
                Modifier.fillMaxWidth()) { Text(t("Start", "शुरू करें")) }
        }
    }
}

@Composable
fun ResultsTab(db: Db, ver: Int) {
    val all = remember(ver) { db.tests(100000) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Results", "परिणाम"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val best = all.maxByOrNull { if (it.total == 0) 0f else it.c.toFloat() / it.total }
        Sec(t("Overall Performance", "कुल प्रदर्शन")) {
            Text(t("Tests: ${all.size}", "टेस्ट: ${all.size}"))
            Text(t("Correct: ${all.sumOf { it.c }}", "सही: ${all.sumOf { it.c }}"))
            Text(t("Wrong: ${all.sumOf { it.w }}", "गलत: ${all.sumOf { it.w }}"))
            Text(t("Unattempted: ${all.sumOf { it.s }}", "अनुत्तरित: ${all.sumOf { it.s }}"))
            Text(t("Best score: ", "सर्वश्रेष्ठ स्कोर: ") + (if (best == null) "—" else "${best.c * 3}/${best.total * 3}"))
        }
        Text(t("Test History", "टेस्ट इतिहास"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (all.isEmpty()) Text(t("No tests yet.", "अभी कोई टेस्ट नहीं।"))
        all.forEach {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${kindName(it.kind)} • ${it.c * 3}/${it.total * 3}", fontWeight = FontWeight.Bold)
                    Text("✔ ${it.c}  ✘ ${it.w}  — ${it.s}   ${fmt.format(Date(it.ts))}", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsTab(st: Settings, db: Db, bump: () -> Unit) {
    var dlg by remember { mutableStateOf(0) }
    @Composable fun Sw(l: String, b: Bool, on: (Boolean) -> Unit = { b.set(it) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(l, Modifier.weight(1f)); Switch(b.v, on)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(t("Settings", "सेटिंग"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Sw(t("Dark mode", "डार्क मोड"), st.dark)
        Sw(t("Sound", "ध्वनि"), st.sound)
        Sw(t("Vibration", "कंपन"), st.vib)
        Sw(t("Hindi interface", "हिन्दी इंटरफ़ेस"), st.hindi) { st.setHindi(it) }
        OutlinedButton({ dlg = 1 }, Modifier.fillMaxWidth()) { Text(t("Clear Test History", "टेस्ट इतिहास हटाएँ")) }
        OutlinedButton({ dlg = 2 }, Modifier.fillMaxWidth()) { Text(t("Reset App Data", "ऐप डेटा रीसेट करें")) }
        OutlinedButton({ dlg = 3 }, Modifier.fillMaxWidth()) { Text(t("About App", "ऐप के बारे में")) }
        OutlinedButton({ dlg = 4 }, Modifier.fillMaxWidth()) { Text(t("Privacy / Disclaimer", "गोपनीयता / अस्वीकरण")) }
    }
    if (dlg != 0) AlertDialog(onDismissRequest = { dlg = 0 },
        title = { Text(when (dlg) { 1 -> t("Clear Test History", "टेस्ट इतिहास हटाएँ"); 2 -> t("Reset App Data", "ऐप डेटा रीसेट करें")
            3 -> t("About App", "ऐप के बारे में"); else -> t("Privacy / Disclaimer", "गोपनीयता / अस्वीकरण") }) },
        text = { Text(when (dlg) {
            1 -> t("Delete all saved test results?", "सभी सहेजे गए परिणाम हटाएँ?")
            2 -> t("Reset settings, progress and test history?", "सेटिंग, प्रगति और टेस्ट इतिहास रीसेट करें?")
            3 -> "BSTC v1.0\n" + t("Offline preparation app for Rajasthan Pre D.El.Ed. No ads, no login, no internet needed.",
                "राजस्थान प्री डी.एल.एड. की ऑफ़लाइन तैयारी का ऐप। कोई विज्ञापन/लॉगिन/इंटरनेट नहीं।")
            else -> t("Practice questions are generated for practice and are NOT official exam questions. Old papers are shown for reference; answer keys are not included. Data stays on your device. Verify syllabus with the official notice.",
                "अभ्यास प्रश्न केवल अभ्यास हेतु बनाए गए हैं, आधिकारिक प्रश्न नहीं। पुराने पेपर संदर्भ हेतु हैं; उत्तर-कुंजी शामिल नहीं। डेटा केवल आपके फ़ोन में रहता है। पाठ्यक्रम आधिकारिक अधिसूचना से मिलाएँ।") }) },
        confirmButton = { TextButton({
            if (dlg == 1) { db.clearTests(); bump() }
            if (dlg == 2) { db.clearTests(); st.resetAll(); bump() }
            dlg = 0 }) { Text("OK") } },
        dismissButton = { if (dlg <= 2) TextButton({ dlg = 0 }) { Text(t("Cancel", "रद्द करें")) } })
}

@Composable
fun QuizScreen(c: Context, st: Settings, db: Db, s: Scr.Quiz, onExit: () -> Unit, onDone: (Scr) -> Unit) {
    val qs = s.qs
    if (qs.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Button(onExit) { Text(t("No questions — Back", "प्रश्न नहीं — वापस")) } }; return }
    val ans = remember { mutableStateListOf<Int>().also { it.addAll(s.ans ?: List(qs.size) { -1 }) } }
    var i by remember { mutableStateOf(s.idx.coerceIn(0, qs.size - 1)) }
    var nav by remember { mutableStateOf(false) }
    var conf by remember { mutableStateOf(false) }
    val q = qs[i]
    LaunchedEffect(ans.joinToString(), i) {
        if (s.mock) st.sp.edit().putString("cont", JSONObject().put("ids", JSONArray(qs.map { it.id }))
            .put("ans", JSONArray(ans.toList())).put("i", i).put("title", s.title).put("kind", s.kind).toString()).apply()
    }
    fun finish() {
        val cc = qs.indices.count { ans[it] == qs[it].ans }; val sk = ans.count { it < 0 }; val w = qs.size - cc - sk
        if (qs.size - sk > 0) db.addTest(s.kind, qs.size, cc, w, sk)
        st.sp.edit().remove("cont").apply()
        onDone(Scr.Res(qs.size, cc, w, sk, s.kind))
    }
    fun pickOpt(k: Int) {
        if (!s.mock) {
            if (ans[i] >= 0) return
            ans[i] = k
            st.sp.edit().putInt("pa", st.sp.getInt("pa", 0) + 1).putInt("pc", st.sp.getInt("pc", 0) + if (k == q.ans) 1 else 0).apply()
        } else ans[i] = if (ans[i] == k) -1 else k
        feedback(c, st)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onExit) { Text("✕") }
            Text(s.title, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
            Text("${i + 1}/${qs.size}")
        }
        LinearProgressIndicator(progress = { (i + 1f) / qs.size }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${q.topic} • ${q.diff} • " + t("Practice question", "अभ्यास प्रश्न"), fontSize = 12.sp)
            Text(q.q, fontSize = 18.sp)
            val locked = !s.mock && ans[i] >= 0
            q.o.forEachIndexed { k, o ->
                val bg = when {
                    locked && k == q.ans -> Color(0xFFC8E6C9)
                    locked && k == ans[i] -> Color(0xFFFFCDD2)
                    s.mock && ans[i] == k -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val fg = if (locked && (k == q.ans || k == ans[i])) Color.Black else MaterialTheme.colorScheme.onSurface
                Card(Modifier.fillMaxWidth().clickable { pickOpt(k) },
                    colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
                    Text("${"ABCD"[k]}.  $o", Modifier.padding(16.dp), fontSize = 16.sp)
                }
            }
            if (locked) Text(t("Explanation: ", "व्याख्या: ") + q.expl, fontSize = 14.sp)
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ if (i > 0) i-- }, Modifier.weight(1f), enabled = i > 0) { Text(t("Prev", "पिछला")) }
            OutlinedButton({ nav = true }, Modifier.weight(1f)) { Text("☰") }
            if (i < qs.size - 1) Button({ i++ }, Modifier.weight(1f)) { Text(t("Next", "अगला")) }
            else Button({ if (s.mock) conf = true else finish() }, Modifier.weight(1f)) { Text(t("Submit", "जमा करें")) }
        }
    }
    if (nav) AlertDialog(onDismissRequest = { nav = false }, confirmButton = {
        TextButton({ nav = false; if (s.mock) conf = true else finish() }) { Text(t("Submit", "जमा करें")) } },
        dismissButton = { TextButton({ nav = false }) { Text(t("Close", "बंद")) } },
        title = { Text(t("Answered ${ans.count { it >= 0 }}/${qs.size}", "उत्तरित ${ans.count { it >= 0 }}/${qs.size}")) },
        text = {
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.heightIn(max = 340.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(qs) { k, _ ->
                    val done = ans[k] >= 0
                    Box(Modifier.height(40.dp).clickable { i = k; nav = false }.then(Modifier),
                        contentAlignment = Alignment.Center) {
                        Surface(color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxSize()) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${k + 1}", color = if (done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        })
    if (conf) AlertDialog(onDismissRequest = { conf = false },
        title = { Text(t("Submit test?", "टेस्ट जमा करें?")) },
        text = { Text(t("Unanswered: ${ans.count { it < 0 }}. You cannot change answers after submitting.",
            "अनुत्तरित: ${ans.count { it < 0 }}। जमा करने के बाद उत्तर नहीं बदले जा सकते।")) },
        confirmButton = { TextButton({ conf = false; finish() }) { Text(t("Submit", "जमा करें")) } },
        dismissButton = { TextButton({ conf = false }) { Text(t("Cancel", "रद्द करें")) } })
}

@Composable
fun ResScreen(r: Scr.Res, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(t("Result", "परिणाम"), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("${r.c * 3} / ${r.total * 3}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text(t("Correct ${r.c}  •  Wrong ${r.w}  •  Unattempted ${r.s}", "सही ${r.c}  •  गलत ${r.w}  •  अनुत्तरित ${r.s}"))
        Spacer(Modifier.height(20.dp))
        Button(onHome) { Text(t("Done", "ठीक है")) }
    }
}

class PdfHolder(ctx: Context, asset: String) {
    private val file = File(ctx.cacheDir, asset).also { f ->
        if (!f.exists() || f.length() == 0L) ctx.assets.open(asset).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    }
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val r = PdfRenderer(pfd)
    val pages = r.pageCount
    private val mutex = Mutex()
    suspend fun render(i: Int): Bitmap = mutex.withLock {
        withContext(Dispatchers.IO) {
            val p = r.openPage(i)
            try {
                val w = 1200; val h = (w.toFloat() * p.height / p.width).toInt()
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(AColor.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); b
            } finally { p.close() }
        }
    }
    fun close() { try { r.close(); pfd.close() } catch (e: Exception) {} }
}

@Composable
fun PdfScreen(c: Context, file: String, title: String, onBack: () -> Unit) {
    val holder = remember(file) { PdfHolder(c, file) }
    DisposableEffect(file) { onDispose { holder.close() } }
    var zoom by remember { mutableStateOf(1f) }
    val ls = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("←") }
            Text(title, Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
            Text("${ls.firstVisibleItemIndex + 1}/${holder.pages}")
            TextButton({ scope.launch { ls.animateScrollToItem((ls.firstVisibleItemIndex - 1).coerceAtLeast(0)) } }) { Text("▲") }
            TextButton({ scope.launch { ls.animateScrollToItem((ls.firstVisibleItemIndex + 1).coerceAtMost(holder.pages - 1)) } }) { Text("▼") }
            TextButton({ zoom = (zoom - 0.5f).coerceAtLeast(1f) }) { Text("−") }
            TextButton({ zoom = (zoom + 0.5f).coerceAtMost(3f) }) { Text("+") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val w = maxWidth
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                LazyColumn(Modifier.width(w * zoom).fillMaxHeight(), state = ls) {
                    items(holder.pages) { p -> PageImg(holder, p) }
                }
            }
        }
    }
}

@Composable
fun PageImg(h: PdfHolder, i: Int) {
    val bmp by produceState<Bitmap?>(null, i) { value = try { h.render(i) } catch (e: Exception) { null } }
    val b = bmp
    if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxWidth().padding(bottom = 4.dp), contentScale = ContentScale.FillWidth)
    else Box(Modifier.fillMaxWidth().height(500.dp), Alignment.Center) { CircularProgressIndicator() }
}
