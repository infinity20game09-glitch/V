package com.v.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.abs

class SettingsActivity : Activity() {
    private val CYAN = Color.rgb(0, 229, 255)
    private lateinit var box: LinearLayout
    private var d = 1f

    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var voiceSpinner: Spinner
    private var voiceNames = ArrayList<String>()

    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var dash: TextView
    private lateinit var btnVision: Button
    private lateinit var btnDisconnect: Button
    private lateinit var wakeEdit: EditText

    private class Perm(val key: String, val label: String, val perms: Array<String>)
    private val permList = ArrayList<Perm>()
    private val switches = HashMap<String, Switch>()
    private var pendingKey: String? = null

    private lateinit var detector: GestureDetector
    private val seeks = ArrayList<SeekBar>()
    private var ignoreGesture = false

    private fun dp(n: Int) = (n * d).toInt()

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) ignoreGesture = onSeek(ev.rawX.toInt(), ev.rawY.toInt())
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun onSeek(x: Int, y: Int): Boolean {
        val r = Rect()
        for (s in seeks) if (s.getGlobalVisibleRect(r) && r.contains(x, y)) return true
        return false
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        d = resources.displayMetrics.density
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.BLACK)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(18), dp(40), dp(18), dp(40))
        scroll.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || ignoreGesture) return false
                val w = resources.displayMetrics.widthPixels
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (e1.x > w * 0.05f && e1.x < w * 0.60f && dx > w * 0.25f &&
                    abs(dx) > abs(dy) * 1.5f && vx > 800f
                ) { finish(); return true }
                return false
            }
        })

        header("PARAMÈTRES")
        buildVoiceSection()
        buildModelSection()
        buildPermissionSection()
        refreshModelUi()
    }

    // ---------- helpers UI ----------
    private fun header(t: String) {
        val v = TextView(this)
        v.text = t
        v.setTextColor(CYAN)
        v.textSize = 22f
        v.letterSpacing = 0.2f
        v.setPadding(0, dp(10), 0, dp(14))
        box.addView(v)
    }

    private fun section(t: String) {
        val v = TextView(this)
        v.text = t
        v.setTextColor(CYAN)
        v.textSize = 15f
        v.letterSpacing = 0.15f
        v.setPadding(0, dp(26), 0, dp(8))
        box.addView(v)
        val line = View(this)
        line.setBackgroundColor(Color.argb(90, 0, 229, 255))
        box.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))
    }

    private fun label(t: String): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextColor(Color.WHITE)
        v.textSize = 14f
        v.setPadding(0, dp(12), 0, dp(4))
        box.addView(v)
        return v
    }

    private fun button(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.setTextColor(CYAN)
        val g = GradientDrawable()
        g.setColor(Color.argb(40, 0, 229, 255))
        g.setStroke(dp(1), CYAN)
        g.cornerRadius = dp(10).toFloat()
        b.background = g
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(10)
        box.addView(b, lp)
        return b
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(Color.argb(35, 0, 229, 255))
        g.setStroke(dp(1), CYAN)
        g.cornerRadius = dp(12).toFloat()
        l.background = g
        l.setPadding(dp(14), dp(12), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(12)
        box.addView(l, lp)
        return l
    }

    // ---------- Voix ----------
    private fun buildVoiceSection() {
        section("VOIX")
        label("Langue")
        val langs = arrayOf("Français", "English")
        val ls = Spinner(this)
        ls.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs)
        ls.setSelection(if (Core.lang == "en") 1 else 0)
        ls.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val nl = if (pos == 1) "en" else "fr"
                if (nl != Core.lang) { Core.lang = nl; Core.voiceName = null; fillVoices() }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        box.addView(ls)

        label("Voix (échantillon de synthèse)")
        voiceSpinner = Spinner(this)
        box.addView(voiceSpinner)

        label("Vitesse")
        box.addView(seek((Core.ttsRate * 100).toInt()) { Core.ttsRate = it / 100f })
        label("Hauteur")
        box.addView(seek((Core.ttsPitch * 100).toInt()) { Core.ttsPitch = it / 100f })

        button("Tester la voix") { speakTest() }

        ttsEngine = TextToSpeech(this, TextToSpeech.OnInitListener { st ->
            if (st == TextToSpeech.SUCCESS) { ttsReady = true; fillVoices() }
        })
    }

    private fun seek(start: Int, onChange: (Int) -> Unit): SeekBar {
        val s = SeekBar(this)
        seeks.add(s)
        s.max = 200
        s.progress = start.coerceIn(50, 200)
        s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) { onChange(maxOf(50, sb?.progress ?: 100)) }
        })
        return s
    }

    private fun fillVoices() {
        val t = ttsEngine ?: return
        if (!ttsReady) return
        val lang = Core.lang
        val list = try {
            t.voices?.filter { it.locale.language == lang && !it.isNetworkConnectionRequired }?.sortedBy { it.name } ?: emptyList()
        } catch (e: Throwable) { emptyList() }
        voiceNames = ArrayList(list.map { it.name })
        val shown = if (voiceNames.isEmpty()) listOf("(voix par défaut du téléphone)") else voiceNames.toList()
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, shown)
        val cur = voiceNames.indexOf(Core.voiceName)
        if (cur >= 0) voiceSpinner.setSelection(cur)
        voiceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (voiceNames.isNotEmpty() && pos < voiceNames.size) Core.voiceName = voiceNames[pos]
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun speakTest() {
        val t = ttsEngine ?: return
        if (!ttsReady) return
        t.language = if (Core.lang == "en") Locale.US else Locale.FRANCE
        Core.voiceName?.let { vn -> t.voices?.firstOrNull { it.name == vn }?.let { t.voice = it } }
        t.setSpeechRate(Core.ttsRate)
        t.setPitch(Core.ttsPitch)
        t.speak(
            if (Core.lang == "en") "Hello, I am V. Voice test." else "Bonjour, je suis V. Test de la voix.",
            TextToSpeech.QUEUE_FLUSH, null, "test"
        )
    }

    // ---------- Modèle ----------
    private fun buildModelSection() {
        section("MODÈLE D'IA")
        button("Ajouter un modèle (GGUF)") { pickFile(10) }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progress.max = 100
        progress.visibility = View.GONE
        box.addView(progress)
        progressText = TextView(this)
        progressText.setTextColor(CYAN)
        progressText.visibility = View.GONE
        box.addView(progressText)

        val c = card()
        dash = TextView(this)
        dash.setTextColor(Color.WHITE)
        dash.textSize = 13.5f
        dash.setLineSpacing(0f, 1.25f)
        c.addView(dash)

        label("Nom d'appel (par défaut : premier mot du nom du fichier)")
        wakeEdit = EditText(this)
        wakeEdit.setTextColor(Color.WHITE)
        wakeEdit.setText(Core.wakeOverride ?: "")
        wakeEdit.hint = Wake.wakeName()
        wakeEdit.setHintTextColor(Color.GRAY)
        wakeEdit.isSingleLine = true
        box.addView(wakeEdit)
        button("Enregistrer le nom d'appel") {
            val v = wakeEdit.text.toString().trim()
            Core.wakeOverride = if (v.isEmpty()) null else v
            refreshModelUi()
            Toast.makeText(this, "Nom d'appel : " + Wake.wakeName(), Toast.LENGTH_SHORT).show()
        }

        btnVision = button("Ajouter un modèle de vision") { pickFile(11) }
        btnDisconnect = button("Déconnecter le modèle") {
            AlertDialog.Builder(this)
                .setMessage("Déconnecter et supprimer le modèle ?")
                .setPositiveButton("Oui") { _, _ -> ModelManager.disconnect(); refreshModelUi() }
                .setNegativeButton("Non", null)
                .show()
        }
    }

    private fun pickFile(code: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, code)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        progress.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progress.progress = 0
        val onProg: (Int, String) -> Unit = { p, msg ->
            if (!isFinishing) { progress.progress = p; progressText.text = "$msg  $p %" }
        }
        if (requestCode == 10) {
            ModelManager.importModel(uri, onProg) { ok, err ->
                if (isFinishing) return@importModel
                if (!ok) {
                    progress.visibility = View.GONE
                    progressText.text = err
                    Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                } else {
                    progressText.text = "Modèle connecté  100 %"
                    refreshModelUi()
                    val inf = ModelManager.info
                    if (inf != null && !inf.multimodal && !Core.visionAsked) {
                        Core.visionAsked = true
                        AlertDialog.Builder(this)
                            .setTitle("Modèle sans vision")
                            .setMessage("Ce modèle ne comprend pas les images. Ajouter un modèle de vision ? (cette question ne sera posée qu'une fois)")
                            .setPositiveButton("Ajouter") { _, _ -> pickFile(11) }
                            .setNegativeButton("Non, laisser ainsi", null)
                            .show()
                    }
                }
            }
        } else if (requestCode == 11) {
            ModelManager.importVision(uri, onProg) { ok, err ->
                if (isFinishing) return@importVision
                if (ok) { progressText.text = "Modèle de vision ajouté  100 %"; refreshModelUi() }
                else Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fmtSize(b: Long): String =
        if (b >= 1_000_000_000L) String.format(Locale.US, "%.2f Go", b / 1e9) else String.format(Locale.US, "%.0f Mo", b / 1e6)

    private fun refreshModelUi() {
        wakeEdit.hint = Wake.wakeName()
        val connected = Core.modelPath != null
        btnDisconnect.visibility = if (connected) View.VISIBLE else View.GONE
        if (!connected) {
            dash.text = "◌ AUCUN MODÈLE CONNECTÉ\n\nAjoute un fichier GGUF avec le bouton ci-dessus."
            btnVision.visibility = View.GONE
            return
        }
        dash.text = "● CHARGEMENT DES INFORMATIONS…"
        Thread {
            val inf = ModelManager.info ?: Gguf.read(java.io.File(Core.modelPath!!)).also { ModelManager.info = it }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (inf == null) { dash.text = "● MODÈLE PRÉSENT (informations illisibles)"; return@runOnUiThread }
                val sb = StringBuilder()
                sb.append(if (Core.modelReady) "● MODÈLE CONNECTÉ\n\n" else "◐ MODÈLE ENREGISTRÉ (non chargé)\n\n")
                sb.append("Nom : ").append(if (inf.name.isNotBlank()) inf.name else Core.modelName).append('\n')
                sb.append("Fichier : ").append(Core.modelName).append('\n')
                sb.append("Poids : ").append(fmtSize(inf.fileBytes)).append('\n')
                if (inf.sizeLabel.isNotBlank()) sb.append("Paramètres : ").append(inf.sizeLabel).append('\n')
                sb.append("Architecture : ").append(inf.arch).append('\n')
                sb.append("Quantification : ").append(inf.quant).append('\n')
                if (inf.ctxLen > 0) sb.append("Contexte max : ").append(inf.ctxLen).append(" tokens\n")
                sb.append("Multimodal : ").append(if (inf.multimodal) "OUI" else "NON").append('\n')
                if (!inf.multimodal) sb.append("Modèle de vision : ").append(if (Core.visionPath != null) "ajouté" else "aucun").append('\n')
                sb.append("Nom d'appel : « ").append(Wake.wakeName()).append(" »")
                dash.text = sb.toString()
                btnVision.visibility = if (!inf.multimodal) View.VISIBLE else View.GONE
            }
        }.start()
    }

    // ---------- Autorisations ----------
    private fun buildPermissionSection() {
        section("AUTORISATIONS DE L'IA")
        val media: Array<String> = if (Build.VERSION.SDK_INT >= 33)
            arrayOf("android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO")
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

        permList.add(Perm("mic", "Microphone", arrayOf(Manifest.permission.RECORD_AUDIO)))
        permList.add(Perm("camera", "Caméra", arrayOf(Manifest.permission.CAMERA)))
        permList.add(Perm("location", "Localisation", arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
        permList.add(Perm("contacts", "Contacts", arrayOf(Manifest.permission.READ_CONTACTS)))
        permList.add(Perm("calendar", "Calendrier", arrayOf(Manifest.permission.READ_CALENDAR)))
        permList.add(Perm("phone", "Téléphone (appels)", arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE)))
        permList.add(Perm("sms", "SMS", arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS)))
        permList.add(Perm("media", "Photos, vidéos et fichiers audio", media))
        if (Build.VERSION.SDK_INT >= 31)
            permList.add(Perm("bluetooth", "Bluetooth", arrayOf("android.permission.BLUETOOTH_CONNECT")))
        if (Build.VERSION.SDK_INT >= 33)
            permList.add(Perm("notif", "Notifications", arrayOf("android.permission.POST_NOTIFICATIONS")))

        for (p in permList) addSwitch(p.key, p.label) { on ->
            if (on) {
                if (!granted(p)) { pendingKey = p.key; requestPermissions(p.perms, 100) }
                else Core.setAiAllow(p.key, true)
            } else Core.setAiAllow(p.key, false)
        }

        addSwitch("overlay", "Affichage par-dessus les autres applis") { on ->
            if (on && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            Core.setAiAllow("overlay", on)
        }
        addSwitch("screen", "Partage d'écran (demandé à chaque ouverture)") { on -> Core.screenShare = on }
        refreshSwitches()
    }

    private fun granted(p: Perm) = p.perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun addSwitch(key: String, text: String, onToggle: (Boolean) -> Unit) {
        val s = Switch(this)
        s.text = text
        s.setTextColor(Color.WHITE)
        s.setPadding(0, dp(10), 0, dp(10))
        s.setOnCheckedChangeListener { btn, checked -> if (btn.isPressed) onToggle(checked) }
        box.addView(s)
        switches[key] = s
    }

    private fun refreshSwitches() {
        for (p in permList) switches[p.key]?.isChecked = granted(p) && Core.aiAllow(p.key)
        switches["overlay"]?.isChecked = Settings.canDrawOverlays(this) && Core.aiAllow("overlay")
        switches["screen"]?.isChecked = Core.screenShare
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val k = pendingKey ?: return
        pendingKey = null
        Core.setAiAllow(k, grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
        refreshSwitches()
    }

    override fun onResume() {
        super.onResume()
        if (switches.isNotEmpty()) refreshSwitches()
    }

    override fun onDestroy() {
        ttsEngine?.shutdown()
        super.onDestroy()
    }
}
