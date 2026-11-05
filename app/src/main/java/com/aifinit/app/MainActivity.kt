package com.aifinit.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

private val bg = Color(0xFF0B0B12)
private val panel = Color(0xFF171722)
private val purple = Color(0xFF9B6DFF)
private val muted = Color(0xFFAAA6B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("aifinit", MODE_PRIVATE)
        setContent { FinitApp(prefs) }
    }
}

data class Message(val text: String, val user: Boolean)

private val personalities = listOf(
    "Equilibrada", "Amiga", "Romântica", "Direta", "Criativa"
)

@Composable
fun FinitApp(prefs: android.content.SharedPreferences) {
    var accepted by remember { mutableStateOf(prefs.getBoolean("adult", false)) }
    var settings by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "") ?: "") }
    var key by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var personality by remember { mutableStateOf(prefs.getString("personality", "Equilibrada") ?: "Equilibrada") }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val messages = remember { mutableStateListOf<Message>() }
    val scope = rememberCoroutineScope()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engineLanguage(tts)
            }
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    LaunchedEffect(Unit) {
        try {
            val array = JSONArray(prefs.getString("history", "[]"))
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                messages.add(Message(item.getString("text"), item.getBoolean("user")))
            }
        } catch (_: Exception) {}
    }

    fun saveHistory() {
        val array = JSONArray()
        messages.takeLast(80).forEach {
            array.put(JSONObject().put("text", it.text).put("user", it.user))
        }
        prefs.edit().putString("history", array.toString()).apply()
    }

    val speech = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )?.firstOrNull()
            if (!spoken.isNullOrBlank()) draft = spoken
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = purple, background = bg, surface = panel)) {
        if (!accepted) {
            Column(
                Modifier.fillMaxSize().background(bg).padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("AI FINIT", color = purple, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text("Confirmação de idade", color = Color.White, fontSize = 23.sp)
                Text(
                    "Este aplicativo é destinado a adultos. Ao continuar, você declara ter 18 anos ou mais. Temas maduros devem envolver adultos e consentimento.",
                    color = muted
                )
                var checked by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked, { checked = it })
                    Text("Tenho 18 anos ou mais", color = Color.White)
                }
                Button(
                    onClick = {
                        prefs.edit().putBoolean("adult", true).apply()
                        accepted = true
                    },
                    enabled = checked
                ) { Text("Entrar") }
            }
        } else if (settings) {
            Column(
                Modifier.fillMaxSize().background(bg).padding(18.dp)
            ) {
                TextButton(onClick = { settings = false }) {
                    Text("← Voltar")
                }
                Text("Configurações", color = Color.White, fontSize = 24.sp)
                Text("Personalidade", color = Color.White)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(personality)
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        personalities.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = { personality = it; expanded = false }
                            )
                        }
                    }
                }
                SettingField("Endpoint HTTPS", endpoint, { endpoint = it })
                SettingField("Modelo", model, { model = it })
                SettingField("Chave API", key, { key = it })
                Button(onClick = {
                    prefs.edit()
                        .putString("endpoint", endpoint)
                        .putString("model", model)
                        .putString("key", key)
                        .putString("personality", personality)
                        .apply()
                    settings = false
                }) { Text("Salvar configurações") }
            }
        } else {
            Column(Modifier.fillMaxSize().background(bg)) {
                Row(
                    Modifier.fillMaxWidth().background(panel).padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("AI FINIT", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("VERSÃO 2.0 • $personality", color = muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { settings = true }) {
                        Icon(Icons.Default.Settings, "Configurações", tint = purple)
                    }
                    IconButton(onClick = {
                        messages.clear()
                        saveHistory()
                    }) {
                        Icon(Icons.Default.Delete, "Limpar", tint = purple)
                    }
                }

                if (messages.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().padding(25.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✦", color = purple, fontSize = 55.sp)
                        Text("Olá! Eu sou a Finit.", color = Color.White, fontSize = 24.sp)
                        Text("Sua assistente pessoal.", color = muted)
                        Button(onClick = { settings = true }) {
                            Text("Configurar IA")
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { msg ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (msg.user) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    color = if (msg.user) Color(0xFF49317A) else panel,
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(Modifier.padding(13.dp)) {
                                        Text(msg.text, color = Color.White)
                                        if (!msg.user) {
                                            TextButton(onClick = {
                                                tts?.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, "reply")
                                            }) {
                                                Text("🔊 Ouvir resposta")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (busy) item { Text("Finit está pensando...", color = muted) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().background(panel).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                        }
                        try { speech.launch(intent) } catch (_: Exception) {}
                    }) {
                        Icon(Icons.Default.Mic, "Ditado", tint = purple)
                    }

                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escreva sua mensagem") }
                    )

                    IconButton(
                        enabled = draft.isNotBlank() && !busy,
                        onClick = {
                            val question = draft.trim()
                            draft = ""
                            messages.add(Message(question, true))
                            saveHistory()
                            busy = true
                            scope.launch {
                                val answer = askAI(
                                    endpoint, model, key, personality, messages.toList()
                                )
                                messages.add(Message(answer, false))
                                saveHistory()
                                tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "reply")
                                busy = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, "Enviar", tint = purple)
                    }
                }
            }
        }
    }
}

fun engineLanguage(engine: TextToSpeech?) {
    engine?.language = Locale("pt", "BR")
}

@Composable
fun SettingField(label: String, value: String, change: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Color.White)
        OutlinedTextField(
            value = value,
            onValueChange = change,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

suspend fun askAI(
    endpoint: String,
    model: String,
    key: String,
    personality: String,
    messages: List<Message>
): String = withContext(Dispatchers.IO) {
    try {
        require(endpoint.startsWith("https://")) {
            "Configure um endpoint HTTPS nas configurações."
        }
        require(model.isNotBlank()) {
            "Informe o identificador do modelo nas configurações."
        }

        val style = when (personality) {
            "Amiga" -> "Seja amigável, empática e descontraída."
            "Romântica" -> "Use um tom carinhoso e romântico."
            "Direta" -> "Seja objetiva e direta."
            "Criativa" -> "Seja criativa e imaginativa."
            else -> "Seja equilibrada, clara e prestativa."
        }

        val history = JSONArray()
        history.put(
            JSONObject()
                .put("role", "system")
                .put("content", "Você é AI Finit, assistente em português brasileiro. $style Você é destinada a adultos 18+. Temas maduros devem envolver adultos e consentimento. Nunca sexualize menores nem normalize abuso ou exploração. Respeite as regras do provedor conectado.")
        )

        messages.takeLast(40).forEach {
            history.put(
                JSONObject()
                    .put("role", if (it.user) "user" else "assistant")
                    .put("content", it.text)
            )
        }

        val body = JSONObject()
            .put("model", model)
            .put("messages", history)
            .put("temperature", 0.8)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val builder = Request.Builder().url(endpoint).post(body)
        if (key.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $key")
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        client.newCall(builder.build()).execute().use { response ->
            val result = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                "Erro do provedor: HTTP ${response.code}. ${result.take(250)}"
            } else {
                JSONObject(result)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                    ?: "O provedor não retornou uma resposta válida."
            }
        }
    } catch (e: Exception) {
        "Não consegui conectar: ${e.message ?: "erro desconhecido"}"
    }
}
