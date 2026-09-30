package com.example.zeca.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var rascunho by rememberSaveable { mutableStateOf("") }
    var enviando by rememberSaveable { mutableStateOf(false) }
    var erro by rememberSaveable { mutableStateOf("") }
    var mensagens by remember { mutableStateOf<List<MensagemChat>>(emptyList()) }
    var conversas by remember { mutableStateOf<List<ConversaChat>>(emptyList()) }
    val destinatario = jogadores.firstOrNull { it.uid == destinatarioUid }
    val chatId = when {
        modo == "Global" -> "global"
        destinatarioUid.isNotBlank() -> FirebaseRepository.idConversaPrivada(uidAtual, destinatarioUid)
        else -> null
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

            if (modo == "Privado" && destinatarioUid.isBlank()) {
                Text("Conversas recentes", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (conversas.isEmpty()) {
                    Text("Ainda não há conversas privadas.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                } else {
                    conversas.forEach { conversa ->
                        val jogador = jogadores.firstOrNull { it.uid == conversa.outroUid }
                        LinhaJogador(
                            nome = jogador?.apelido ?: "Jogador",
                            detalhe = conversa.ultimaMensagem,
                            onClick = { destinatarioUid = conversa.outroUid; erro = "" },
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
                            onClick = { destinatarioUid = jogador.uid; erro = "" },
                        )
                    }
                }
            } else {
                if (modo == "Privado") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(destinatario?.apelido ?: "Conversa privada", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { destinatarioUid = "" }) { Text("Conversas") }
                    }
                } else {
                    Text("Sala global", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mensagens.isEmpty()) {
                        Text("Envie a primeira mensagem.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                    }
                    mensagens.takeLast(80).forEach { mensagem ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(if (mensagem.minha) 0.92f else 1f)
                                .align(if (mensagem.minha) Alignment.End else Alignment.Start)
                                .background(
                                    if (mensagem.minha) Cores.Verde.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(15.dp),
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                                .padding(12.dp),
                        ) {
                            if (!mensagem.minha) Text(mensagem.autor, color = Cores.Turquesa, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun LinhaJogador(nome: String, detalhe: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(13.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(nome, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(detalhe, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
        }
        Text("Abrir", color = Cores.Verde, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
