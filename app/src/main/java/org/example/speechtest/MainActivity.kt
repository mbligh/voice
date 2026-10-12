package org.example.speechtest

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.ArrayAdapter
import java.util.Locale
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var result: TextView
    private lateinit var history: TextView
    private lateinit var startOffline: Button
    private lateinit var startDefault: Button
    private lateinit var stop: Button
    private var recognizer: SpeechRecognizer? = null
    private var started = 0L
    private var mode = ""
    private var locale = "en-GB"
    private val records = ArrayDeque<String>()
    private val permissionCode = 42
    private var tts: TextToSpeech? = null
    private lateinit var voiceSpinner: Spinner
    private lateinit var ttsStatus: TextView
    private lateinit var ttsText: EditText
    private var ttsVoices: List<android.speech.tts.Voice> = emptyList()
    private var speechRate = 1.0f
    private var speechPitch = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        fun label(s: String, size: Float = 17f): TextView = TextView(this).apply {
            text = s
            textSize = size
            setPadding(0, 10, 0, 10)
            root.addView(this)
        }
        label("Speech Recognition Test", 24f)
        label("Choose recognition language:")
        val languages = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            root.addView(this)
        }
        val gb = RadioButton(this).apply {
            text = "English (UK)"
            id = View.generateViewId()
            isChecked = true
        }
        val us = RadioButton(this).apply {
            text = "English (US)"
            id = View.generateViewId()
        }
        languages.addView(gb)
        languages.addView(us)
        languages.setOnCheckedChangeListener { _, checked ->
            locale = if (checked == gb.id) "en-GB" else "en-US"
        }
        label("On-device available: ${SpeechRecognizer.isOnDeviceRecognitionAvailable(this)}", 14f)
        startOffline = Button(this).apply {
            text = "Start on-device"
            root.addView(this)
            setOnClickListener { begin(true) }
        }
        startDefault = Button(this).apply {
            text = "Start default"
            root.addView(this)
            setOnClickListener { begin(false) }
        }
        stop = Button(this).apply {
            text = "Stop listening"
            root.addView(this)
            isEnabled = false
            setOnClickListener { recognizer?.stopListening() }
        }
        status = label("Ready", 15f)
        result = label("Transcription will appear here", 19f)
        history = label("History (current app session):", 14f)
        label("Text-to-speech (offline voices only)", 22f)
        ttsStatus = label("Initializing text-to-speech...", 14f)
        voiceSpinner = Spinner(this).apply { root.addView(this) }
        ttsText = EditText(this).apply {
            setSingleLine(false)
            minLines = 2
            setText("Hello. This is a test of British English text to speech on this phone.")
            root.addView(this)
        }
        fun control(title: String, initial: Int, changed: (Float) -> Unit) {
            val display = label("$title: 1.00", 14f)
            val slider = SeekBar(this).apply {
                max = 180
                progress = initial
                root.addView(this)
            }
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = (progress + 20) / 100f
                    display.text = "$title: " + String.format(Locale.US, "%.2f", value)
                    changed(value)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        control("Speed", 80) { speechRate = it }
        control("Pitch", 80) { speechPitch = it }
        Button(this).apply {
            text = "Speak (offline voice)"
            root.addView(this)
            setOnClickListener { speakTest() }
        }
        Button(this).apply {
            text = "Stop speech"
            root.addView(this)
            setOnClickListener { tts?.stop() }
        }
        initTts()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { result ->
            if (result != TextToSpeech.SUCCESS) {
                ttsStatus.text = "Text-to-speech initialization failed ($result)"
            } else {
                val engine = tts ?: return@TextToSpeech
                val voices = engine.voices ?: emptySet()
                ttsVoices = voices.filter {
                    it.locale.language == "en" && !it.isNetworkConnectionRequired
                }.sortedWith(compareBy({ it.locale.toLanguageTag() != "en-GB" }, { it.name }))
                val names = ttsVoices.map {
                    "${it.locale.toLanguageTag()} — ${it.name} (offline)"
                }
                voiceSpinner.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    if (names.isEmpty()) listOf("No offline English voices installed") else names
                )
                ttsStatus.text = "Engine: ${engine.defaultEngine}; ${ttsVoices.size} offline English voices. Only voices marked as not requiring a network are shown."
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        runOnUiThread { ttsStatus.text = "Speaking..." }
                    }
                    override fun onDone(utteranceId: String?) {
                        runOnUiThread { ttsStatus.text = "Speech completed (offline-capable voice)" }
                    }
                    override fun onError(utteranceId: String?) {
                        runOnUiThread { ttsStatus.text = "Speech synthesis failed" }
                    }
                })
            }
        }
    }

    private fun speakTest() {
        val engine = tts ?: return
        val voice = ttsVoices.getOrNull(voiceSpinner.selectedItemPosition)
        if (voice == null) {
            ttsStatus.text = "No offline English voice available"
            return
        }
        val selected = engine.setVoice(voice)
        if (selected != TextToSpeech.SUCCESS) {
            ttsStatus.text = "Could not select offline voice"
            return
        }
        engine.setSpeechRate(speechRate)
        engine.setPitch(speechPitch)
        val code = engine.speak(ttsText.text.toString(), TextToSpeech.QUEUE_FLUSH, null, "voice-test")
        if (code != TextToSpeech.SUCCESS) ttsStatus.text = "Failed to queue speech"
    }

    private fun begin(onDevice: Boolean) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), permissionCode)
            status.text = "Microphone permission required. Tap start again after granting."
            return
        }
        if (onDevice && !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "No on-device recognition service available"
            return
        }
        recognizer?.destroy()
        recognizer = null
        mode = if (onDevice) "On-device" else "Default"
        val chosenLocale = locale
        try {
            val sr = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                     else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    status.text = "$mode / $chosenLocale: listening"
                }
                override fun onBeginningOfSpeech() {
                    status.text = "$mode / $chosenLocale: speech detected"
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    status.text = "$mode / $chosenLocale: processing"
                }
                override fun onError(error: Int) {
                    status.text = "$mode / $chosenLocale: error $error (${errorDescription(error)})"
                    finishRecognition()
                }
                override fun onResults(results: Bundle?) {
                    val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognized = candidates?.firstOrNull() ?: "(no transcription)"
                    val elapsed = SystemClock.elapsedRealtime() - started
                    result.text = recognized
                    records.addFirst("$mode / $chosenLocale / ${elapsed} ms: $recognized")
                    while (records.size > 20) records.removeLast()
                    history.text = "History (current app session):\n" + records.joinToString("\n\n")
                    status.text = "$mode / $chosenLocale: completed in ${elapsed} ms"
                    finishRecognition()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (partial != null) result.text = "(partial) $partial"
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, chosenLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, chosenLocale)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            started = SystemClock.elapsedRealtime()
            startOffline.isEnabled = false
            startDefault.isEnabled = false
            stop.isEnabled = true
            result.text = "Listening..."
            sr.startListening(intent)
        } catch (e: Exception) {
            status.text = "Failed to start: ${e.message}"
            finishRecognition()
        }
    }

    private fun finishRecognition() {
        startOffline.isEnabled = true
        startDefault.isEnabled = true
        stop.isEnabled = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun errorDescription(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network timeout"
        SpeechRecognizer.ERROR_NETWORK -> "network error"
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_SERVER -> "server error"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no speech"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "permission denied"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "too many requests"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "server disconnected"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language model unavailable"
        else -> "unknown"
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
