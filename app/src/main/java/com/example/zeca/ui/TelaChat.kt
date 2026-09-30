package com.example.zeca.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ConversaChat
import com.example.zeca.FirebaseRepository
import com.example.zeca.JogadorRanking
import com.example.zeca.MensagemChat
import com.example.zeca.ui.theme.Cores
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun TelaChat(
    uidAtual: String,
    jogadores: List<JogadorRanking>,
    onEnviar: (String?, String, String, (Exception?) -> Unit) -> Unit,
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
        if (chatId == null) {
            mensagens = emptyList()
            onDispose { }
        } else {
            val registration = FirebaseRepository.observarMensagensChat(chatId, uidAtual) { novas, error ->
                mensagens = novas
                if (error != null) erro = error.localizedMessage ?: "Não foi possível carregar as mensagens."
            }
            onDispose { registration.remove() }
        }
    }

    TelaChatBase {
        if (perfilUid.isNotBlank()) {
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
            return@TelaChatBase
        }

        if (emConversa) {
            CabecalhoConversa(
                nome = destinatario?.apelido ?: "Jogador",
                detalhe = destinatario?.let { "Nível ${it.nivel}" } ?: "",
                onVoltar = { destinatarioUid = ""; erro = "" },
                onPerfil = { perfilUid = destinatarioUid },
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Chat", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                    Text("Converse com os jogadores.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                }
            }
        }

        PainelChat {
            if (!emConversa) {
                OpcoesChat(listOf("Global", "Privado"), modo) {
                    modo = it
                    destinatarioUid = ""
                    erro = ""
                }
            }

            if (modo == "Privado" && !emConversa) {
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
            } else {
                if (modo == "Global") {
                    Column {
                        Text("Sala global", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                        Text("Todos os jogadores podem ver estas mensagens.", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mensagens.isEmpty()) {
                        Text("Envie a primeira mensagem.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                    }
                    mensagens.takeLast(80).forEach { mensagem ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (mensagem.minha) Arrangement.End else Arrangement.Start,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            if (!mensagem.minha) {
                                AvatarChat(
                                    nome = mensagem.autor,
                                    tamanho = 32.dp,
                                    modifier = Modifier.clickable { perfilUid = mensagem.autorUid },
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .background(
                                        if (mensagem.minha) Cores.Verde.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(15.dp),
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                                    .padding(12.dp),
                            ) {
                                if (!mensagem.minha && modo == "Global") {
                                    Text(
                                        mensagem.autor,
                                        color = Cores.Turquesa,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { perfilUid = mensagem.autorUid },
                                    )
                                }
                                Text(mensagem.texto, color = Color.White, fontSize = 14.sp)
                                if (mensagem.enviadaEmMs > 0) {
                                    Text(
                                        SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(mensagem.enviadaEmMs)),
                                        color = Color.White.copy(alpha = 0.48f),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = rascunho,
                        onValueChange = { rascunho = it.take(500); erro = "" },
                        modifier = Modifier.weight(1f),
                        label = { Text("Mensagem") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    Button(
                        onClick = {
                            val texto = rascunho.trim()
                            if (texto.isNotEmpty() && !enviando) {
                                enviando = true
                                erro = ""
                                onEnviar(
                                    if (modo == "Global") null else destinatarioUid,
                                    texto,
                                    UUID.randomUUID().toString(),
                                ) { error ->
                                    enviando = false
                                    if (error == null) rascunho = ""
                                    else erro = error.localizedMessage ?: "Não foi possível enviar a mensagem."
                                }
                            }
                        },
                        enabled = rascunho.isNotBlank() && !enviando && (modo == "Global" || destinatarioUid.isNotBlank()),
                    ) { Text(if (enviando) "..." else "Enviar") }
                }
            }

            if (erro.isNotBlank()) Text(erro, color = Cores.Laranja, fontSize = 12.sp)
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
