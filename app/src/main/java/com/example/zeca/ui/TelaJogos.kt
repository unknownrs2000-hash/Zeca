package com.example.zeca.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.ln
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
    var sessaoCrash by remember { mutableStateOf<SessaoCrash?>(null) }
    var crashBps by remember { mutableStateOf(100) }
    var estadoBlackjack by remember { mutableStateOf<EstadoBlackjack?>(null) }
    val apostaCentavos = parseValorCentavos(apostaTexto)
    val apostaValida = apostaCentavos != null && apostaCentavos in 100..1_000_000 && apostaCentavos <= saldoCentavos

    LaunchedEffect(sessaoCrash?.gameId) {
        val sessao = sessaoCrash ?: return@LaunchedEffect
        while (true) {
            val elapsedMs = (System.currentTimeMillis() - sessao.iniciadoEmMs).coerceAtLeast(0L)
            crashBps = (100 * exp(elapsedMs / 5_000.0)).toInt().coerceIn(100, 1_000_000)
            delay(80)
        }
    }

    TelaJogosBase {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Jogos", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text("$partidas partidas", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            }
            Text(formatarReais(saldoCentavos), color = Cores.Verde, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        JogosPainel {
            SeletorJogos(listOf("Slots", "Roleta", "Crash", "Blackjack"), jogo) {
                jogo = it
                mensagem = ""
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
            Text("Mínimo R$ 1,00 · máximo R$ 10.000,00", color = Color.White.copy(alpha = 0.54f), fontSize = 11.sp)

            when (jogo) {
                "Slots" -> SlotsControls(
                    resultado = resultado,
                    mensagem = mensagem,
                    carregando = ocupado,
                    habilitado = apostaValida && !ocupado,
                    onJogar = {
                        val wager = apostaCentavos ?: return@SlotsControls
                        ocupado = true
                        onJogar("slots", wager, "", "", UUID.randomUUID().toString()) { round, error ->
                            ocupado = false
                            if (error != null || round == null) {
                                mensagem = error?.localizedMessage ?: "Não foi possível concluir a rodada."
                            } else {
                                resultado = round.resultado
                                mensagem = mensagemPremio(round.variacaoCentavos)
                            }
                        }
                    },
                )
                "Roleta" -> RouletteControls(
                    tipo = tipoRoleta,
                    selecao = selecaoRoleta,
                    numero = numeroRoleta,
                    resultado = resultado,
                    mensagem = mensagem,
                    carregando = ocupado,
                    apostaValida = apostaValida && !ocupado,
                    onTipoChange = { tipoRoleta = it; selecaoRoleta = when (it) {
                        "Cor" -> "Vermelho"
                        "Paridade" -> "Par"
                        "Faixa" -> "1–18"
                        "Dúzia" -> "1ª"
                        else -> ""
                    } },
                    onSelecaoChange = { selecaoRoleta = it },
                    onNumeroChange = { numeroRoleta = it.filter(Char::isDigit).take(2) },
                    onJogar = {
                        val wager = apostaCentavos ?: return@RouletteControls
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
                            return@RouletteControls
                        }
                        ocupado = true
                        onJogar("roulette", wager, betType, selection, UUID.randomUUID().toString()) { round, error ->
                            ocupado = false
                            if (error != null || round == null) {
                                mensagem = error?.localizedMessage ?: "Não foi possível concluir a rodada."
                            } else {
                                resultado = round.resultado
                                mensagem = mensagemPremio(round.variacaoCentavos)
                            }
                        }
                    },
                )
                "Crash" -> CrashControls(
                    apostaValida = apostaValida,
                    carregando = ocupado,
                    sessao = sessaoCrash,
                    multiplicadorBps = crashBps,
                    mensagem = mensagem,
                    onIniciar = {
                        val wager = apostaCentavos ?: return@CrashControls
                        ocupado = true
                        onIniciarCrash(wager, UUID.randomUUID().toString()) { session, error ->
                            ocupado = false
                            if (error != null || session == null) {
                                mensagem = error?.localizedMessage ?: "Não foi possível iniciar Crash."
                            } else {
                                sessaoCrash = session
                                crashBps = 100
                                mensagem = if (session.retomada) "Partida retomada." else "A partida começou. Retire antes da queda."
                            }
                        }
                    },
                    onSacar = {
                        val session = sessaoCrash ?: return@CrashControls
                        ocupado = true
                        onSacarCrash(session.gameId, UUID.randomUUID().toString()) { result, error ->
                            ocupado = false
                            sessaoCrash = null
                            if (error != null || result == null) {
                                mensagem = error?.localizedMessage ?: "Não foi possível retirar."
                            } else {
                                crashBps = result.multiplicadorBps
                                mensagem = if (result.caiu) "A queda aconteceu antes da retirada." else "Retirado em ${formatarMultiplicador(result.multiplicadorBps)} · ${formatarReais(result.premioCentavos)}"
                            }
                        }
                    },
                )
                else -> BlackjackControls(
                    apostaValida = apostaValida,
                    carregando = ocupado,
                    estado = estadoBlackjack,
                    onIniciar = {
                        val wager = apostaCentavos ?: return@BlackjackControls
                        ocupado = true
                        onIniciarBlackjack(wager, UUID.randomUUID().toString()) { state, error ->
                            ocupado = false
                            if (error != null || state == null) mensagem = error?.localizedMessage ?: "Não foi possível abrir a mesa."
                            else {
                                estadoBlackjack = state
                                mensagem = if (state.status == "active") "Sua vez." else resultadoBlackjack(state.resultado, state.lucroCentavos)
                            }
                        }
                    },
                    onAcao = { action ->
                        val state = estadoBlackjack ?: return@BlackjackControls
                        ocupado = true
                        onAcaoBlackjack(state.gameId, action, UUID.randomUUID().toString()) { nextState, error ->
                            ocupado = false
                            if (error != null || nextState == null) mensagem = error?.localizedMessage ?: "Não foi possível jogar esta ação."
                            else {
                                estadoBlackjack = nextState
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
private fun SlotsControls(
    resultado: String,
    mensagem: String,
    carregando: Boolean,
    habilitado: Boolean,
    onJogar: () -> Unit,
) {
    Text("Trinca paga 20× · par paga 2×", color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp)
    var rolos by remember { mutableStateOf(listOf("7", "★", "◆")) }
    LaunchedEffect(carregando, resultado) {
        if (carregando) {
            val simbolos = listOf("7", "BAR", "★", "◆", "●", "♣")
            while (true) {
                rolos = List(3) { simbolos.random() }
                delay(85)
            }
        } else if (resultado != "Escolha um jogo para começar") {
            rolos = resultado.split(Regex("\\s+")).take(3).ifEmpty { rolos }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    ) {
        rolos.forEachIndexed { index, simbolo ->
            Crossfade(targetState = simbolo, label = "slot-reel-$index") { face ->
                Box(
                    modifier = Modifier
                        .size(width = 82.dp, height = 96.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Cores.Cartao, Color(0xFF0D1114))),
                            RoundedCornerShape(15.dp),
                        )
                        .border(1.dp, if (carregando) Cores.Verde.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.22f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(face, color = listOf(Cores.Laranja, Cores.Turquesa, Color(0xFFFF8290))[index], fontSize = 25.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
    Button(onClick = onJogar, enabled = habilitado, modifier = Modifier.fillMaxWidth()) {
        Text(if (carregando) "Girando..." else "Girar rolos")
    }
    StatusText(mensagem)
}

@Composable
private fun RouletteControls(
    tipo: String,
    selecao: String,
    numero: String,
    resultado: String,
    mensagem: String,
    carregando: Boolean,
    apostaValida: Boolean,
    onTipoChange: (String) -> Unit,
    onSelecaoChange: (String) -> Unit,
    onNumeroChange: (String) -> Unit,
    onJogar: () -> Unit,
) {
    var giro by remember { mutableStateOf(0) }
    val numeroSaido = resultado.substringBefore("·").trim().toIntOrNull()
    val rotacao by animateFloatAsState(
        targetValue = giro * 360f + (numeroSaido ?: 0) * (360f / 37f),
        animationSpec = tween(durationMillis = 1_450, easing = FastOutSlowInEasing),
        label = "roulette-wheel-spin",
    )
    SeletorJogos(listOf("Cor", "Paridade", "Faixa", "Dúzia", "Número"), tipo, onTipoChange)
    when (tipo) {
        "Cor" -> SeletorJogos(listOf("Vermelho", "Preto"), selecao, onSelecaoChange)
        "Paridade" -> SeletorJogos(listOf("Par", "Ímpar"), selecao, onSelecaoChange)
        "Faixa" -> SeletorJogos(listOf("1–18", "19–36"), selecao, onSelecaoChange)
        "Dúzia" -> SeletorJogos(listOf("1ª", "2ª", "3ª"), selecao, onSelecaoChange)
        else -> OutlinedTextField(
            value = numero,
            onValueChange = onNumeroChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número (0–36)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Text("Cor/paridade/faixa pagam 2× · dúzia 3× · número 36×. Zero perde em todos, exceto aposta no 0.", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
    RoletaAnimada(numeroSaido, rotacao)
    Text(resultado, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
    Button(onClick = { giro += 5; onJogar() }, enabled = apostaValida && !carregando && (tipo != "Número" || numero.toIntOrNull() in 0..36), modifier = Modifier.fillMaxWidth()) {
        Text(if (carregando) "Girando..." else "Girar roleta")
    }
    StatusText(mensagem)
}

@Composable
private fun CrashControls(
    apostaValida: Boolean,
    carregando: Boolean,
    sessao: SessaoCrash?,
    multiplicadorBps: Int,
    mensagem: String,
    onIniciar: () -> Unit,
    onSacar: () -> Unit,
) {
    Text("Retire antes que o multiplicador pare.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
    CrashGrafico(multiplicadorBps, sessao != null)
    Text(
        formatarMultiplicador(multiplicadorBps),
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        color = if (sessao == null) Color.White else Cores.Verde,
        fontSize = 48.sp,
        fontWeight = FontWeight.Black,
        textAlign = TextAlign.Center,
    )
    if (sessao == null) {
        Button(onClick = onIniciar, enabled = !carregando, modifier = Modifier.fillMaxWidth()) {
            Text(if (carregando) "Iniciando..." else "Iniciar Crash")
        }
    } else {
        Text("Aposta ${formatarReais(sessao.apostaCentavos)}", color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
        Button(onClick = onSacar, enabled = !carregando && multiplicadorBps > 100, modifier = Modifier.fillMaxWidth()) {
            Text(if (carregando) "Aguardando..." else "Retirar agora")
        }
    }
    StatusText(mensagem)
}

@Composable
private fun BlackjackControls(
    apostaValida: Boolean,
    carregando: Boolean,
    estado: EstadoBlackjack?,
    onIniciar: () -> Unit,
    onAcao: (String) -> Unit,
) {
    if (estado == null) {
        Text("Receba duas cartas e escolha pedir, parar ou dobrar.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        Button(onClick = onIniciar, enabled = !carregando, modifier = Modifier.fillMaxWidth()) {
            Text(if (carregando) "Abrindo mesa..." else "Distribuir cartas")
        }
        return
    }

    Text("Sua mão · ${pontuacaoCartas(estado.cartasJogador)}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Cartas(estado.cartasJogador)
    Text("Dealer${if (estado.cartaOculta) " · carta oculta" else " · ${pontuacaoCartas(estado.cartasDealer)}"}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Cartas(estado.cartasDealer)

    if (estado.status == "active") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAcao("hit") }, enabled = !carregando, modifier = Modifier.weight(1f)) { Text("Pedir") }
            Button(onClick = { onAcao("stand") }, enabled = !carregando, modifier = Modifier.weight(1f)) { Text("Parar") }
            Button(
                onClick = { onAcao("double") },
                enabled = !carregando && estado.cartasJogador.size == 2,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Cores.Turquesa.copy(alpha = 0.8f)),
            ) { Text("Dobrar") }
        }
    } else {
        Text(resultadoBlackjack(estado.resultado, estado.lucroCentavos), color = Cores.Verde, fontWeight = FontWeight.Bold)
        Button(onClick = onIniciar, enabled = apostaValida && !carregando, modifier = Modifier.fillMaxWidth()) {
            Text(if (carregando) "Abrindo..." else "Nova mão")
        }
    }
}

@Composable
private fun Cartas(cartas: List<CartaBlackjack>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cartas.forEachIndexed { index, carta ->
            key("$index-${carta.rank}-${carta.suit}") {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(260, delayMillis = index * 75)) + scaleIn(initialScale = 0.72f, animationSpec = tween(320, delayMillis = index * 75)),
                    label = "deal-card-$index-${carta.rank}",
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 72.dp)
                            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE4E9EC))), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${carta.rank}${carta.suit}", color = if (carta.suit == "♥" || carta.suit == "♦") Color(0xFFB72F4C) else Color(0xFF151A1D), fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoletaAnimada(numero: Int?, rotacao: Float) {
    val corCentro = when {
        numero == 0 -> Color(0xFF33C889)
        numero == null -> Cores.Turquesa
        numero in setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36) -> Color(0xFFFF6879)
        else -> Color.White
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer { rotationZ = rotacao },
        ) {
            val diameter = size.minDimension
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val wheelSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            val redNumbers = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
            repeat(37) { index ->
                val color = when {
                    index == 0 -> Color(0xFF2CC889)
                    index in redNumbers -> Color(0xFFD7475E)
                    else -> Color(0xFF181D21)
                }
                drawArc(
                    color = color,
                    startAngle = -90f + index * (360f / 37f),
                    sweepAngle = 360f / 37f - 0.7f,
                    useCenter = true,
                    topLeft = topLeft,
                    size = wheelSize,
                )
            }
            drawCircle(color = Color(0xFFD5B66B), radius = diameter / 2f, style = Stroke(width = 4.dp.toPx()))
            drawCircle(color = Color(0xFF0C1114), radius = diameter * 0.19f)
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width / 2f, 8.dp.toPx()))
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFF101619), RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(numero?.toString() ?: "ZECA", color = corCentro, fontSize = if (numero == null) 12.sp else 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CrashGrafico(multiplicadorBps: Int, ativo: Boolean) {
    val alvo = (ln((multiplicadorBps.coerceAtLeast(100) / 100.0)) / ln(10_000.0)).coerceIn(0.04, 1.0).toFloat()
    val progresso by animateFloatAsState(
        targetValue = alvo,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "crash-chart-progress",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(128.dp)
            .background(Color.White.copy(alpha = 0.045f), RoundedCornerShape(16.dp))
            .padding(10.dp),
    ) {
        val graph = Path().apply {
            moveTo(0f, size.height)
            val endX = size.width * progresso
            val endY = size.height * (1f - progresso).coerceAtLeast(0.04f)
            cubicTo(size.width * progresso * 0.25f, size.height, size.width * progresso * 0.45f, endY, endX, endY)
        }
        repeat(3) { index ->
            val y = size.height * (index + 1) / 4f
            drawLine(Color.White.copy(alpha = 0.08f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        drawPath(graph, if (ativo) Cores.Verde else Cores.Turquesa, style = Stroke(width = 3.dp.toPx()))
        drawCircle(
            color = if (ativo) Cores.Verde else Cores.Turquesa,
            radius = 5.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(size.width * progresso, size.height * (1f - progresso).coerceAtLeast(0.04f)),
        )
    }
}

@Composable
private fun SeletorJogos(opcoes: List<String>, selecionada: String, onSelecionar: (String) -> Unit) {
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
private fun TelaJogosBase(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun JogosPainel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
private fun StatusText(texto: String) {
    if (texto.isNotBlank()) Text(texto, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp)
}

private fun formatarMultiplicador(bps: Int): String = String.format(Locale.ROOT, "%.2fx", bps / 100.0)

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

private fun pontuacaoCartas(cartas: List<CartaBlackjack>): Int {
    var total = cartas.sumOf { carta -> if (carta.rank == "A") 11 else carta.rank.toIntOrNull() ?: 10 }
    var ases = cartas.count { it.rank == "A" }
    while (total > 21 && ases > 0) {
        total -= 10
        ases -= 1
    }
    return total
}