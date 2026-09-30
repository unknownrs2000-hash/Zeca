package com.example.zeca.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.zeca.ConversaChat
import com.example.zeca.FirebaseRepository
import com.example.zeca.JogadorRanking
import com.example.zeca.MensagemChat
import com.example.zeca.PerfilPublico
import com.example.zeca.RespostaChat
import com.example.zeca.ui.theme.Cores
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun TelaChat(
    uidAtual: String,
    chavePixAtual: String,
    jogadores: List<JogadorRanking>,
    onEnviar: (String?, String?, String, String, RespostaChat?, (Exception?) -> Unit) -> Unit,
    onEncaminhar: (String?, String?, String, String, (Exception?) -> Unit) -> Unit,
    onCriarGrupo: (String, String, List<String>, String, String, String, (String?, Exception?) -> Unit) -> Unit,
    onAtualizarGrupo: (String, String, String, String, String, (Exception?) -> Unit) -> Unit,
    onRenomearFoguinho: (String, String, (Exception?) -> Unit) -> Unit,
    onApagarParaMim: (String, String, (Exception?) -> Unit) -> Unit,
    onApagarParaTodos: (String, String, (Exception?) -> Unit) -> Unit,
    onEditarMensagem: (String, String, String, (Exception?) -> Unit) -> Unit,
    onBuscarPerfil: (String, (PerfilPublico?, Exception?) -> Unit) -> Unit,
) {
    var modo by rememberSaveable { mutableStateOf("Global") }
    var destinatarioUid by rememberSaveable { mutableStateOf("") }
    var grupoUid by rememberSaveable { mutableStateOf("") }
    var perfilUid by rememberSaveable { mutableStateOf("") }
    var rascunho by rememberSaveable { mutableStateOf("") }
    var enviando by rememberSaveable { mutableStateOf(false) }
    var erro by rememberSaveable { mutableStateOf("") }
    var mensagens by remember { mutableStateOf<List<MensagemChat>>(emptyList()) }
    var conversas by remember { mutableStateOf<List<ConversaChat>>(emptyList()) }
    var perfilPublico by remember { mutableStateOf<PerfilPublico?>(null) }
    var carregandoPerfil by remember { mutableStateOf(false) }
    var erroPerfil by remember { mutableStateOf("") }
    var criarGrupoAberto by remember { mutableStateOf(false) }
    var nomeGrupo by rememberSaveable { mutableStateOf("") }
    var descricaoGrupo by rememberSaveable { mutableStateOf("") }
    var editarGrupoPolicy by rememberSaveable { mutableStateOf("creator") }
    var enviarGrupoPolicy by rememberSaveable { mutableStateOf("everyone") }
    val membrosGrupo = remember { mutableStateListOf<String>() }
    var criandoGrupo by remember { mutableStateOf(false) }
    var grupoErro by rememberSaveable { mutableStateOf("") }
    var editarFoguinho by remember { mutableStateOf(false) }
    var nomeFoguinhoEditavel by rememberSaveable { mutableStateOf("") }
    var salvandoFoguinho by remember { mutableStateOf(false) }
    var grupoConfigId by rememberSaveable { mutableStateOf("") }
    var nomeGrupoEditavel by rememberSaveable { mutableStateOf("") }
    var descricaoGrupoEditavel by rememberSaveable { mutableStateOf("") }
    var editarPolicyEditavel by rememberSaveable { mutableStateOf("creator") }
    var enviarPolicyEditavel by rememberSaveable { mutableStateOf("everyone") }
    var salvandoConfigGrupo by remember { mutableStateOf(false) }
    val destinatario = jogadores.firstOrNull { it.uid == destinatarioUid }
    val perfilJogador = jogadores.firstOrNull { it.uid == perfilUid }
        ?: perfilPublico?.takeIf { it.uid == perfilUid }?.let {
            JogadorRanking(
                it.uid,
                it.apelido,
                it.saldoCentavos,
                it.nivel,
                it.avatarUrl,
                it.username,
                it.avatarItensEquipados,
                it.avatarComoFotoPerfil,
            )
        }
    val conversaGrupo = conversas.firstOrNull { it.id == grupoUid && it.tipo == "group" }
    val conversasDiretas = conversas.filter { it.tipo != "group" }
    val grupos = conversas.filter { it.tipo == "group" }
    val emGrupo = grupoUid.isNotBlank()
    val emConversa = emGrupo || (modo == "Privado" && destinatarioUid.isNotBlank())
    val chatId = when {
        emGrupo -> grupoUid
        modo == "Global" -> "global"
        destinatarioUid.isNotBlank() -> FirebaseRepository.idConversaPrivada(uidAtual, destinatarioUid)
        else -> null
    }
    val conversaSelecionada = conversaGrupo ?: conversas.firstOrNull { it.id == chatId }

    fun abrirConfiguracoesGrupo(grupo: ConversaChat) {
        grupoConfigId = grupo.id
        nomeGrupoEditavel = grupo.nome
        descricaoGrupoEditavel = grupo.descricao
        editarPolicyEditavel = grupo.politicaEditar
        enviarPolicyEditavel = grupo.politicaEnviar
        grupoErro = ""
    }

    BackHandler(enabled = perfilUid.isNotBlank() || emConversa) {
        if (perfilUid.isNotBlank()) {
            perfilUid = ""
        } else if (emGrupo) {
            grupoUid = ""
            erro = ""
        } else {
            destinatarioUid = ""
            erro = ""
        }
    }

    DisposableEffect(uidAtual) {
        val registration = FirebaseRepository.observarConversasChat(uidAtual) { novas, error, _ ->
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
            val registration = FirebaseRepository.observarMensagensChat(chatId, uidAtual) { novas, error, _ ->
                mensagens = novas
                if (error != null) erro = error.localizedMessage ?: "Não foi possível carregar as mensagens."
            }
            onDispose { registration.remove() }
        }
    }

    fun enviarTextoChat(textoOriginal: String, resposta: RespostaChat?, aoSucesso: () -> Unit) {
        val texto = textoOriginal.trim()
        if (texto.isNotEmpty() && !enviando) {
            enviando = true
            erro = ""
            onEnviar(
                if (emGrupo || modo == "Global") null else destinatarioUid,
                grupoUid.takeIf { emGrupo },
                texto,
                UUID.randomUUID().toString(),
                resposta,
            ) { error ->
                enviando = false
                if (error == null) {
                    if (textoOriginal == rascunho) rascunho = ""
                    aoSucesso()
                } else {
                    erro = error.localizedMessage ?: "Não foi possível enviar a mensagem."
                }
            }
        }
    }
    val enviarMensagem: (RespostaChat?, () -> Unit) -> Unit = { resposta, aoSucesso ->
        enviarTextoChat(rascunho, resposta, aoSucesso)
    }

    LaunchedEffect(perfilUid) {
        perfilPublico = null
        erroPerfil = ""
        if (perfilUid.isBlank()) {
            carregandoPerfil = false
        } else {
            val alvoUid = perfilUid
            carregandoPerfil = true
            onBuscarPerfil(alvoUid) { perfil, error ->
                if (perfilUid == alvoUid) {
                    carregandoPerfil = false
                    perfilPublico = perfil
                    erroPerfil = error?.localizedMessage
                        ?: if (perfil == null) "Não foi possível carregar o perfil." else ""
                }
            }
        }
    }

    if (perfilUid.isNotBlank()) {
        TelaPerfilJogador(
            jogador = perfilJogador ?: JogadorRanking(perfilUid, "Jogador", 0L, 1, ""),
            perfil = perfilPublico,
            carregando = carregandoPerfil,
            erro = erroPerfil,
            onFechar = { perfilUid = "" },
            onConversar = if (perfilUid != uidAtual) ({
                modo = "Privado"
                destinatarioUid = perfilUid
                perfilUid = ""
                erro = ""
            }) else null,
        )
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
                    grupoUid = ""
                    erro = ""
                }

                Text("Conversas recentes", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (conversasDiretas.isEmpty()) {
                    Text("Ainda não há conversas privadas.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                } else {
                    conversasDiretas.forEach { conversa ->
                        val jogador = jogadores.firstOrNull { it.uid == conversa.outroUid }
                        LinhaJogador(
                            nome = jogador?.apelido ?: "Jogador",
                            username = jogador?.username.orEmpty(),
                            detalhe = conversa.ultimaMensagem,
                            hora = horaConversa(conversa.atualizadaEmMs),
                            avatarUrl = jogador?.avatarUrl.orEmpty(),
                            avatarItems = jogador?.avatarItensEquipados.orEmpty(),
                            avatarAsProfilePhoto = jogador?.avatarComoFotoPerfil == true,
                            onClick = { destinatarioUid = conversa.outroUid; erro = "" },
                            onPerfil = { perfilUid = conversa.outroUid },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Grupos", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = {
                        nomeGrupo = ""
                        descricaoGrupo = ""
                        editarGrupoPolicy = "creator"
                        enviarGrupoPolicy = "everyone"
                        membrosGrupo.clear()
                        grupoErro = ""
                        criarGrupoAberto = true
                    }) { Text("+ Criar grupo", color = Cores.Verde) }
                }
                if (grupos.isEmpty()) {
                    Text("Crie um grupo para conversar e manter um foguinho coletivo.", color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
                } else {
                    grupos.forEach { grupo ->
                            val diasAtivos = diasFoguinhoAtivos(grupo)
                            val nivelAtivo = if (diasAtivos == grupo.diasFoguinho) grupo.nivelFoguinho else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(13.dp))
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Groups, contentDescription = null, tint = Cores.Turquesa)
                            Column(Modifier.weight(1f).clickable { grupoUid = grupo.id; erro = "" }) {
                                Text(grupo.nome, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    grupo.descricao.ifBlank { "${grupo.participantes.size} pessoas · ${grupo.ultimaMensagem}" },
                                    color = Color.White.copy(alpha = 0.62f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(Icons.Filled.LocalFireDepartment, contentDescription = "$diasAtivos dias de sequência", tint = corFoguinho(nivelAtivo))
                            Text(diasAtivos.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            IconButton(onClick = { abrirConfiguracoesGrupo(grupo) }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Configurações de ${grupo.nome}", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }
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
                            username = jogador.username,
                            detalhe = "Nível ${jogador.nivel}",
                            hora = "",
                            avatarUrl = jogador.avatarUrl,
                            avatarItems = jogador.avatarItensEquipados,
                            avatarAsProfilePhoto = jogador.avatarComoFotoPerfil,
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
                jogadores = jogadores,
                uidAtual = uidAtual,
                chavePixAtual = chavePixAtual,
                podeCriarCobranca = modo == "Privado" && destinatarioUid.isNotBlank() && !emGrupo,
                onEnviarCobranca = { texto, aoSucesso -> enviarTextoChat(texto, null, aoSucesso) },
                onEncaminhar = onEncaminhar,
                ehGlobal = modo == "Global",
                rascunho = rascunho,
                onRascunhoChange = { rascunho = it.take(500); erro = "" },
                enviando = enviando,
                podeEnviarMensagem = !emGrupo || conversaGrupo?.politicaEnviar != "creator" || conversaGrupo.criadoPorUid == uidAtual,
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
                onEditarMensagem = { mensagemId, texto, concluir ->
                    val idConversa = chatId
                    if (idConversa == null) concluir(IllegalStateException("Conversa não encontrada."))
                    else onEditarMensagem(idConversa, mensagemId, texto, concluir)
                },
                cabecalho = {
                    if (emConversa) {
                        CabecalhoConversa(
                            nome = if (emGrupo) conversaGrupo?.nome ?: "Grupo" else destinatario?.apelido ?: "Jogador",
                            avatarUrl = if (emGrupo) "" else destinatario?.avatarUrl.orEmpty(),
                            avatarItems = destinatario?.avatarItensEquipados.orEmpty(),
                            avatarAsProfilePhoto = destinatario?.avatarComoFotoPerfil == true,
                            detalhe = if (emGrupo) "${conversaGrupo?.participantes?.size ?: 0} pessoas" else destinatario?.let { "Nível ${it.nivel}" } ?: "",
                            onVoltar = { if (emGrupo) grupoUid = "" else destinatarioUid = ""; erro = "" },
                            onPerfil = { perfilUid = destinatarioUid },
                        )
                        conversaSelecionada?.let { conversa ->
                            FoguinhoChip(
                                nome = conversa.nomeFoguinho,
                                dias = diasFoguinhoAtivos(conversa),
                                nivel = if (diasFoguinhoAtivos(conversa) == conversa.diasFoguinho) conversa.nivelFoguinho else 0,
                                onClick = {
                                    nomeFoguinhoEditavel = conversa.nomeFoguinho
                                    editarFoguinho = true
                                },
                            )
                        }
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

    if (criarGrupoAberto) {
        Dialog(
            onDismissRequest = { if (!criandoGrupo) criarGrupoAberto = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 760.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF20282C), Cores.Cartao, Cores.Fundo)))
                    .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(Cores.Turquesa.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Groups, contentDescription = null, tint = Cores.Turquesa, modifier = Modifier.size(26.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text("NOVO ESPAÇO", color = Cores.Turquesa, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Criar grupo", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    }
                    IconButton(onClick = { if (!criandoGrupo) criarGrupoAberto = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White.copy(alpha = 0.72f))
                    }
                }

                Text("IDENTIDADE", color = Cores.Verde, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = nomeGrupo,
                    onValueChange = { nomeGrupo = it.take(32); grupoErro = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nome do grupo") },
                    supportingText = { Text("2 a 32 caracteres") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = descricaoGrupo,
                    onValueChange = { descricaoGrupo = it.take(160) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Descrição") },
                    placeholder = { Text("Sobre o que é este grupo?") },
                    supportingText = { Text("${descricaoGrupo.length}/160") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PARTICIPANTES", color = Cores.Verde, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("${membrosGrupo.size} selecionados · até 19", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = Cores.Turquesa)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    jogadores.filter { it.uid != uidAtual }.forEach { jogador ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (jogador.uid in membrosGrupo) membrosGrupo.remove(jogador.uid)
                                else if (membrosGrupo.size < 19) membrosGrupo.add(jogador.uid)
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = jogador.uid in membrosGrupo,
                                onCheckedChange = { selecionado ->
                                    if (selecionado && membrosGrupo.size < 19) membrosGrupo.add(jogador.uid)
                                    else membrosGrupo.remove(jogador.uid)
                                },
                            )
                            AvatarChat(
                                jogador.apelido,
                                36.dp,
                                photoUrl = jogador.avatarUrl,
                                avatarItems = jogador.avatarItensEquipados,
                                avatarAsProfilePhoto = jogador.avatarComoFotoPerfil,
                            )
                            Column(Modifier.padding(start = 10.dp)) {
                                Text(jogador.apelido, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("@${jogador.username}", color = Color.White.copy(alpha = 0.56f), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Text("PERMISSÕES", color = Cores.Verde, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.055f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text("Quem pode editar o grupo?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OpcoesChat(listOf("Criador", "Todos"), if (editarGrupoPolicy == "creator") "Criador" else "Todos") {
                        editarGrupoPolicy = if (it == "Criador") "creator" else "members"
                    }
                    Text("Quem pode enviar mensagens?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OpcoesChat(listOf("Todos", "Criador"), if (enviarGrupoPolicy == "creator") "Criador" else "Todos") {
                        enviarGrupoPolicy = if (it == "Criador") "creator" else "everyone"
                    }
                }
                Text("A sequência diária avança quando todos os participantes enviam uma mensagem.", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
                if (grupoErro.isNotBlank()) Text(grupoErro, color = Cores.Laranja, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { criarGrupoAberto = false },
                        enabled = !criandoGrupo,
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancelar", color = Color.White.copy(alpha = 0.75f)) }
                    Button(
                        onClick = {
                            criandoGrupo = true
                            grupoErro = ""
                            onCriarGrupo(
                                nomeGrupo.trim(),
                                descricaoGrupo.trim(),
                                membrosGrupo.toList(),
                                editarGrupoPolicy,
                                enviarGrupoPolicy,
                                UUID.randomUUID().toString(),
                            ) { novoGrupo, error ->
                                criandoGrupo = false
                                if (error != null || novoGrupo == null) {
                                    grupoErro = error?.localizedMessage ?: "Não foi possível criar o grupo."
                                } else {
                                    criarGrupoAberto = false
                                    grupoUid = novoGrupo
                                }
                            }
                        },
                        enabled = nomeGrupo.trim().length in 2..32 && membrosGrupo.isNotEmpty() && !criandoGrupo,
                        modifier = Modifier.weight(1.3f),
                    ) { Text(if (criandoGrupo) "Criando..." else "Criar grupo") }
                }
            }
        }
    }

    val grupoEmConfiguracao = grupos.firstOrNull { it.id == grupoConfigId }
    if (grupoEmConfiguracao != null) {
        val podeEditarGrupo = grupoEmConfiguracao.criadoPorUid == uidAtual || grupoEmConfiguracao.politicaEditar == "members"
        Dialog(
            onDismissRequest = { if (!salvandoConfigGrupo) grupoConfigId = "" },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .heightIn(max = 720.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF20282C), Cores.Cartao, Cores.Fundo)))
                    .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(Cores.Turquesa.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Groups, contentDescription = null, tint = Cores.Turquesa, modifier = Modifier.size(24.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text("CONFIGURAÇÕES", color = Cores.Turquesa, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(grupoEmConfiguracao.nome, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { if (!salvandoConfigGrupo) grupoConfigId = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White.copy(alpha = 0.72f))
                    }
                }
                OutlinedTextField(
                    value = nomeGrupoEditavel,
                    onValueChange = { nomeGrupoEditavel = it.take(32); grupoErro = "" },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = podeEditarGrupo,
                    label = { Text("Nome do grupo") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = descricaoGrupoEditavel,
                    onValueChange = { descricaoGrupoEditavel = it.take(160); grupoErro = "" },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = podeEditarGrupo,
                    label = { Text("Descrição") },
                    supportingText = { Text("${descricaoGrupoEditavel.length}/160") },
                    maxLines = 3,
                )
                Text("PARTICIPANTES · ${grupoEmConfiguracao.participantes.size}", color = Cores.Verde, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.16f)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    grupoEmConfiguracao.participantes.forEach { uid ->
                        val jogador = jogadores.firstOrNull { it.uid == uid }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AvatarChat(
                                jogador?.apelido ?: "?",
                                34.dp,
                                photoUrl = jogador?.avatarUrl.orEmpty(),
                                avatarItems = jogador?.avatarItensEquipados.orEmpty(),
                                avatarAsProfilePhoto = jogador?.avatarComoFotoPerfil == true,
                            )
                            Column {
                                Text(jogador?.apelido ?: "Participante", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    when (uid) {
                                        grupoEmConfiguracao.criadoPorUid -> "Criador"
                                        uidAtual -> "Você"
                                        else -> jogador?.username?.let { "@$it" } ?: "Membro"
                                    },
                                    color = Color.White.copy(alpha = 0.58f),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.055f)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text("Quem pode editar informações?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OpcoesChat(listOf("Criador", "Todos"), if (editarPolicyEditavel == "creator") "Criador" else "Todos") {
                        if (podeEditarGrupo) editarPolicyEditavel = if (it == "Criador") "creator" else "members"
                    }
                    Text("Quem pode enviar mensagens?", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OpcoesChat(listOf("Todos", "Criador"), if (enviarPolicyEditavel == "creator") "Criador" else "Todos") {
                        if (podeEditarGrupo) enviarPolicyEditavel = if (it == "Criador") "creator" else "everyone"
                    }
                }
                if (!podeEditarGrupo) {
                    Text("As informações deste grupo só podem ser alteradas pelo criador.", color = Cores.Laranja, fontSize = 12.sp)
                }
                if (grupoErro.isNotBlank()) Text(grupoErro, color = Cores.Laranja, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { grupoConfigId = "" }, modifier = Modifier.weight(1f)) {
                        Text("Fechar", color = Color.White.copy(alpha = 0.75f))
                    }
                    Button(
                        onClick = {
                            salvandoConfigGrupo = true
                            onAtualizarGrupo(
                                grupoEmConfiguracao.id,
                                nomeGrupoEditavel.trim(),
                                descricaoGrupoEditavel.trim(),
                                editarPolicyEditavel,
                                enviarPolicyEditavel,
                            ) { error ->
                                salvandoConfigGrupo = false
                                if (error == null) grupoConfigId = ""
                                else grupoErro = error.localizedMessage ?: "Não foi possível atualizar o grupo."
                            }
                        },
                        enabled = podeEditarGrupo && nomeGrupoEditavel.trim().length in 2..32 && !salvandoConfigGrupo,
                        modifier = Modifier.weight(1.3f),
                    ) { Text(if (salvandoConfigGrupo) "Salvando..." else "Salvar alterações") }
                }
            }
        }
    }

    if (editarFoguinho && conversaSelecionada != null) {
        AlertDialog(
            onDismissRequest = { editarFoguinho = false },
            title = { Text("Nome do foguinho") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nomeFoguinhoEditavel,
                        onValueChange = { nomeFoguinhoEditavel = it.take(24) },
                        label = { Text("Nome compartilhado") },
                        singleLine = true,
                    )
                    Text("O nível evolui com os dias em que todos conversam.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        salvandoFoguinho = true
                        onRenomearFoguinho(conversaSelecionada.id, nomeFoguinhoEditavel.trim()) { error ->
                            salvandoFoguinho = false
                            if (error == null) editarFoguinho = false
                            else erro = error.localizedMessage ?: "Não foi possível renomear o foguinho."
                        }
                    },
                    enabled = nomeFoguinhoEditavel.trim().isNotEmpty() && !salvandoFoguinho,
                ) { Text(if (salvandoFoguinho) "Salvando..." else "Salvar") }
            },
            dismissButton = { TextButton(onClick = { editarFoguinho = false }) { Text("Cancelar") } },
        )
    }
}

private fun diasFoguinhoAtivos(conversa: ConversaChat): Int {
    val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date())
    val ontem = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000L))
    return if (conversa.ultimaSequenciaUtc == hoje || conversa.ultimaSequenciaUtc == ontem) conversa.diasFoguinho else 0
}

private fun corFoguinho(nivel: Int): Color = when (nivel) {
    0 -> Color.White.copy(alpha = 0.45f)
    1 -> Color(0xFFFFA044)
    2 -> Color(0xFFFF6B35)
    3 -> Color(0xFFFF3D5A)
    4 -> Color(0xFF42D9FF)
    else -> Color(0xFFFFD166)
}

@Composable
private fun FoguinhoChip(nome: String, dias: Int, nivel: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(corFoguinho(nivel).copy(alpha = 0.12f))
            .border(1.dp, corFoguinho(nivel).copy(alpha = 0.35f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = "Foguinho nível $nivel",
            tint = corFoguinho(nivel),
            modifier = Modifier.size((19 + nivel * 2).dp),
        )
        Column(Modifier.weight(1f)) {
            Text(nome, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (dias > 0) "$dias dias de sequência · nível $nivel" else "Conversem hoje para acender", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
        }
        Text("Editar", color = Cores.Turquesa, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
private fun AvatarChat(
    nome: String,
    tamanho: Dp,
    modifier: Modifier = Modifier,
    photoUrl: String = "",
    avatarItems: List<String> = emptyList(),
    avatarAsProfilePhoto: Boolean = false,
) {
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
        if (avatarAsProfilePhoto) {
            AvatarPersonagem(nome.trim().take(1).uppercase(), avatarItems, tamanho)
        } else if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Foto de $nome",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                nome.trim().take(1).uppercase(),
                color = Color.White,
                fontSize = (tamanho.value * 0.42f).sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun CabecalhoConversa(
    nome: String,
    detalhe: String,
    avatarUrl: String,
    avatarItems: List<String>,
    avatarAsProfilePhoto: Boolean,
    onVoltar: () -> Unit,
    onPerfil: () -> Unit,
) {
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
            AvatarChat(
                nome,
                42.dp,
                photoUrl = avatarUrl,
                avatarItems = avatarItems,
                avatarAsProfilePhoto = avatarAsProfilePhoto,
            )
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
                AvatarChat(
                    jogador.apelido,
                    88.dp,
                    photoUrl = jogador.avatarUrl,
                    avatarItems = jogador.avatarItensEquipados,
                    avatarAsProfilePhoto = jogador.avatarComoFotoPerfil,
                )
                Text(
                    jogador.apelido + if (ehVoce) " (você)" else "",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                if (jogador.username.isNotBlank()) Text("@${jogador.username}", color = Cores.Turquesa, fontSize = 13.sp)
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
    username: String = "",
    detalhe: String,
    hora: String,
    avatarUrl: String = "",
    avatarItems: List<String> = emptyList(),
    avatarAsProfilePhoto: Boolean = false,
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
        AvatarChat(
            nome,
            42.dp,
            Modifier.clickable(onClick = onPerfil),
            avatarUrl,
            avatarItems,
            avatarAsProfilePhoto,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(nome, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(username.takeIf { it.isNotBlank() }?.let { "@$it" }, detalhe).filterNotNull().joinToString(" · "),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    jogadores: List<JogadorRanking>,
    uidAtual: String,
    chavePixAtual: String,
    podeCriarCobranca: Boolean,
    onEnviarCobranca: (String, () -> Unit) -> Unit,
    onEncaminhar: (String?, String?, String, String, (Exception?) -> Unit) -> Unit,
    ehGlobal: Boolean,
    rascunho: String,
    onRascunhoChange: (String) -> Unit,
    enviando: Boolean,
    podeEnviarMensagem: Boolean,
    erro: String,
    onEnviar: (RespostaChat?, () -> Unit) -> Unit,
    onPerfil: (String) -> Unit,
    onApagarParaMim: (String) -> Unit,
    onApagarParaTodos: (String) -> Unit,
    onEditarMensagem: (String, String, (Exception?) -> Unit) -> Unit,
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
    var mensagemEditando by remember { mutableStateOf<MensagemChat?>(null) }
    var salvandoEdicao by remember { mutableStateOf(false) }
    var erroEdicao by rememberSaveable { mutableStateOf("") }
    var encaminhando by remember { mutableStateOf<MensagemChat?>(null) }
    var cobrancaAberta by rememberSaveable { mutableStateOf(false) }
    var valorCobranca by rememberSaveable { mutableStateOf("") }
    val valorCobrancaCentavos = remember(valorCobranca) { parseValorCobranca(valorCobranca) }
    val conteudoCobranca = remember(chavePixAtual, valorCobrancaCentavos) {
        valorCobrancaCentavos?.let { valor ->
            Uri.Builder()
                .scheme("zeca")
                .authority("pix")
                .appendQueryParameter("key", chavePixAtual)
                .appendQueryParameter("amount", valor.toString())
                .build()
                .toString()
        }
    }
    val imagemCobranca = remember(conteudoCobranca) {
        conteudoCobranca?.let(::criarQrCodeChat)
    }
    val invertida = remember(mensagens) { mensagens.asReversed() }
    val podeEnviar = rascunho.isNotBlank() && !enviando && podeEnviarMensagem

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
                    val avatarDoRanking = jogadores.firstOrNull { it.uid == mensagem.autorUid }
                    val mensagemTemAvatarSalvo = mensagem.avatarUrlAutor.isNotBlank()
                        || mensagem.avatarItensAutor.isNotEmpty()
                        || mensagem.avatarComoFotoAutor
                    val mensagemComAvatar = if (mensagemTemAvatarSalvo) mensagem else mensagem.copy(
                        avatarUrlAutor = avatarDoRanking?.avatarUrl.orEmpty(),
                        avatarItensAutor = avatarDoRanking?.avatarItensEquipados.orEmpty(),
                        avatarComoFotoAutor = avatarDoRanking?.avatarComoFotoPerfil == true,
                    )
                    BolhaMensagem(
                        mensagem = mensagemComAvatar,
                        mostrarAutor = ehGlobal && !mensagem.minha,
                        avatarUrl = mensagemComAvatar.avatarUrlAutor,
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
            if (podeCriarCobranca) {
                IconButton(
                    onClick = {
                        valorCobranca = ""
                        cobrancaAberta = true
                    },
                    enabled = !enviando,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(Icons.Filled.QrCode2, contentDescription = "Criar código de pagamento", tint = Cores.Verde)
                }
            }
            OutlinedTextField(
                value = rascunho,
                onValueChange = onRascunhoChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(campoFoco),
                enabled = podeEnviarMensagem,
                placeholder = { Text(if (podeEnviarMensagem) "Mensagem" else "Só o criador pode enviar") },
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

    if (cobrancaAberta) {
        Dialog(onDismissRequest = { cobrancaAberta = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF20282C), Cores.Cartao, Cores.Fundo)))
                    .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Criar cobrança", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                if (chavePixAtual.isBlank()) {
                    Text(
                        "Cadastre uma chave Pix na Carteira antes de criar uma cobrança.",
                        color = Cores.Laranja,
                        fontSize = 13.sp,
                    )
                } else {
                    OutlinedTextField(
                        value = valorCobranca,
                        onValueChange = { valorCobranca = it.filter { caractere -> caractere.isDigit() || caractere == ',' || caractere == '.' }.take(8) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Valor") },
                        prefix = { Text("R$ ") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (valorCobranca.isNotBlank() && valorCobrancaCentavos == null) {
                        Text("Informe um valor entre R$ 0,01 e R$ 10.000,00.", color = Cores.Laranja, fontSize = 12.sp)
                    }
                    imagemCobranca?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Prévia do código de pagamento",
                            modifier = Modifier.size(190.dp),
                        )
                        Text(
                            "Cobrança para saldo interno do Zeca. Não é um Pix bancário.",
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = {
                            val centavos = valorCobrancaCentavos ?: return@Button
                            val codigo = conteudoCobranca ?: return@Button
                            val texto = "COBRANÇA ZECA\nValor: ${formatarReais(centavos)}\n$codigo"
                            onEnviarCobranca(texto) { cobrancaAberta = false }
                        },
                        enabled = imagemCobranca != null && !enviando && podeEnviarMensagem,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (enviando) "Enviando..." else "Enviar cobrança no chat")
                    }
                }
                TextButton(onClick = { cobrancaAberta = false }) { Text("Cancelar", color = Color.White.copy(alpha = 0.75f)) }
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
            onEncaminhar = { encaminhando = alvo; menu = null },
            onPerfil = if (!alvo.minha) ({ onPerfil(alvo.autorUid); menu = null }) else null,
            onApagar = { apagando = alvo; menu = null },
            onEditar = if (alvo.minha && !alvo.apagadaParaTodos) {
                { mensagemEditando = alvo; erroEdicao = ""; menu = null }
            } else null,
        )
    }

    encaminhando?.let { alvo ->
        DialogoEncaminhar(
            jogadores = jogadores.filter { it.uid != uidAtual },
            onEscolher = { destinoUid ->
                encaminhando = null
                onEncaminhar(destinoUid, null, alvo.texto, UUID.randomUUID().toString()) { e ->
                    val aviso = if (e == null) "Mensagem encaminhada"
                    else e.localizedMessage ?: "Não foi possível encaminhar."
                    Toast.makeText(contexto, aviso, Toast.LENGTH_SHORT).show()
                }
            },
            onCancelar = { encaminhando = null },
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

    mensagemEditando?.let { alvo ->
        DialogoEditarMensagem(
            mensagem = alvo,
            salvando = salvandoEdicao,
            erro = erroEdicao,
            onCancelar = { if (!salvandoEdicao) mensagemEditando = null },
            onSalvar = { texto ->
                salvandoEdicao = true
                erroEdicao = ""
                onEditarMensagem(alvo.id, texto) { error ->
                    salvandoEdicao = false
                    if (error == null) mensagemEditando = null
                    else erroEdicao = error.localizedMessage ?: "Não foi possível editar a mensagem."
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BolhaMensagem(
    mensagem: MensagemChat,
    mostrarAutor: Boolean,
    avatarUrl: String,
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
                AvatarChat(
                    mensagem.autor,
                    32.dp,
                    Modifier.clickable(onClick = onPerfil),
                    avatarUrl,
                    mensagem.avatarItensAutor,
                    mensagem.avatarComoFotoAutor,
                )
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
                        mensagem.autor + mensagem.usernameAutor.takeIf { it.isNotBlank() }?.let { " · @$it" }.orEmpty(),
                        color = Cores.Turquesa,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClick = onPerfil),
                    )
                }
                if (mensagem.encaminhada && !mensagem.apagadaParaTodos) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Forward,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            "Encaminhada",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                        )
                    }
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
                    val cobranca = remember(mensagem.texto) { parseCodigoCobranca(mensagem.texto) }
                    if (cobranca == null) {
                        Text(mensagem.texto, color = Color.White, fontSize = 14.sp)
                    } else {
                        Text("COBRANÇA ZECA", color = Cores.Verde, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(formatarReais(cobranca.valorCentavos), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        val imagem = remember(cobranca.uri) { criarQrCodeChat(cobranca.uri) }
                        if (imagem != null) {
                            Image(
                                bitmap = imagem.asImageBitmap(),
                                contentDescription = "Código de pagamento ${formatarReais(cobranca.valorCentavos)}",
                                modifier = Modifier.align(Alignment.CenterHorizontally).size(176.dp),
                            )
                        }
                        Text(
                            "Escaneie pela Carteira para transferir no Zeca. Não é um Pix bancário.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                if (mensagem.editada || mensagem.enviadaEmMs > 0) {
                    Row(modifier = Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if (mensagem.editada) {
                            Text("editada", color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp)
                        }
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
    onEncaminhar: () -> Unit,
    onPerfil: (() -> Unit)?,
    onApagar: () -> Unit,
    onEditar: (() -> Unit)?,
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
                AcaoMenu(Icons.AutoMirrored.Filled.Forward, "Encaminhar", onEncaminhar)
                AcaoMenu(Icons.Filled.ContentCopy, "Copiar", onCopiar)
                AcaoMenu(Icons.Filled.Share, "Compartilhar", onCompartilhar)
                if (onPerfil != null) AcaoMenu(Icons.Filled.Person, "Ver perfil", onPerfil)
                if (onEditar != null) AcaoMenu(Icons.Filled.Edit, "Editar mensagem", onEditar)
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

@Composable
private fun DialogoEditarMensagem(
    mensagem: MensagemChat,
    salvando: Boolean,
    erro: String,
    onCancelar: () -> Unit,
    onSalvar: (String) -> Unit,
) {
    var texto by rememberSaveable(mensagem.id) { mutableStateOf(mensagem.texto) }
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = Color(0xFF1A2126),
        title = { Text("Editar mensagem", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                    enabled = !salvando,
                    supportingText = { Text("${texto.length}/500") },
                )
                if (erro.isNotBlank()) Text(erro, color = Color(0xFFFF8790), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSalvar(texto.trim()) },
                enabled = texto.trim().isNotEmpty() && !salvando,
            ) { Text(if (salvando) "Salvando..." else "Salvar", color = Cores.Verde) }
        },
        dismissButton = {
            TextButton(onClick = onCancelar, enabled = !salvando) { Text("Cancelar") }
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

private data class CodigoPagamentoChat(val uri: String, val valorCentavos: Long)

private fun parseValorCobranca(valor: String): Long? {
    val formatado = valor.trim().replace(',', '.')
    if (!Regex("^\\d{1,5}(\\.\\d{1,2})?$").matches(formatado)) return null
    val centavos = runCatching { BigDecimal(formatado).movePointRight(2).longValueExact() }.getOrNull()
    return centavos?.takeIf { it in 1..1_000_000 }
}

private fun parseCodigoCobranca(texto: String): CodigoPagamentoChat? = runCatching {
    if (!texto.startsWith("COBRANÇA ZECA\n")) return null
    val uriTexto = texto.lineSequence().firstOrNull { it.startsWith("zeca://pix?") } ?: return null
    val uri = Uri.parse(uriTexto)
    if (uri.scheme != "zeca" || uri.host != "pix" || uri.getQueryParameter("key").isNullOrBlank()) return null
    val valor = uri.getQueryParameter("amount")?.toLongOrNull()?.takeIf { it in 1..1_000_000 } ?: return null
    CodigoPagamentoChat(uriTexto, valor)
}.getOrNull()

private fun criarQrCodeChat(conteudo: String): Bitmap? = runCatching {
    val matriz = QRCodeWriter().encode(conteudo, BarcodeFormat.QR_CODE, 384, 384)
    val pixels = IntArray(matriz.width * matriz.height) { indice ->
        if (matriz[indice % matriz.width, indice / matriz.width]) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(matriz.width, matriz.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matriz.width, 0, 0, matriz.width, matriz.height)
    }
}.getOrNull()

@Composable
private fun DialogoEncaminhar(
    jogadores: List<JogadorRanking>,
    onEscolher: (String?) -> Unit,
    onCancelar: () -> Unit,
) {
    Dialog(onDismissRequest = onCancelar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A2126), RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                .padding(vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Encaminhar para",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCancelar) {
                    Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = Color.White)
                }
            }
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "global") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEscolher(null) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AvatarChat("#", 40.dp)
                        Column {
                            Text("Sala global", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Todos os jogadores veem", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                        }
                    }
                }
                items(jogadores, key = { it.uid }) { jogador ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEscolher(jogador.uid) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AvatarChat(
                            jogador.apelido,
                            40.dp,
                            photoUrl = jogador.avatarUrl,
                            avatarItems = jogador.avatarItensEquipados,
                            avatarAsProfilePhoto = jogador.avatarComoFotoPerfil,
                        )
                        Column {
                            Text(
                                jogador.apelido,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("Nível ${jogador.nivel}", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}