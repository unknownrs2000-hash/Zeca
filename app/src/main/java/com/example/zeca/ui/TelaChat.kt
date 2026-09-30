package com.example.zeca.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.zeca.ConversaChat
import com.example.zeca.FirebaseRepository
import com.example.zeca.JogadorRanking
import com.example.zeca.MensagemChat
import com.example.zeca.RespostaChat
import com.example.zeca.ui.theme.Cores
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun TelaChat(
    uidAtual: String,
    jogadores: List<JogadorRanking>,
    onEnviar: (String?, String, String, RespostaChat?, (Exception?) -> Unit) -> Unit,
    onApagarParaMim: (String, String, (Exception?) -> Unit) -> Unit,
    onApagarParaTodos: (String, String, (Exception?) -> Unit) -> Unit,
) {
    var modo by rememberSaveable { mutableStateOf("Global") }
    var destinatarioUid by rememberSaveable { mutableStateOf("") }
    var perfilUid by rememberSaveable { mutableStateOf("") }
    var rascunho by rememberSaveable { mutableStateOf("") }
    var enviando by rememberSaveable { mutableStateOf(false) }
    var erro by rememberSaveable { mutableStateOf("") }
    var mensagens by remember { mutableStateOf<List<MensagemChat>>(emptyList()) }
    var conversas by remember { mutableStateOf<List<ConversaChat>>(emptyList()) }
    val destinatario = jogadores.firstOrNull { it.uid == destinatarioUid }
    val perfilJogador = jogadores.firstOrNull { it.uid == perfilUid }
    val emConversa = modo == "Privado" && destinatarioUid.isNotBlank()
    val chatId = when {
        modo == "Global" -> "global"
        destinatarioUid.isNotBlank() -> FirebaseRepository.idConversaPrivada(uidAtual, destinatarioUid)
        else -> null
    }

    BackHandler(enabled = perfilUid.isNotBlank() || emConversa) {
        if (perfilUid.isNotBlank()) {
            perfilUid = ""
        } else {
            destinatarioUid = ""
            erro = ""
        }
    }

    DisposableEffect(uidAtual) {
        val registration = FirebaseRepository.observarConversasChat(uidAtual) { novas, error ->
            conversas = novas
            if (error != null) erro = error.localizedMessage ?: "Não foi possível carregar as conversas."
        }
        onDispose { registration.remove() }
    }

    DisposableEffect(uidAtual, chatId) {
        mensagens = emptyList()
        if (chatId == null) {
            onDispose { }
        } else {
            val registration = FirebaseRepository.observarMensagensChat(chatId, uidAtual) { novas, error ->
                mensagens = novas
                if (error != null) erro = error.localizedMessage ?: "Não foi possível carregar as mensagens."
            }
            onDispose { registration.remove() }
        }
    }

    val enviarMensagem: (RespostaChat?, () -> Unit) -> Unit = { resposta, aoSucesso ->
        val texto = rascunho.trim()
        if (texto.isNotEmpty() && !enviando) {
            enviando = true
            erro = ""
            onEnviar(
                if (modo == "Global") null else destinatarioUid,
                texto,
                UUID.randomUUID().toString(),
                resposta,
            ) { error ->
                enviando = false
                if (error == null) {
                    rascunho = ""
                    aoSucesso()
                } else {
                    erro = error.localizedMessage ?: "Não foi possível enviar a mensagem."
                }
            }
        }
    }

    if (perfilUid.isNotBlank()) {
        TelaChatBase {
            PerfilChat(
                jogador = perfilJogador,
                posicao = jogadores.indexOfFirst { it.uid == perfilUid } + 1,
                ehVoce = perfilUid == uidAtual,
                onVoltar = { perfilUid = "" },
                onConversar = {
                    modo = "Privado"
                    destinatarioUid = perfilUid
                    perfilUid = ""
                    erro = ""
                },
            )
        }
    } else if (modo == "Privado" && !emConversa) {
        TelaChatBase {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Chat", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Text("Converse com os jogadores.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                }
            }

            PainelChat {
                OpcoesChat(listOf("Global", "Privado"), modo) {
                    modo = it
                    destinatarioUid = ""
                    erro = ""
                }

                Text("Conversas recentes", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (conversas.isEmpty()) {
                    Text("Ainda não há conversas privadas.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                } else {
                    conversas.forEach { conversa ->
                        val jogador = jogadores.firstOrNull { it.uid == conversa.outroUid }
                        LinhaJogador(
                            nome = jogador?.apelido ?: "Jogador",
                            detalhe = conversa.ultimaMensagem,
                            hora = horaConversa(conversa.atualizadaEmMs),
                            onClick = { destinatarioUid = conversa.outroUid; erro = "" },
                            onPerfil = { perfilUid = conversa.outroUid },
                        )
                    }
                }

                Text("Iniciar conversa", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                val contatos = jogadores.filter { it.uid != uidAtual }.take(30)
                if (contatos.isEmpty()) {
                    Text("Outros jogadores aparecerão aqui.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                } else {
                    contatos.forEach { jogador ->
                        LinhaJogador(
                            nome = jogador.apelido,
                            detalhe = "Nível ${jogador.nivel}",
                            hora = "",
                            onClick = { destinatarioUid = jogador.uid; erro = "" },
                            onPerfil = { perfilUid = jogador.uid },
                        )
                    }
                }

                if (erro.isNotBlank()) Text(erro, color = Cores.Laranja, fontSize = 12.sp)
            }
        }
    } else {
        key(chatId) {
            TelaConversa(
                mensagens = mensagens,
                ehGlobal = modo == "Global",
                rascunho = rascunho,
                onRascunhoChange = { rascunho = it.take(500); erro = "" },
                enviando = enviando,
                erro = erro,
                onEnviar = enviarMensagem,
                onPerfil = { perfilUid = it },
                onApagarParaMim = { mensagemId ->
                    if (chatId != null) {
                        onApagarParaMim(chatId, mensagemId) { e ->
                            if (e != null) erro = e.localizedMessage ?: "Não foi possível apagar a mensagem."
                        }
                    }
                },
                onApagarParaTodos = { mensagemId ->
                    if (chatId != null) {
                        onApagarParaTodos(chatId, mensagemId) { e ->
                            if (e != null) erro = e.localizedMessage ?: "Não foi possível apagar a mensagem."
                        }
                    }
                },
                cabecalho = {
                    if (emConversa) {
                        CabecalhoConversa(
                            nome = destinatario?.apelido ?: "Jogador",
                            detalhe = destinatario?.let { "Nível ${it.nivel}" } ?: "",
                            onVoltar = { destinatarioUid = ""; erro = "" },
                            onPerfil = { perfilUid = destinatarioUid },
                        )
                    } else {
                        Column {
                            Text("Chat", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                            Text("Todos os jogadores podem ver a sala global.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                        }
                        OpcoesChat(listOf("Global", "Privado"), modo) {
                            modo = it
                            destinatarioUid = ""
                            erro = ""
                        }
                    }
                },
            )
        }
    }
}

private val CORES_AVATAR = listOf(
    Color(0xFF1B7A55),
    Color(0xFF3D5AFE),
    Color(0xFFB0397A),
    Color(0xFFC77D1A),
    Color(0xFF7A4DD8),
    Color(0xFF1592A6),
)

private fun horaConversa(ms: Long): String {
    if (ms <= 0L) return ""
    val local = Locale.forLanguageTag("pt-BR")
    val hoje = SimpleDateFormat("yyyyMMdd", local).format(Date())
    val dia = SimpleDateFormat("yyyyMMdd", local).format(Date(ms))
    val padrao = if (hoje == dia) "HH:mm" else "dd/MM"
    return SimpleDateFormat(padrao, local).format(Date(ms))
}

@Composable
private fun AvatarChat(nome: String, tamanho: Dp, modifier: Modifier = Modifier) {
    val cor = CORES_AVATAR[(nome.hashCode() and Int.MAX_VALUE) % CORES_AVATAR.size]
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(CircleShape)
            .then(modifier)
            .background(cor)
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            nome.trim().take(1).uppercase(),
            color = Color.White,
            fontSize = (tamanho.value * 0.42f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun CabecalhoConversa(nome: String, detalhe: String, onVoltar: () -> Unit, onPerfil: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onVoltar) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onPerfil)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AvatarChat(nome, 42.dp)
            Column {
                Text(nome, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (detalhe.isBlank()) "Ver perfil" else "$detalhe · Ver perfil",
                    color = Cores.Turquesa,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun PerfilChat(
    jogador: JogadorRanking?,
    posicao: Int,
    ehVoce: Boolean,
    onVoltar: () -> Unit,
    onConversar: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onVoltar) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
        Text("Perfil", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
    PainelChat {
        if (jogador == null) {
            Text("Jogador não encontrado.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AvatarChat(jogador.apelido, 88.dp)
                Text(
                    jogador.apelido + if (ehVoce) " (você)" else "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text("Nível ${jogador.nivel}", color = Cores.Turquesa, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EstatisticaPerfil("Ranking", if (posicao > 0) "#$posicao" else "—", Modifier.weight(1f))
                EstatisticaPerfil("Saldo", formatarReais(jogador.saldoCentavos), Modifier.weight(1f))
            }
            if (!ehVoce) {
                Button(onClick = onConversar, modifier = Modifier.fillMaxWidth()) { Text("Enviar mensagem") }
            }
        }
    }
}

@Composable
private fun EstatisticaPerfil(titulo: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(13.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(valor, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(titulo, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
    }
}

@Composable
private fun LinhaJogador(
    nome: String,
    detalhe: String,
    hora: String,
    onClick: () -> Unit,
    onPerfil: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarChat(nome, 42.dp, Modifier.clickable(onClick = onPerfil))
        Column(modifier = Modifier.weight(1f)) {
            Text(nome, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detalhe, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            hora.ifBlank { "Abrir" },
            color = if (hora.isBlank()) Cores.Verde else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TelaChatBase(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151A1D), Cores.Fundo, Color(0xFF090D10))))
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 118.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

@Composable
private fun PainelChat(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.055f))), RoundedCornerShape(22.dp))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun OpcoesChat(opcoes: List<String>, selecionada: String, onSelecionar: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opcoes.forEach { opcao ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (opcao == selecionada) Color.White.copy(alpha = 0.9f) else Color.Transparent, RoundedCornerShape(11.dp))
                    .clickable { onSelecionar(opcao) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(opcao, color = if (opcao == selecionada) Color(0xFF111418) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TelaConversa(
    mensagens: List<MensagemChat>,
    ehGlobal: Boolean,
    rascunho: String,
    onRascunhoChange: (String) -> Unit,
    enviando: Boolean,
    erro: String,
    onEnviar: (RespostaChat?, () -> Unit) -> Unit,
    onPerfil: (String) -> Unit,
    onApagarParaMim: (String) -> Unit,
    onApagarParaTodos: (String) -> Unit,
    cabecalho: @Composable ColumnScope.() -> Unit,
) {
    val contexto = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val foco = LocalFocusManager.current
    val escopo = rememberCoroutineScope()
    val estadoLista = rememberLazyListState()
    val campoFoco = remember { FocusRequester() }
    val tecladoAberto = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var respondendo by remember { mutableStateOf<MensagemChat?>(null) }
    var menu by remember { mutableStateOf<MensagemChat?>(null) }
    var apagando by remember { mutableStateOf<MensagemChat?>(null) }
    val invertida = remember(mensagens) { mensagens.asReversed() }
    val podeEnviar = rascunho.isNotBlank() && !enviando

    val fecharTeclado: () -> Unit = { foco.clearFocus() }
    val conexaoRolagem = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y != 0f) foco.clearFocus()
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(mensagens.lastOrNull()?.id) {
        if (mensagens.isNotEmpty()) estadoLista.animateScrollToItem(0)
    }

    LaunchedEffect(respondendo?.id) {
        if (respondendo != null) {
            delay(150)
            campoFoco.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151A1D), Cores.Fundo, Color(0xFF090D10))))
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = cabecalho,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { fecharTeclado() }) },
        ) {
            if (invertida.isEmpty()) {
                Text(
                    "Envie a primeira mensagem.",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 13.sp,
                )
            }
            LazyColumn(
                state = estadoLista,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(conexaoRolagem),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(invertida, key = { it.id }) { mensagem ->
                    BolhaMensagem(
                        mensagem = mensagem,
                        mostrarAutor = ehGlobal && !mensagem.minha,
                        onPerfil = { onPerfil(mensagem.autorUid) },
                        onMenu = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = mensagem
                        },
                        onResponder = { if (!mensagem.apagadaParaTodos) respondendo = mensagem },
                        onIrParaOriginal = { id ->
                            val indice = invertida.indexOfFirst { it.id == id }
                            if (indice >= 0) escopo.launch { estadoLista.animateScrollToItem(indice) }
                        },
                    )
                }
            }
        }

        respondendo?.let { alvo ->
            PreviaResposta(alvo, onFechar = { respondendo = null })
        }

        if (erro.isNotBlank()) {
            Text(
                erro,
                color = Cores.Laranja,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = if (tecladoAberto) 8.dp else 100.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = rascunho,
                onValueChange = onRascunhoChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(campoFoco),
                placeholder = { Text("Mensagem") },
                shape = RoundedCornerShape(26.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (podeEnviar) Cores.Verde else Color.White.copy(alpha = 0.12f))
                    .clickable(enabled = podeEnviar) {
                        val alvo = respondendo
                        val resposta = alvo?.let {
                            RespostaChat(it.id, it.autor, if (it.apagadaParaTodos) "Mensagem apagada" else it.texto)
                        }
                        onEnviar(resposta) { respondendo = null }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Enviar",
                    tint = if (podeEnviar) Color(0xFF07130F) else Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }

    menu?.let { alvo ->
        MenuMensagem(
            mensagem = alvo,
            onDispensar = { menu = null },
            onResponder = { respondendo = alvo; menu = null },
            onCopiar = { copiarTexto(contexto, alvo.texto); menu = null },
            onCompartilhar = { compartilharTexto(contexto, alvo.texto); menu = null },
            onPerfil = if (!alvo.minha) ({ onPerfil(alvo.autorUid); menu = null }) else null,
            onApagar = { apagando = alvo; menu = null },
        )
    }

    apagando?.let { alvo ->
        DialogoApagar(
            podeParaTodos = alvo.minha && !alvo.apagadaParaTodos,
            onParaMim = { onApagarParaMim(alvo.id); apagando = null },
            onParaTodos = { onApagarParaTodos(alvo.id); apagando = null },
            onCancelar = { apagando = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BolhaMensagem(
    mensagem: MensagemChat,
    mostrarAutor: Boolean,
    onPerfil: () -> Unit,
    onMenu: () -> Unit,
    onResponder: () -> Unit,
    onIrParaOriginal: (String) -> Unit,
) {
    var arraste by remember { mutableStateOf(0f) }
    val limite = with(LocalDensity.current) { 64.dp.toPx() }
    val responder by rememberUpdatedState(onResponder)

    Box(modifier = Modifier.fillMaxWidth()) {
        if (arraste > limite * 0.4f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = Cores.Turquesa,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(22.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(arraste.roundToInt(), 0) }
                .pointerInput(mensagem.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (arraste >= limite) responder()
                            arraste = 0f
                        },
                        onDragCancel = { arraste = 0f },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            arraste = (arraste + dx).coerceIn(0f, limite * 1.4f)
                        },
                    )
                },
            horizontalArrangement = if (mensagem.minha) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!mensagem.minha) {
                AvatarChat(mensagem.autor, 32.dp, Modifier.clickable(onClick = onPerfil))
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        if (mensagem.minha) Cores.Verde.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                    .combinedClickable(onClick = {}, onLongClick = onMenu)
                    .padding(12.dp),
            ) {
                if (mostrarAutor) {
                    Text(
                        mensagem.autor,
                        color = Cores.Turquesa,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onPerfil),
                    )
                }
                if (mensagem.respostaId.isNotBlank() && !mensagem.apagadaParaTodos) {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.22f))
                            .clickable { onIrParaOriginal(mensagem.respostaId) }
                            .height(IntrinsicSize.Min),
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(Cores.Turquesa),
                        )
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                mensagem.respostaAutor,
                                color = Cores.Turquesa,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                mensagem.respostaTexto,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (mensagem.apagadaParaTodos) {
                    Text(
                        "🚫 Mensagem apagada",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                        fontStyle = FontStyle.Italic,
                    )
                } else {
                    Text(mensagem.texto, color = Color.White, fontSize = 14.sp)
                }
                if (mensagem.enviadaEmMs > 0) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(mensagem.enviadaEmMs)),
                        color = Color.White.copy(alpha = 0.48f),
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviaResposta(alvo: MensagemChat, onFechar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(Cores.Turquesa),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                "Respondendo a ${alvo.autor}",
                color = Cores.Turquesa,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (alvo.apagadaParaTodos) "Mensagem apagada" else alvo.texto,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onFechar) {
            Icon(Icons.Filled.Close, contentDescription = "Cancelar resposta", tint = Color.White)
        }
    }
}

@Composable
private fun MenuMensagem(
    mensagem: MensagemChat,
    onDispensar: () -> Unit,
    onResponder: () -> Unit,
    onCopiar: () -> Unit,
    onCompartilhar: () -> Unit,
    onPerfil: (() -> Unit)?,
    onApagar: () -> Unit,
) {
    Dialog(onDismissRequest = onDispensar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2126), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                .padding(vertical = 8.dp),
        ) {
            Text(
                if (mensagem.apagadaParaTodos) "Mensagem apagada" else mensagem.texto,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            if (!mensagem.apagadaParaTodos) {
                AcaoMenu(Icons.AutoMirrored.Filled.Reply, "Responder", onResponder)
                AcaoMenu(Icons.Filled.ContentCopy, "Copiar", onCopiar)
                AcaoMenu(Icons.Filled.Share, "Compartilhar", onCompartilhar)
                if (onPerfil != null) AcaoMenu(Icons.Filled.Person, "Ver perfil", onPerfil)
            }
            AcaoMenu(Icons.Filled.Delete, "Apagar", onApagar, Color(0xFFFF6879))
        }
    }
}

@Composable
private fun AcaoMenu(icone: ImageVector, titulo: String, onClick: () -> Unit, cor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(22.dp))
        Text(titulo, color = cor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DialogoApagar(
    podeParaTodos: Boolean,
    onParaMim: () -> Unit,
    onParaTodos: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Color(0xFF1A2126),
        title = { Text("Apagar mensagem?", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                if (podeParaTodos) "Você pode apagar só para você ou para todos da conversa."
                else "A mensagem será apagada só para você.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (podeParaTodos) {
                    TextButton(onClick = onParaTodos) { Text("Apagar para todos", color = Color(0xFFFF6879)) }
                }
                TextButton(onClick = onParaMim) { Text("Apagar para mim", color = Color(0xFFFF6879)) }
                TextButton(onClick = onCancelar) { Text("Cancelar") }
            }
        },
    )
}

private fun copiarTexto(contexto: Context, texto: String) {
    val gerente = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    gerente.setPrimaryClip(ClipData.newPlainText("mensagem", texto))
    Toast.makeText(contexto, "Mensagem copiada", Toast.LENGTH_SHORT).show()
}

private fun compartilharTexto(contexto: Context, texto: String) {
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(envio, "Compartilhar mensagem"))
}