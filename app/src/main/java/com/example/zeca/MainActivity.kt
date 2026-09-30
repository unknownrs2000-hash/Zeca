package com.example.zeca

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ui.TelaCarteira
import com.example.zeca.ui.TelaAutenticacao
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

enum class Aba(val titulo: String, val icone: ImageVector) {
    Inicio("Início", Icons.Filled.Home),
    Jogos("Jogos", Icons.Filled.Casino),
    Carteira("Carteira", Icons.Filled.AccountBalanceWallet),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    Perfil("Perfil", Icons.Filled.Person),
}

data class Movimento(val titulo: String, val variacaoCentavos: Long, val horario: String =
    SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date()))

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
    var aba by rememberSaveable { mutableStateOf(Aba.Inicio) }
    var mostrarLoja by rememberSaveable { mutableStateOf(false) }
    var perfil by remember(usuario.uid) { mutableStateOf<PerfilJogador?>(null) }
    var erroPerfil by rememberSaveable { mutableStateOf("") }
    val ranking = remember { mutableStateListOf<JogadorRanking>() }
    val movimentos = remember { mutableStateListOf<Movimento>() }
    val itensComprados = remember { mutableStateListOf<String>() }
    val stateHolder = rememberSaveableStateHolder()

    DisposableEffect(usuario.uid) {
        val profileRegistration = FirebaseRepository.observarPerfil(usuario.uid) { perfil = it }
        val rankingRegistration = FirebaseRepository.observarRanking { jogadores ->
            ranking.clear()
            ranking.addAll(jogadores)
        }
        val movementsRegistration = FirebaseRepository.observarMovimentos(usuario.uid) { recentes ->
            movimentos.clear()
            movimentos.addAll(recentes)
        }
        FirebaseRepository.garantirPerfil(usuario) { error ->
            if (error != null) erroPerfil = error.localizedMessage ?: "Não foi possível carregar seu perfil."
        }
        onDispose {
            profileRegistration.remove()
            rankingRegistration.remove()
            movementsRegistration.remove()
        }
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
                            jogadores = ranking,
                            onEnviar = { destinatarioUid, texto, requestId, resposta, concluir ->
                                FirebaseRepository.enviarMensagemChat(destinatarioUid, texto, requestId, resposta) { _, error ->
                                    concluir(error)
                                }
                            },
                            onEncaminhar = { destinatarioUid, texto, requestId, concluir ->
                                FirebaseRepository.enviarMensagemChat(destinatarioUid, texto, requestId, null, true) { _, error ->
                                    concluir(error)
                                }
                            },
                            onApagarParaMim = { chatId, mensagemId, concluir ->
                                FirebaseRepository.apagarMensagemParaMim(chatId, mensagemId, concluir)
                            },
                            onApagarParaTodos = { chatId, mensagemId, concluir ->
                                FirebaseRepository.apagarMensagemParaTodos(chatId, mensagemId, concluir)
                            },
                            onBuscarPerfil = { uid, concluir ->
                                FirebaseRepository.buscarPerfilPublico(uid, concluir)
                            },
                        )
                        Aba.Perfil -> if (mostrarLoja) {
                            TelaLoja(
                                saldoCentavos = jogador.saldoCentavos,
                                itensComprados = jogador.inventario,
                                onVoltar = { mostrarLoja = false },
                                onComprar = { itemId, concluir ->
                                    FirebaseRepository.comprarCosmetico(itemId, concluir)
                                },
                            )
                        } else {
                            TelaPerfil(
                                apelido = jogador.apelido,
                                email = jogador.email,
                                saldoCentavos = jogador.saldoCentavos,
                                nivel = jogador.nivel,
                                partidas = jogador.partidas,
                                vitorias = jogador.vitorias,
                                avatarUrl = jogador.avatarUrl,
                                inventario = jogador.inventario,
                                molduraEquipada = jogador.molduraEquipada,
                                onEscolherMoldura = { itemId, concluir ->
                                    FirebaseRepository.equiparMoldura(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onSalvarApelido = { nome, concluir ->
                                    FirebaseRepository.atualizarApelido(nome) { error -> concluir(error?.localizedMessage) }
                                },
                                onAbrirLoja = { mostrarLoja = true },
                                onSair = { FirebaseRepository.sair() },
                            )
                        }
                    }
                }
            }
            BarraInferior(
                atual = aba,
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
fun BarraInferior(atual: Aba, onSelecionar: (Aba) -> Unit, modifier: Modifier = Modifier) {
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
        Aba.entries.forEach { aba ->
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