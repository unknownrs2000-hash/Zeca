package com.example.zeca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    var estadoBlackjack by remember { mutableStateOf<EstadoBlackjack?>(null) }
    val historicoRoleta = remember { mutableStateListOf<Int>() }

    val apostaCentavos = parseValorCentavos(apostaTexto)
    val apostaValida = apostaCentavos != null && apostaCentavos in 100..1_000_000 && apostaCentavos <= saldoCentavos
    val apostaTravada = when (jogo) {
        "Crash" -> sessaoCrash != null
        "Blackjack" -> estadoBlackjack?.status == "active"
        else -> false
    }

    LaunchedEffect(sessaoCrash?.gameId) {
        val sessao = sessaoCrash ?: return@LaunchedEffect
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
            SeletorJogos(listOf("Slots", "Roleta", "Crash", "Blackjack"), jogo) {
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
                        ocupado = true
                        onSacarCrash(session.gameId, UUID.randomUUID().toString()) { result, error ->
                            ocupado = false
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
                else -> BlackjackJogo(
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