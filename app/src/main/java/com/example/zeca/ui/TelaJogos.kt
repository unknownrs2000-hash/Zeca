package com.example.zeca.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.CartaBlackjack
import com.example.zeca.EstadoBlackjack
import com.example.zeca.ResultadoCrash
import com.example.zeca.ResultadoJogo
import com.example.zeca.SessaoCrash
import com.example.zeca.ui.theme.Cores
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.UUID
import kotlin.math.exp

@Composable
fun TelaJogos(
    saldoCentavos: Long,
    partidas: Int,
    onJogar: (String, Long, String, String, String, (ResultadoJogo?, Exception?) -> Unit) -> Unit,
    onIniciarCrash: (Long, String, (SessaoCrash?, Exception?) -> Unit) -> Unit,
    onSacarCrash: (String, String, (ResultadoCrash?, Exception?) -> Unit) -> Unit,
    onIniciarBlackjack: (Long, String, (EstadoBlackjack?, Exception?) -> Unit) -> Unit,
    onAcaoBlackjack: (String, String, String, (EstadoBlackjack?, Exception?) -> Unit) -> Unit,
) {
    var jogo by rememberSaveable { mutableStateOf("Slots") }
    var apostaTexto by rememberSaveable { mutableStateOf("10,00") }
    var tipoRoleta by rememberSaveable { mutableStateOf("Cor") }
    var selecaoRoleta by rememberSaveable { mutableStateOf("Vermelho") }
    var numeroRoleta by rememberSaveable { mutableStateOf("17") }
    var ladoMoeda by rememberSaveable { mutableStateOf("Cara") }
    var numeroDado by rememberSaveable { mutableStateOf("1") }
    var paridadeDado by rememberSaveable { mutableStateOf("Par") }
    var casaMinas by rememberSaveable { mutableStateOf("1") }
    var resultado by rememberSaveable { mutableStateOf("Escolha um jogo para começar") }
    var mensagem by rememberSaveable { mutableStateOf("") }
    var ocupado by rememberSaveable { mutableStateOf(false) }
    var rodada by remember { mutableStateOf(0) }
    var lucroUltimo by remember { mutableStateOf(0L) }
    var atrasoSaldo by remember { mutableStateOf(0L) }
    var saldoExibido by remember { mutableStateOf(saldoCentavos) }
    var servidorLento by remember { mutableStateOf(false) }
    var fimCrash by remember { mutableStateOf<String?>(null) }
    var sessaoCrash by remember { mutableStateOf<SessaoCrash?>(null) }
    var crashBps by remember { mutableStateOf(100) }
    var saqueCrashPendente by remember { mutableStateOf(false) }
    var estadoBlackjack by remember { mutableStateOf<EstadoBlackjack?>(null) }
    val historicoRoleta = remember { mutableStateListOf<Int>() }

    val apostaCentavos = parseValorCentavos(apostaTexto)
    val apostaValida = apostaCentavos != null && apostaCentavos in 100..1_000_000 && apostaCentavos <= saldoCentavos
    val apostaTravada = when (jogo) {
        "Crash" -> sessaoCrash != null
        "Blackjack" -> estadoBlackjack?.status == "active"
        else -> false
    }

    LaunchedEffect(sessaoCrash?.gameId, saqueCrashPendente) {
        val sessao = sessaoCrash ?: return@LaunchedEffect
        if (saqueCrashPendente) return@LaunchedEffect
        while (true) {
            val elapsedMs = (System.currentTimeMillis() - sessao.iniciadoEmMs).coerceAtLeast(0L)
            crashBps = (100 * exp(elapsedMs / 5_000.0)).toInt().coerceIn(100, 1_000_000)
            delay(80)
        }
    }

    LaunchedEffect(saldoCentavos, ocupado) {
        if (!ocupado) {
            delay(atrasoSaldo)
            saldoExibido = saldoCentavos
        }
    }

    LaunchedEffect(ocupado) {
        servidorLento = false
        if (ocupado) {
            delay(4_000)
            servidorLento = true
        }
    }

    val mensagemTela = if (ocupado && servidorLento) {
        "Conectando ao servidor… isso pode levar alguns segundos."
    } else {
        mensagem
    }

    fun jogarMiniJogo(gameId: String, selection: String) {
        val wager = apostaCentavos ?: return
        resultado = "Escolha um jogo para começar"
        lucroUltimo = 0L
        mensagem = ""
        ocupado = true
        onJogar(gameId, wager, "", selection, UUID.randomUUID().toString()) { round, error ->
            ocupado = false
            if (error != null || round == null) {
                lucroUltimo = 0L
                atrasoSaldo = 0L
                mensagem = error?.localizedMessage ?: "Não foi possível concluir a rodada."
            } else {
                resultado = round.resultado
                lucroUltimo = round.variacaoCentavos
                atrasoSaldo = 1_300L
                mensagem = mensagemPremio(round.variacaoCentavos)
                rodada += 1
            }
        }
    }

    TelaJogosBase {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Jogos", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("$partidas partidas", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            SaldoAnimado(saldoExibido)
        }

        JogosPainel {
            SeletorJogos(
                listOf("Slots", "Roleta", "Crash", "Blackjack", "Cara ou coroa", "Dado", "Par ou ímpar", "Minas", "Raspadinha"),
                jogo,
            ) {
                jogo = it
                mensagem = ""
                lucroUltimo = 0L
                resultado = "Escolha um jogo para começar"
            }
            OutlinedTextField(
                value = apostaTexto,
                onValueChange = { apostaTexto = it; mensagem = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Aposta") },
                prefix = { Text("R$ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            ChipsAposta(
                saldoCentavos = saldoCentavos,
                apostaCentavos = apostaCentavos,
                habilitado = !ocupado && !apostaTravada,
                onEscolher = {
                    apostaTexto = formatarValorCampo(it)
                    mensagem = ""
                },
            )
            Text("Mínimo R$ 1,00 · máximo R$ 10.000,00", color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp)

            when (jogo) {
                "Slots" -> SlotsJogo(
                    resultado = resultado,
                    carregando = ocupado,
                    habilitado = apostaValida && !ocupado,
                    rodada = rodada,
                    lucroCentavos = lucroUltimo,
                    mensagem = mensagemTela,
                    onJogar = {
                        val wager = apostaCentavos ?: return@SlotsJogo
                        ocupado = true
                        onJogar("slots", wager, "", "", UUID.randomUUID().toString()) { round, error ->
                            ocupado = false
                            if (error != null || round == null) {
                                lucroUltimo = 0L
                                atrasoSaldo = 0L
                                mensagem = error?.localizedMessage ?: "Não foi possível concluir a rodada."
                            } else {
                                resultado = round.resultado
                                lucroUltimo = round.variacaoCentavos
                                atrasoSaldo = 1_500L
                                mensagem = mensagemPremio(round.variacaoCentavos)
                                rodada += 1
                            }
                        }
                    },
                )
                "Roleta" -> RoletaJogo(
                    tipo = tipoRoleta,
                    selecao = selecaoRoleta,
                    numero = numeroRoleta,
                    resultado = resultado,
                    mensagem = mensagemTela,
                    carregando = ocupado,
                    apostaValida = apostaValida && !ocupado,
                    rodada = rodada,
                    lucroCentavos = lucroUltimo,
                    historico = historicoRoleta,
                    onTipoChange = {
                        tipoRoleta = it
                        selecaoRoleta = when (it) {
                            "Cor" -> "Vermelho"
                            "Paridade" -> "Par"
                            "Faixa" -> "1–18"
                            "Dúzia" -> "1ª"
                            else -> ""
                        }
                    },
                    onSelecaoChange = { selecaoRoleta = it },
                    onNumeroChange = { numeroRoleta = it.filter(Char::isDigit).take(2) },
                    onJogar = {
                        val wager = apostaCentavos ?: return@RoletaJogo
                        val betType: String
                        val selection: String
                        when (tipoRoleta) {
                            "Cor" -> { betType = "color"; selection = if (selecaoRoleta == "Vermelho") "red" else "black" }
                            "Paridade" -> { betType = "parity"; selection = if (selecaoRoleta == "Par") "even" else "odd" }
                            "Faixa" -> { betType = "range"; selection = if (selecaoRoleta == "1–18") "low" else "high" }
                            "Dúzia" -> { betType = "dozen"; selection = when (selecaoRoleta) { "1ª" -> "1"; "2ª" -> "2"; else -> "3" } }
                            else -> { betType = "number"; selection = numeroRoleta }
                        }
                        if (betType == "number" && selection.toIntOrNull() !in 0..36) {
                            mensagem = "Escolha um número entre 0 e 36."
                            return@RoletaJogo
                        }
                        ocupado = true
                        onJogar("roulette", wager, betType, selection, UUID.randomUUID().toString()) { round, error ->
                            ocupado = false
                            if (error != null || round == null) {
                                lucroUltimo = 0L
                                atrasoSaldo = 0L
                                mensagem = error?.localizedMessage ?: "Não foi possível concluir a rodada."
                            } else {
                                resultado = round.resultado
                                lucroUltimo = round.variacaoCentavos
                                atrasoSaldo = 3_900L
                                mensagem = mensagemPremio(round.variacaoCentavos)
                                round.resultado.substringBefore("·").trim().toIntOrNull()?.let { n ->
                                    historicoRoleta.add(0, n)
                                    if (historicoRoleta.size > 20) historicoRoleta.removeAt(historicoRoleta.lastIndex)
                                }
                                rodada += 1
                            }
                        }
                    },
                )
                "Crash" -> CrashJogo(
                    apostaValida = apostaValida,
                    carregando = ocupado,
                    sessao = sessaoCrash,
                    multiplicadorBps = crashBps,
                    fim = fimCrash,
                    lucroCentavos = lucroUltimo,
                    mensagem = mensagemTela,
                    rodada = rodada,
                    onIniciar = {
                        val wager = apostaCentavos ?: return@CrashJogo
                        ocupado = true
                        onIniciarCrash(wager, UUID.randomUUID().toString()) { session, error ->
                            ocupado = false
                            atrasoSaldo = 0L
                            if (error != null || session == null) {
                                mensagem = error?.localizedMessage ?: "Não foi possível iniciar Crash."
                            } else {
                                sessaoCrash = session
                                crashBps = 100
                                fimCrash = null
                                lucroUltimo = 0L
                                mensagem = if (session.retomada) "Partida retomada." else "A partida começou. Retire antes da queda."
                            }
                        }
                    },
                    onSacar = {
                        val session = sessaoCrash ?: return@CrashJogo
                        saqueCrashPendente = true
                        ocupado = true
                        onSacarCrash(session.gameId, UUID.randomUUID().toString()) { result, error ->
                            ocupado = false
                            saqueCrashPendente = false
                            atrasoSaldo = 0L
                            sessaoCrash = null
                            if (error != null || result == null) {
                                mensagem = error?.localizedMessage ?: "Não foi possível retirar."
                            } else {
                                crashBps = result.multiplicadorBps
                                lucroUltimo = result.lucroCentavos
                                fimCrash = if (result.caiu) "caiu" else "retirou"
                                rodada += 1
                                mensagem = if (result.caiu) {
                                    "A queda aconteceu antes da retirada."
                                } else {
                                    "Retirado em ${formatarMultiplicador(result.multiplicadorBps)} · ${formatarReais(result.premioCentavos)}"
                                }
                            }
                        }
                    },
                )
                "Blackjack" -> BlackjackJogo(
                    apostaValida = apostaValida,
                    carregando = ocupado,
                    estado = estadoBlackjack,
                    mensagem = mensagemTela,
                    onIniciar = {
                        val wager = apostaCentavos ?: return@BlackjackJogo
                        ocupado = true
                        onIniciarBlackjack(wager, UUID.randomUUID().toString()) { state, error ->
                            ocupado = false
                            if (error != null || state == null) {
                                atrasoSaldo = 0L
                                mensagem = error?.localizedMessage ?: "Não foi possível abrir a mesa."
                            } else {
                                estadoBlackjack = state
                                atrasoSaldo = if (state.status == "active") 0L else 1_300L
                                mensagem = if (state.status == "active") "Sua vez." else resultadoBlackjack(state.resultado, state.lucroCentavos)
                            }
                        }
                    },
                    onAcao = { action ->
                        val state = estadoBlackjack ?: return@BlackjackJogo
                        ocupado = true
                        onAcaoBlackjack(state.gameId, action, UUID.randomUUID().toString()) { nextState, error ->
                            ocupado = false
                            if (error != null || nextState == null) {
                                atrasoSaldo = 0L
                                mensagem = error?.localizedMessage ?: "Não foi possível jogar esta ação."
                            } else {
                                estadoBlackjack = nextState
                                atrasoSaldo = if (nextState.status == "active") 0L else 1_300L
                                mensagem = if (nextState.status == "active") "Sua vez." else resultadoBlackjack(nextState.resultado, nextState.lucroCentavos)
                            }
                        }
                    },
                )
                else -> {
                    val opcoes = when (jogo) {
                        "Cara ou coroa" -> listOf("Cara", "Coroa")
                        "Dado" -> listOf("1", "2", "3", "4", "5", "6")
                        "Par ou ímpar" -> listOf("Par", "Ímpar")
                        "Minas" -> emptyList()
                        else -> emptyList()
                    }
                    val selecionada = when (jogo) {
                        "Cara ou coroa" -> ladoMoeda
                        "Dado" -> numeroDado
                        "Par ou ímpar" -> paridadeDado
                        "Minas" -> casaMinas
                        else -> ""
                    }
                    val regras = when (jogo) {
                        "Cara ou coroa" -> "Escolha um lado. Acerto paga 1,90x."
                        "Dado" -> "Adivinhe o resultado de 1 a 6. Acerto paga 5,50x."
                        "Par ou ímpar" -> "Escolha a paridade do dado. Acerto paga 1,90x."
                        "Minas" -> "Escolha uma casa entre cinco. Quatro são seguras e pagam 1,18x."
                        else -> "Três estrelas pagam 20x, sinos 4x e cerejas 2x."
                    }
                    val gameId = when (jogo) {
                        "Cara ou coroa" -> "coin"
                        "Dado" -> "dice"
                        "Par ou ímpar" -> "parity"
                        "Minas" -> "mines"
                        else -> "scratch"
                    }
                    val selecaoServidor = when (jogo) {
                        "Cara ou coroa" -> if (ladoMoeda == "Cara") "heads" else "tails"
                        "Dado" -> numeroDado
                        "Par ou ímpar" -> if (paridadeDado == "Par") "even" else "odd"
                        "Minas" -> casaMinas
                        else -> ""
                    }
                    MiniGameCard(
                        nome = jogo,
                        regras = regras,
                        opcoes = opcoes,
                        selecionada = selecionada,
                        onSelecionar = { escolha ->
                            when (jogo) {
                                "Cara ou coroa" -> ladoMoeda = escolha
                                "Dado" -> numeroDado = escolha
                                "Par ou ímpar" -> paridadeDado = escolha
                                "Minas" -> casaMinas = escolha
                            }
                            resultado = "Escolha um jogo para começar"
                            lucroUltimo = 0L
                            mensagem = ""
                        },
                        resultado = resultado,
                        mensagem = mensagemTela,
                        lucroCentavos = lucroUltimo,
                        carregando = ocupado,
                        habilitado = apostaValida && !ocupado,
                        onJogar = { jogarMiniJogo(gameId, selecaoServidor) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniGameCard(
    nome: String,
    regras: String,
    opcoes: List<String>,
    selecionada: String,
    onSelecionar: (String) -> Unit,
    resultado: String,
    mensagem: String,
    lucroCentavos: Long,
    carregando: Boolean,
    habilitado: Boolean,
    onJogar: () -> Unit,
) {
    val accent = when (nome) {
        "Cara ou coroa" -> Color(0xFFFFD86B)
        "Dado" -> Color(0xFF64D6C2)
        "Par ou ímpar" -> Color(0xFF83D68A)
        "Minas" -> Color(0xFFFF7C83)
        else -> Color(0xFFFFA45B)
    }
    var vitoria by remember { mutableStateOf(false) }
    var festa by remember { mutableStateOf(0) }
    val vibrar = rememberVibrar()
    val pulso by rememberInfiniteTransition(label = "minigame-pulso-$nome").animateFloat(
        initialValue = 1f,
        targetValue = 1.055f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "pulso",
    )

    LaunchedEffect(carregando, resultado) {
        if (carregando) {
            vitoria = false
        } else if (resultado != "Escolha um jogo para começar" && lucroCentavos > 0L) {
            vitoria = true
            festa += 1
            vibrar(true)
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = 0.2f), Color(0xFF141B1E), Color(0xFF0D1215))),
                    RoundedCornerShape(22.dp),
                )
                .border(1.5.dp, accent.copy(alpha = if (vitoria) 0.9f else 0.48f), RoundedCornerShape(22.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(nome.uppercase(), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Text("RODADA RÁPIDA", color = Color.White.copy(alpha = 0.48f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(regras, modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(154.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF172226), Color(0xFF0B1114))))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when (nome) {
                    "Cara ou coroa" -> {
                        val lado = when {
                            resultado.startsWith("Cara") -> "CARA"
                            resultado.startsWith("Coroa") -> "COROA"
                            selecionada == "Cara" -> "CARA"
                            else -> "COROA"
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .graphicsLayer {
                                        scaleX = if (carregando) pulso else 1f
                                        scaleY = if (carregando) pulso else 1f
                                        rotationY = if (carregando) 180f else 0f
                                    }
                                    .clip(CircleShape)
                                    .background(Brush.sweepGradient(listOf(Color(0xFFFFF1B3), accent, Color(0xFFBC7827), accent)))
                                    .border(4.dp, Color(0xFFFFF1B3).copy(alpha = 0.8f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(82.dp).clip(CircleShape).border(1.dp, Color(0xFF8D6125), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(lado, color = Color(0xFF593B14), fontSize = 17.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Text("MOEDA ZECA", color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "Dado", "Par ou ímpar" -> {
                        val lancado = Regex("(?:Saiu|Dado) ([1-6])").find(resultado)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val face = lancado ?: if (nome == "Dado") selecionada.toIntOrNull() ?: 1 else if (selecionada == "Par") 2 else 3
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            DadoVisual(face, carregando, pulso)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (lancado != null) "RESULTADO" else "SUA ESCOLHA", color = Color.White.copy(alpha = 0.52f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        nome == "Dado" -> "Face $face"
                                        nome == "Par ou ímpar" -> selecionada.uppercase()
                                        else -> "Dado $face"
                                    },
                                    color = accent,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Black,
                                )
                                Text("Boa sorte", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
                            }
                        }
                    }
                    "Minas" -> {
                        val mina = Regex("Mina na casa ([1-5])").find(resultado)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val segura = Regex("Casa ([1-5]) segura").find(resultado)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (mina != null || segura != null) "CASAS REVELADAS" else "ESCOLHA UMA CASA", color = Color.White.copy(alpha = 0.54f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                (1..5).forEach { casa ->
                                    val foiMina = mina == casa
                                    val foiSegura = segura == casa
                                    val escolhida = selecionada == casa.toString()
                                    val corCasa = when {
                                        foiMina -> Color(0xFFB73C52)
                                        foiSegura -> Color(0xFF26845F)
                                        escolhida -> accent.copy(alpha = 0.28f)
                                        else -> Color.White.copy(alpha = 0.08f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.76f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Brush.verticalGradient(listOf(corCasa, Color(0xFF11191C))))
                                            .border(1.dp, if (escolhida || foiMina || foiSegura) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                            .clickable(enabled = !carregando) { onSelecionar(casa.toString()) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            when {
                                                foiMina -> "✹"
                                                foiSegura -> "✓"
                                                escolhida -> casa.toString()
                                                else -> "·"
                                            },
                                            color = if (foiSegura) Color.White else accent,
                                            fontSize = if (foiMina || foiSegura) 23.sp else 18.sp,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val simbolo = when {
                            resultado.contains("estrelas", ignoreCase = true) -> "⭐"
                            resultado.contains("sinos", ignoreCase = true) -> "🔔"
                            resultado.contains("cerejas", ignoreCase = true) -> "🍒"
                            resultado.contains("Não premiada", ignoreCase = true) -> "✕"
                            else -> "?"
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            repeat(3) { indice ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 76.dp, height = 92.dp)
                                        .graphicsLayer {
                                            val escala = if (carregando) pulso else 1f
                                            scaleX = escala
                                            scaleY = escala
                                            rotationZ = if (carregando) (indice - 1) * 4f else 0f
                                        }
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(Brush.verticalGradient(listOf(Color(0xFF3D2A1B), Color(0xFF17120D))))
                                        .border(1.5.dp, accent.copy(alpha = 0.62f), RoundedCornerShape(15.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(simbolo, color = accent, fontSize = 31.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }

            if (opcoes.isNotEmpty()) SeletorJogos(opcoes, selecionada, onSelecionar)
            if (resultado != "Escolha um jogo para começar") {
                Text(resultado, modifier = Modifier.fillMaxWidth(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                BannerPremio(lucroCentavos, festa, vitoria)
            }
            BotaoJogo(
                texto = if (carregando) "Revelando..." else when (nome) {
                    "Cara ou coroa" -> "Lançar moeda"
                    "Dado" -> "Lançar dado"
                    "Par ou ímpar" -> "Lançar dado"
                    "Minas" -> "Revelar casa"
                    else -> "Raspar cartão"
                },
                onClick = onJogar,
                enabled = habilitado,
                cor = accent,
                corTexto = Color(0xFF101417),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            )
            StatusText(if (carregando) "Aguardando resultado seguro do servidor..." else mensagem)
        }
        ExplosaoMoedas(festa, Modifier.matchParentSize())
    }
}

@Composable
private fun DadoVisual(numero: Int, carregando: Boolean, pulso: Float) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .graphicsLayer {
                val escala = if (carregando) pulso else 1f
                scaleX = escala
                scaleY = escala
                rotationZ = if (carregando) 10f else 0f
            }
            .clip(RoundedCornerShape(19.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF5F1E5), Color(0xFFD7D0BC))))
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(19.dp)),
    ) {
        Canvas(Modifier.fillMaxSize().padding(17.dp)) {
            val pontos = when (numero.coerceIn(1, 6)) {
                1 -> listOf(0.5f to 0.5f)
                2 -> listOf(0.22f to 0.22f, 0.78f to 0.78f)
                3 -> listOf(0.22f to 0.22f, 0.5f to 0.5f, 0.78f to 0.78f)
                4 -> listOf(0.22f to 0.22f, 0.78f to 0.22f, 0.22f to 0.78f, 0.78f to 0.78f)
                5 -> listOf(0.22f to 0.22f, 0.78f to 0.22f, 0.5f to 0.5f, 0.22f to 0.78f, 0.78f to 0.78f)
                else -> listOf(0.22f to 0.18f, 0.78f to 0.18f, 0.22f to 0.5f, 0.78f to 0.5f, 0.22f to 0.82f, 0.78f to 0.82f)
            }
            pontos.forEach { (x, y) ->
                drawCircle(Color(0xFF1B3331), radius = size.minDimension * 0.085f, center = Offset(size.width * x, size.height * y))
            }
        }
    }
}

@Composable
internal fun SeletorJogos(opcoes: List<String>, selecionada: String, onSelecionar: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        opcoes.chunked(2).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                linha.forEach { opcao ->
                    Button(
                        onClick = { onSelecionar(opcao) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (opcao == selecionada) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.1f),
                        ),
                    ) {
                        Text(opcao, color = if (opcao == selecionada) Color(0xFF111418) else Color.White, fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (linha.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TelaJogosBase(content: @Composable ColumnScope.() -> Unit) {
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
private fun JogosPainel(content: @Composable ColumnScope.() -> Unit) {
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
internal fun StatusText(texto: String) {
    if (texto.isNotBlank()) Text(texto, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp)
}

internal fun formatarMultiplicador(bps: Int): String = String.format(Locale.ROOT, "%.2fx", bps / 100.0)

private fun mensagemPremio(lucroCentavos: Long): String =
    if (lucroCentavos > 0) "Você ganhou ${formatarReais(lucroCentavos)}." else "Rodada concluída."

private fun resultadoBlackjack(outcome: String, lucroCentavos: Long): String = when (outcome) {
    "blackjack" -> "Blackjack · +${formatarReais(lucroCentavos)}"
    "win" -> "Você venceu · +${formatarReais(lucroCentavos)}"
    "push" -> "Empate · aposta devolvida"
    "bust" -> "Passou de 21 · perdeu a mão"
    "dealer_blackjack" -> "Dealer fez Blackjack"
    else -> "Dealer venceu"
}

internal fun pontuacaoCartas(cartas: List<CartaBlackjack>): Int {
    var total = cartas.sumOf { carta -> if (carta.rank == "A") 11 else carta.rank.toIntOrNull() ?: 10 }
    var ases = cartas.count { it.rank == "A" }
    while (total > 21 && ases > 0) {
        total -= 10
        ases -= 1
    }
    return total
}