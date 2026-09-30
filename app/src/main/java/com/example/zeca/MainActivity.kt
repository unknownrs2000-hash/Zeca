package com.example.zeca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.example.zeca.ui.TelaCarteira
import com.example.zeca.ui.TelaConfigurarPerfil
import com.example.zeca.ui.TelaAutenticacao
import com.example.zeca.ui.TelaAdmin
import com.example.zeca.ui.TelaChat
import com.example.zeca.ui.TelaInicio
import com.example.zeca.ui.TelaJogos
import com.example.zeca.ui.TelaLoja
import com.example.zeca.ui.TelaPerfil
import com.example.zeca.ui.theme.CassinoTheme
import com.example.zeca.ui.theme.Cores
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.math.BigDecimal
import java.text.NumberFormat

private fun formatarValorNotificacao(centavos: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(centavos, 2))

enum class Aba(val titulo: String, val icone: ImageVector) {
    Inicio("Início", Icons.Filled.Home),
    Jogos("Jogos", Icons.Filled.Casino),
    Carteira("Carteira", Icons.Filled.AccountBalanceWallet),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    Perfil("Perfil", Icons.Filled.Person),
    Admin("Admin", Icons.Filled.Settings),
}

data class Movimento(
    val titulo: String,
    val variacaoCentavos: Long,
    val horario: String = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date()),
    val id: String = "",
    val ehTransferenciaPix: Boolean = false,
    val ehPremioNivel: Boolean = false,
)

private data class NotificacaoApp(
    val id: String,
    val titulo: String,
    val detalhe: String,
    val aba: Aba,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CassinoTheme { CassinoApp() } }
    }
}

@Composable
fun CassinoApp() {
    val auth = remember { FirebaseRepository.auth }
    var usuario by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { usuario = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val usuarioAtual = usuario
    if (usuarioAtual == null) {
        TelaAutenticacao(onAutenticado = { usuario = it })
    } else {
        AppAutenticado(usuarioAtual)
    }
}

@Composable
private fun AppAutenticado(usuario: FirebaseUser) {
    val contexto = LocalContext.current
    var aba by rememberSaveable { mutableStateOf(Aba.Inicio) }
    var ehAdmin by remember(usuario.uid) { mutableStateOf(false) }
    var mostrarLoja by rememberSaveable { mutableStateOf(false) }
    var perfil by remember(usuario.uid) { mutableStateOf<PerfilJogador?>(null) }
    var erroPerfil by rememberSaveable { mutableStateOf("") }
    val ranking = remember { mutableStateListOf<JogadorRanking>() }
    val movimentos = remember { mutableStateListOf<Movimento>() }
    val itensComprados = remember { mutableStateListOf<String>() }
    val notificacoes = remember { mutableStateListOf<NotificacaoApp>() }
    val convitesCaboNotificados = remember(usuario.uid) { mutableSetOf<String>() }
    val stateHolder = rememberSaveableStateHolder()

    LaunchedEffect(usuario.uid) {
        FirebaseRepository.verificarAdmin { ehAdmin = it }
    }

    fun notificar(notificacao: NotificacaoApp) {
        if (notificacoes.none { it.id == notificacao.id }) {
            notificacoes.add(notificacao)
            if (notificacoes.size > 4) notificacoes.removeAt(0)
        }
    }

    LaunchedEffect(usuario.uid) {
        while (true) {
            FirebaseRepository.listarSalasCaboGuerra { rooms, error ->
                if (error == null) {
                    rooms.filter { it.conviteParaMim && it.status == "waiting" }.forEach { room ->
                        if (convitesCaboNotificados.add(room.id)) {
                            notificar(
                                NotificacaoApp(
                                    id = "tug-invite:${room.id}",
                                    titulo = "Convite para Cabo de Guerra",
                                    detalhe = "${room.criadorNome} convidou você · aposta ${formatarValorNotificacao(room.apostaCentavos)}",
                                    aba = Aba.Jogos,
                                ),
                            )
                        }
                    }
                }
            }
            delay(15_000)
        }
    }

    DisposableEffect(usuario.uid) {
        val idsMovimentos = mutableSetOf<String>()
        var movimentosCarregados = false
        val profileRegistration = FirebaseRepository.observarPerfil(usuario.uid) { perfil = it }
        val rankingRegistration = FirebaseRepository.observarRanking { jogadores ->
            ranking.clear()
            ranking.addAll(jogadores)
        }
        val movementsRegistration = FirebaseRepository.observarMovimentos(usuario.uid) { recentes, fromCache ->
            movimentos.clear()
            movimentos.addAll(recentes)
            if (fromCache) return@observarMovimentos
            if (movimentosCarregados) {
                recentes.forEach { movimento ->
                    if (idsMovimentos.add(movimento.id) && movimento.variacaoCentavos > 0L) {
                        val pixRecebido = movimento.ehTransferenciaPix
                        val premioNivel = movimento.ehPremioNivel
                        notificar(
                            NotificacaoApp(
                                id = "movimento:${movimento.id}",
                                titulo = when {
                                    premioNivel -> "Novo nível alcançado!"
                                    pixRecebido -> "Pix recebido"
                                    else -> "Saldo recebido"
                                },
                                detalhe = "${movimento.titulo} · ${formatarValorNotificacao(movimento.variacaoCentavos)}",
                                aba = Aba.Carteira,
                            ),
                        )
                    } else {
                        idsMovimentos.add(movimento.id)
                    }
                }
            } else {
                idsMovimentos.addAll(recentes.map { it.id })
                movimentosCarregados = true
            }
        }
        val idsMensagensPrivadas = mutableMapOf<String, String>()
        var conversasCarregadas = false
        val conversationsRegistration = FirebaseRepository.observarConversasChat(usuario.uid) { conversas, erro, fromCache ->
            if (erro == null && !fromCache) {
                if (!conversasCarregadas) {
                    conversas.forEach { idsMensagensPrivadas[it.id] = it.ultimaMensagemId }
                    conversasCarregadas = true
                } else {
                    conversas.forEach { conversa ->
                        val mensagemAnterior = idsMensagensPrivadas[conversa.id]
                        if (conversa.ultimaMensagemId.isNotBlank()
                            && conversa.ultimaMensagemId != mensagemAnterior
                            && conversa.ultimoRemetenteUid != usuario.uid) {
                            notificar(
                                NotificacaoApp(
                                    id = "mensagem:${conversa.id}:${conversa.ultimaMensagemId}",
                                    titulo = "Nova mensagem",
                                    detalhe = conversa.ultimaMensagem.take(90),
                                    aba = Aba.Chat,
                                ),
                            )
                        }
                        idsMensagensPrivadas[conversa.id] = conversa.ultimaMensagemId
                    }
                }
            }
        }
        val idsMensagensGlobais = mutableSetOf<String>()
        var chatGlobalCarregado = false
        val globalMessagesRegistration = FirebaseRepository.observarMensagensChat("global", usuario.uid) { mensagens, erro, fromCache ->
            if (erro == null && !fromCache) {
                if (!chatGlobalCarregado) {
                    idsMensagensGlobais.addAll(mensagens.map { it.id })
                    chatGlobalCarregado = true
                } else {
                    mensagens.forEach { mensagem ->
                        if (idsMensagensGlobais.add(mensagem.id) && !mensagem.minha) {
                            notificar(
                                NotificacaoApp(
                                    id = "mensagem:global:${mensagem.id}",
                                    titulo = "Mensagem no chat global",
                                    detalhe = "${mensagem.autor}: ${mensagem.texto.take(75)}",
                                    aba = Aba.Chat,
                                ),
                            )
                        }
                    }
                }
            }
        }
        FirebaseRepository.garantirPerfil(usuario) { error ->
            erroPerfil = error?.localizedMessage.orEmpty()
        }
        onDispose {
            profileRegistration.remove()
            rankingRegistration.remove()
            movementsRegistration.remove()
            conversationsRegistration.remove()
            globalMessagesRegistration.remove()
        }
    }

    val notificacaoAtual = notificacoes.firstOrNull()
    LaunchedEffect(notificacaoAtual?.id) {
        val atual = notificacaoAtual ?: return@LaunchedEffect
        delay(5_000)
        if (notificacoes.firstOrNull()?.id == atual.id) notificacoes.removeAt(0)
    }

    val jogador = perfil
    if (jogador == null) {
        Box(Modifier.fillMaxSize().background(Cores.Fundo), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(color = Cores.Verde)
                androidx.compose.material3.Text(
                    erroPerfil.ifBlank { "Carregando seu perfil..." },
                    modifier = Modifier.padding(20.dp),
                    color = Color.White,
                )
                if (erroPerfil.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = { FirebaseRepository.sair() }) {
                        androidx.compose.material3.Text("Sair")
                    }
                }
            }
        }
    } else if (!jogador.profileSetupComplete || jogador.username.isBlank()) {
        TelaConfigurarPerfil(
            nomeInicial = jogador.apelido,
            avatarUrlInicial = jogador.avatarUrl,
            onEnviarFoto = { uri, concluir ->
                FirebaseRepository.enviarFotoPerfil(uri, contexto.contentResolver) { url, error ->
                    concluir(url, error?.localizedMessage)
                }
            },
            onSalvarPerfil = { username, displayName, avatarUrl, avatarComoFoto, concluir ->
                FirebaseRepository.atualizarPerfil(username, displayName, avatarUrl, avatarComoFoto) { error ->
                    concluir(error?.localizedMessage)
                }
            },
        )
    } else {
        Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
            Crossfade(targetState = aba, label = "aba") { atual ->
                stateHolder.SaveableStateProvider(atual.name) {
                    when (atual) {
                        Aba.Inicio -> TelaInicio(
                            saldoCentavos = jogador.saldoCentavos,
                            atividadeRecente = movimentos.take(3).map { it.titulo },
                            ranking = ranking,
                            erroSincronizacao = erroPerfil,
                            ganhoTotalCentavos = jogador.ganhoTotalCentavos,
                            perdaTotalCentavos = jogador.perdaTotalCentavos,
                            onCarregarMissoes = { concluir -> FirebaseRepository.carregarMissoes(concluir) },
                            onAbrirAba = { aba = it },
                            onAbrirLoja = {
                                mostrarLoja = true
                                aba = Aba.Perfil
                            },
                            onBuscarPerfil = { uid, concluir ->
                                FirebaseRepository.buscarPerfilPublico(uid, concluir)
                            },
                        )
                        Aba.Jogos -> TelaJogos(
                            saldoCentavos = jogador.saldoCentavos,
                            partidas = jogador.partidas,
                            uidAtual = usuario.uid,
                            jogadores = ranking,
                            onCarregarConfiguracaoMinas = { concluir ->
                                FirebaseRepository.carregarConfiguracaoMinas(concluir)
                            },
                            onCarregarMinasAtiva = { concluir ->
                                FirebaseRepository.carregarMinasAtiva(concluir)
                            },
                            onCarregarPartidasEsportivas = { concluir ->
                                FirebaseRepository.carregarPartidasEsportivas(concluir)
                            },
                            onCarregarApostasEsportivas = { concluir ->
                                FirebaseRepository.listarApostasEsportivas(concluir)
                            },
                            onApostarEsportiva = { pernas, valor, requestId, concluir ->
                                FirebaseRepository.apostarPartidaEsportiva(pernas, valor, requestId, concluir)
                            },
                            onLiquidarApostasEsportivas = { concluir ->
                                FirebaseRepository.liquidarApostasEsportivas(concluir)
                            },
                            onCarregarSalasCaboGuerra = { concluir -> FirebaseRepository.listarSalasCaboGuerra(concluir) },
                            onCriarSalaCaboGuerra = { aposta, convites, senha, requestId, concluir ->
                                FirebaseRepository.criarSalaCaboGuerra(aposta, convites, senha, requestId, concluir)
                            },
                            onEntrarSalaCaboGuerra = { roomId, senha, requestId, concluir ->
                                FirebaseRepository.entrarSalaCaboGuerra(roomId, senha, requestId, concluir)
                            },
                            onGerenciarSalaCaboGuerra = { roomId, acao, requestId, targetUid, aposta, senha, concluir ->
                                FirebaseRepository.gerenciarSalaCaboGuerra(roomId, acao, requestId, targetUid, aposta, senha, concluir)
                            },
                            onIniciarSalaCaboGuerra = { roomId, concluir -> FirebaseRepository.iniciarSalaCaboGuerra(roomId, concluir) },
                            onPuxarCordaCaboGuerra = { roomId, requestId, concluir ->
                                FirebaseRepository.puxarCordaCaboGuerra(roomId, requestId, concluir)
                            },
                            onIniciarMinas = { aposta, minas, requestId, concluir ->
                                FirebaseRepository.iniciarMinas(aposta, minas, requestId, concluir)
                            },
                            onRevelarMinas = { gameId, casa, requestId, concluir ->
                                FirebaseRepository.revelarCasaMinas(gameId, casa, requestId, concluir)
                            },
                            onSacarMinas = { gameId, requestId, concluir ->
                                FirebaseRepository.sacarMinas(gameId, requestId, concluir)
                            },
                            onJogar = { jogo, aposta, tipo, selecao, requestId, concluir ->
                                FirebaseRepository.jogar(jogo, aposta, tipo, selecao, requestId) { resultado, error ->
                                    concluir(resultado, error)
                                }
                            },
                            onIniciarCrash = { aposta, requestId, concluir ->
                                FirebaseRepository.iniciarCrash(aposta, requestId) { sessao, error -> concluir(sessao, error) }
                            },
                            onSacarCrash = { gameId, requestId, concluir ->
                                FirebaseRepository.sacarCrash(gameId, requestId) { resultado, error -> concluir(resultado, error) }
                            },
                            onIniciarBlackjack = { aposta, requestId, concluir ->
                                FirebaseRepository.iniciarBlackjack(aposta, requestId) { estado, error -> concluir(estado, error) }
                            },
                            onAcaoBlackjack = { gameId, acao, requestId, concluir ->
                                FirebaseRepository.acaoBlackjack(gameId, acao, requestId) { estado, error -> concluir(estado, error) }
                            },
                        )
                        Aba.Carteira -> TelaCarteira(
                            saldoCentavos = jogador.saldoCentavos,
                            chavePix = jogador.chavePix,
                            tipoChavePix = jogador.tipoChavePix,
                            historico = movimentos,
                            emailConta = usuario.email.orEmpty(),
                            emailVerificadoInicial = usuario.isEmailVerified,
                            onReenviarVerificacao = { concluir ->
                                FirebaseRepository.reenviarVerificacaoEmail { error ->
                                    concluir(error?.localizedMessage)
                                }
                            },
                            onConferirVerificacao = { concluir ->
                                FirebaseRepository.conferirEmailVerificado { verificado, error ->
                                    concluir(verificado, error?.localizedMessage)
                                }
                            },
                            onSalvarChave = { tipo, chave, concluir ->
                                FirebaseRepository.registrarChavePix(tipo, chave) { error ->
                                    concluir(error?.localizedMessage)
                                }
                            },
                            onBuscarDestinatario = { chave, concluir ->
                                FirebaseRepository.buscarChavePix(chave) { destino, error ->
                                    concluir(destino, error?.localizedMessage)
                                }
                            },
                            onTransferir = { chave, valor, requestId, concluir ->
                                FirebaseRepository.transferirPorPix(chave, valor, requestId) { resultado, error ->
                                    concluir(resultado, error?.localizedMessage)
                                }
                            },
                        )
                        Aba.Chat -> TelaChat(
                            uidAtual = usuario.uid,
                            chavePixAtual = jogador.chavePix,
                            jogadores = ranking,
                            onEnviar = { destinatarioUid, grupoId, texto, requestId, resposta, concluir ->
                                FirebaseRepository.enviarMensagemChat(
                                    destinatarioUid,
                                    texto,
                                    requestId,
                                    resposta,
                                    chatId = grupoId,
                                ) { _, error ->
                                    concluir(error)
                                }
                            },
                            onEnviarAudio = { destinatarioUid, grupoId, arquivo, duracaoMs, requestId, concluir ->
                                FirebaseRepository.enviarAudioChat(
                                    arquivo,
                                    duracaoMs,
                                    destinatarioUid,
                                    grupoId,
                                    requestId,
                                    concluir,
                                )
                            },
                            onEncaminhar = { destinatarioUid, grupoId, texto, requestId, concluir ->
                                FirebaseRepository.enviarMensagemChat(
                                    destinatarioUid,
                                    texto,
                                    requestId,
                                    null,
                                    encaminhada = true,
                                    chatId = grupoId,
                                ) { _, error ->
                                    concluir(error)
                                }
                            },
                            onCriarGrupo = { nome, descricao, membros, editPolicy, sendPolicy, requestId, concluir ->
                                FirebaseRepository.criarGrupo(nome, descricao, membros, editPolicy, sendPolicy, requestId) { id, error ->
                                    concluir(id, error)
                                }
                            },
                            onAtualizarGrupo = { chatId, nome, descricao, editPolicy, sendPolicy, concluir ->
                                FirebaseRepository.atualizarGrupo(chatId, nome, descricao, editPolicy, sendPolicy, concluir)
                            },
                            onGerenciarMembros = { chatId, action, targetUid, memberUids, concluir ->
                                FirebaseRepository.gerenciarMembrosGrupo(chatId, action, targetUid, memberUids, concluir)
                            },
                            onEnviarFotoGrupo = { chatId, uri, concluir ->
                                FirebaseRepository.enviarFotoGrupo(uri, chatId, contexto.contentResolver) { error ->
                                    concluir(error?.localizedMessage)
                                }
                            },
                            onRenomearFoguinho = { chatId, nome, concluir ->
                                FirebaseRepository.renomearFoguinho(chatId, nome, concluir)
                            },
                            onApagarParaMim = { chatId, mensagemId, concluir ->
                                FirebaseRepository.apagarMensagemParaMim(chatId, mensagemId, concluir)
                            },
                            onApagarParaTodos = { chatId, mensagemId, concluir ->
                                FirebaseRepository.apagarMensagemParaTodos(chatId, mensagemId, concluir)
                            },
                            onEditarMensagem = { chatId, mensagemId, texto, concluir ->
                                FirebaseRepository.editarMensagemChat(chatId, mensagemId, texto, concluir)
                            },
                            onBuscarPerfil = { uid, concluir ->
                                FirebaseRepository.buscarPerfilPublico(uid, concluir)
                            },
                        )
                        Aba.Admin -> if (ehAdmin) {
                            TelaAdmin(
                                onCarregarUsuarios = { cursor, concluir ->
                                    FirebaseRepository.listarUsuariosAdmin(cursor, concluir)
                                },
                                onCarregarDetalhes = { uid, concluir ->
                                    FirebaseRepository.carregarDetalhesAdmin(uid, concluir)
                                },
                                onCarregarConfiguracao = { concluir ->
                                    FirebaseRepository.carregarConfiguracaoMinas(concluir)
                                },
                                onAtualizarConfiguracao = { configuracao, motivo, requestId, concluir ->
                                    FirebaseRepository.atualizarConfiguracaoMinasAdmin(configuracao, motivo, requestId, concluir)
                                },
                                onAjustarSaldo = { uid, delta, motivo, requestId, concluir ->
                                    FirebaseRepository.ajustarSaldoAdmin(uid, delta, motivo, requestId, concluir)
                                },
                                onAtualizarInventario = { uid, acao, itemId, motivo, requestId, concluir ->
                                    FirebaseRepository.atualizarInventarioAdmin(uid, acao, itemId, motivo, requestId, concluir)
                                },
                                onDefinirBloqueio = { uid, bloqueado, motivo, requestId, concluir ->
                                    FirebaseRepository.definirBloqueioAdmin(uid, bloqueado, motivo, requestId, concluir)
                                },
                                onExcluirUsuario = { uid, confirmUid, motivo, requestId, concluir ->
                                    FirebaseRepository.excluirUsuarioAdmin(uid, confirmUid, motivo, requestId, concluir)
                                },
                            )
                        } else {
                            aba = Aba.Inicio
                        }
                        Aba.Perfil -> if (mostrarLoja) {
                            TelaLoja(
                                saldoCentavos = jogador.saldoCentavos,
                                itensComprados = jogador.inventario,
                                apelido = jogador.apelido,
                                username = jogador.username,
                                avatarUrl = jogador.avatarUrl,
                                avatarItensEquipados = jogador.avatarItensEquipados,
                                avatarComoFotoPerfil = jogador.avatarComoFotoPerfil,
                                molduraEquipada = jogador.molduraEquipada,
                                onVoltar = { mostrarLoja = false },
                                onComprar = { itemId, concluir ->
                                    FirebaseRepository.comprarCosmetico(itemId, concluir)
                                },
                                onEquiparAvatar = { slot, itemId, concluir ->
                                    FirebaseRepository.equiparItemAvatar(slot, itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onEquiparMoldura = { itemId, concluir ->
                                    FirebaseRepository.equiparMoldura(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                            )
                        } else {
                            TelaPerfil(
                                apelido = jogador.apelido,
                                username = jogador.username,
                                email = jogador.email,
                                saldoCentavos = jogador.saldoCentavos,
                                nivel = jogador.nivel,
                                partidas = jogador.partidas,
                                vitorias = jogador.vitorias,
                                avatarUrl = jogador.avatarUrl,
                                avatarItensEquipados = jogador.avatarItensEquipados,
                                avatarComoFotoPerfil = jogador.avatarComoFotoPerfil,
                                inventario = jogador.inventario,
                                molduraEquipada = jogador.molduraEquipada,
                                onEscolherMoldura = { itemId, concluir ->
                                    FirebaseRepository.equiparMoldura(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onEnviarFoto = { uri, concluir ->
                                    FirebaseRepository.enviarFotoPerfil(uri, contexto.contentResolver) { url, error ->
                                        concluir(url, error?.localizedMessage)
                                    }
                                },
                                onSalvarPerfil = { username, nome, avatarUrl, avatarComoFoto, concluir ->
                                    FirebaseRepository.atualizarPerfil(username, nome, avatarUrl, avatarComoFoto) { error -> concluir(error?.localizedMessage) }
                                },
                                onEquiparItemAvatar = { slot, itemId, concluir ->
                                    FirebaseRepository.equiparItemAvatar(slot, itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onAbrirLoja = { mostrarLoja = true },
                                onSair = { FirebaseRepository.sair() },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = notificacaoAtual != null,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().zIndex(1f),
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            ) {
                notificacaoAtual?.let { notificacao ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Cores.Cartao)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                            .clickable {
                                aba = notificacao.aba
                                notificacoes.removeAll { it.id == notificacao.id }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = Cores.Verde)
                        Column(Modifier.weight(1f)) {
                            Text(notificacao.titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(notificacao.detalhe, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2)
                        }
                        IconButton(onClick = { notificacoes.removeAll { it.id == notificacao.id } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar notificação", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            BarraInferior(
                atual = aba,
                mostrarAdmin = ehAdmin,
                onSelecionar = {
                    aba = it
                    mostrarLoja = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun BarraInferior(
    atual: Aba,
    onSelecionar: (Aba) -> Unit,
    modifier: Modifier = Modifier,
    mostrarAdmin: Boolean = false,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(64.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Cores.Barra.copy(alpha = 0.88f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.26f), CircleShape)
            .padding(5.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Aba.entries.filter { mostrarAdmin || it != Aba.Admin }.forEach { aba ->
            val selecionada = aba == atual
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selecionada) Color.White.copy(alpha = 0.92f)
                        else Color.Transparent,
                    )
                    .clickable { onSelecionar(aba) }
                    .padding(horizontal = if (selecionada) 14.dp else 11.dp, vertical = 11.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = aba.icone,
                    contentDescription = aba.titulo,
                    tint = if (selecionada) Color(0xFF111418) else Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.size(24.dp),
                )
                if (selecionada) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        aba.titulo,
                        color = Color(0xFF111418),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}