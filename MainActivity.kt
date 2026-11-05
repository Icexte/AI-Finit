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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val Bg = Color(0xFF0B0B12)
private val Panel = Color(0xFF171722)
private val Purple = Color(0xFF9B6DFF)
private val Muted = Color(0xFFAAA6B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("aifinit_v2", MODE_PRIVATE)
        setContent { FinitApp(prefs) }
    }
}

data class ChatMessage(val text: String, val fromUser: Boolean)

private val personalities = listOf(
    "Equilibrada" to "Seja natural, prestativa, clara e acolhedora.",
    "Amiga" to "Fale como uma amiga próxima, com empatia, leveza e bom humor.",
    "Romântica" to "Use um tom carinhoso e romântico, sem fingir ser uma pessoa real.",
    "Direta" to "Responda de forma objetiva, prática e sem rodeios.",
    "Criativa" to "Seja imaginativa, proponha ideias originais e use linguagem expressiva."
)

@Composable
fun FinitApp(prefs: android.content.SharedPreferences) {
    var accepted by remember { mutableStateOf(prefs.getBoolean("adult_ok", false)) }
    var showSettings by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var personality by remember { mutableStateOf(prefs.getString("personality", "Equilibrada") ?: "Equilibrada") }
    var voiceEnabled by remember { mutableStateOf(prefs.getBoolean("voice_enabled", true)) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale("pt", "BR")
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("chat_history", "[]") ?: "[]"
        try {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                messages.add(ChatMessage(item.optString("text"), item.optBoolean("user")))
            }
        } catch (_: Exception) { }
    }

    fun persistChat() {
        val arr = JSONArray()
        messages.takeLast(80).forEach {
            arr.put(JSONObject().put("text", it.text).put("user", it.fromUser))
        }
        prefs.edit().putString("chat_history", arr.toString()).apply()
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) draft = listOf(draft, spoken).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Purple, background = Bg, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        if (!accepted) {
            AgeGate {
                prefs.edit().putBoolean("adult_ok", true).apply()
                accepted = true
            }
        } else if (showSettings) {
            SettingsScreen(
                endpoint, { endpoint = it }, model, { model = it }, apiKey, { apiKey = it },
                personality, { personality = it }, voiceEnabled, { voiceEnabled = it },
                onSave = {
                    prefs.edit().putString("endpoint", endpoint.trim())
                        .putString("model", model.trim()).putString("key", apiKey.trim())
                        .putString("personality", personality).putBoolean("voice_enabled", voiceEnabled).apply()
                    showSettings = false
                },
                onBack = { showSettings = false }
            )
        } else {
            Column(Modifier.fillMaxSize().background(Bg)) {
                Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("AI FINIT", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("VERSÃO 2.0 • $personality", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        messages.clear()
                        persistChat()
                    }) { Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar conversa", tint = Color.White) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = Color.White) }
                }

                if (messages.isEmpty()) {
                    Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("✦", color = Purple, fontSize = 50.sp)
                        Text("Olá! Eu sou a Finit.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Sua assistente pessoal. Configure um provedor de IA para começar.", color = Muted)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { showSettings = true }) { Text("Configurar IA") }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { msg ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                                Surface(color = if (msg.fromUser) Color(0xFF49317A) else Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 315.dp)) {
                                    Column(Modifier.padding(13.dp)) {
                                        Text(msg.text, color = Color.White)
                                        if (!msg.fromUser) {
                                            TextButton(
                                                contentPadding = PaddingValues(0.dp),
                                                onClick = { if (voiceEnabled) tts?.speak(msg.text, TextToSpeech.QUEUE_FLUSH, null, "finit_reply") }
                                            ) { Icon(Icons.Default.VolumeUp, contentDescription = "Ouvir resposta", tint = Purple); Spacer(Modifier.width(5.dp)); Text("Ouvir", color = Purple) }
                                        }
                                    }
                                }
                            }
                        }
                        if (busy) item { Text("Finit está pensando…", color = Muted, modifier = Modifier.padding(8.dp)) }
                    }
                }

                Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Fale com a AI Finit")
                        }
                        try { speechLauncher.launch(intent) } catch (_: Exception) { draft = "Reconhecimento de voz indisponível neste aparelho." }
                    }) { Icon(Icons.Default.Mic, contentDescription = "Ditado por voz", tint = Purple) }

                    BasicTextField(
                        value = draft, onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).background(Color(0xFF252533), RoundedCornerShape(22.dp)).padding(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        decorationBox = { inner -> if (draft.isEmpty()) Text("Escreva ou dite…", color = Muted); inner() }
                    )
                    IconButton(enabled = !busy && draft.isNotBlank(), onClick = {
                        val text = draft.trim()
                        draft = ""
                        messages.add(ChatMessage(text, true))
                        persistChat()
                        busy = true
                        scope.launch {
                            val answer = requestAI(endpoint, model, apiKey, personality, messages.toList())
                            messages.add(ChatMessage(answer, false))
                            persistChat()
                            if (voiceEnabled) tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "finit_reply")
                            busy = false
                        }
                    }) { Icon(Icons.Default.Send, contentDescription = "Enviar", tint = Purple) }
                }
                Text("18+ • Voz usa os serviços de fala disponíveis no Android • Respostas dependem do provedor", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 5.dp))
            }
        }
    }
}

@Composable
fun AgeGate(onAccept: () -> Unit) {
    var checked by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Bg).padding(26.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("AI FINIT", color = Purple, fontSize = 31.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(18.dp))
        Text("Confirmação de idade", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Aplicativo destinado a adultos. Ao continuar, você declara ter 18 anos ou mais. Temas maduros devem envolver adultos e consentimento. Conteúdo envolvendo menores, abuso ou exploração não é permitido.", color = Muted)
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, { checked = it })
            Text("Confirmo que tenho 18 anos ou mais.", color = Color.White)
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onAccept, enabled = checked, modifier = Modifier.fillMaxWidth()) { Text("Entrar na AI Finit") }
    }
}

@Composable
fun SettingsScreen(
    endpoint: String, setEndpoint: (String) -> Unit,
    model: String, setModel: (String) -> Unit,
    apiKey: String, setApiKey: (String) -> Unit,
    personality: String, setPersonality: (String) -> Unit,
    voiceEnabled: Boolean, setVoiceEnabled: (Boolean) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Bg).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Voltar", tint = Color.White) }
            Text("Configurações da Finit", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text("Personalidade", color = Color.White, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(personality, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ExpandMore, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                personalities.forEach { option ->
                    DropdownMenuItem(text = { Text(option.first) }, onClick = { setPersonality(option.first); expanded = false })
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Respostas por voz", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Lê as respostas em voz alta", color = Muted, fontSize = 12.sp)
            }
            Switch(checked = voiceEnabled, onCheckedChange = setVoiceEnabled)
        }
        Spacer(Modifier.height(10.dp))
        Text("Conexão com IA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Use um provedor com plano gratuito e API compatível com OpenAI Chat Completions. O provedor pode ter limites e filtros próprios.", color = Muted, fontSize = 12.sp)
        Field("URL HTTPS do endpoint", endpoint, setEndpoint, "https://servidor/v1/chat/completions")
        Field("Identificador do modelo", model, setModel, "nome-exato-do-modelo")
        Field("Chave de API (se necessária)", apiKey, setApiKey, "Cole sua chave")
        Text("A chave fica nas preferências privadas do app, mas esta versão não usa Android Keystore. Não publique a chave no GitHub. Mensagens são enviadas ao provedor configurado.", color = Muted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Salvar configurações") }
    }
}

@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, hint: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value, onChange, placeholder = { Text(hint) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    }
}

suspend fun requestAI(endpoint: String, model: String, apiKey: String, personality: String, messages: List<ChatMessage>): String =
    withContext(Dispatchers.IO) {
        try {
            require(endpoint.startsWith("https://")) { "Use um endpoint HTTPS seguro nas configurações." }
            require(model.isNotBlank()) { "Informe o identificador do modelo nas configurações." }
            val style = personalities.firstOrNull { it.first == personality }?.second ?: personalities[0].second
            val history = JSONArray()
            history.put(JSONObject().put("role", "system").put("content",
                "Você é AI Finit, uma assistente em português brasileiro. $style Seja honesta sobre suas capacidades. O app é destinado a adultos 18+. Temas maduros só podem envolver adultos e consentimento; nunca sexualize menores nem normalize abuso, coerção ou exploração. Respeite a lei e as políticas do provedor conectado."
            ))
            messages.takeLast(40).forEach {
                history.put(JSONObject().put("role", if (it.fromUser) "user" else "assistant").put("content", it.text))
            }
            val payload = JSONObject().put("model", model).put("messages", history).put("temperature", 0.8)
            val builder = Request.Builder().url(endpoint).post(payload.toString().toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) builder.addHeader("Authorization", "Bearer $apiKey")
            val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
            client.newCall(builder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext "Erro do provedor (${response.code}): ${body.take(400)}"
                JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                    ?.takeIf { it.isNotBlank() } ?: "O provedor não retornou texto no formato esperado."
            }
        } catch (e: Exception) {
            "Falha na conexão: ${e.message ?: "erro desconhecido"}. Confira endpoint, modelo, chave e internet."
        }
    }
