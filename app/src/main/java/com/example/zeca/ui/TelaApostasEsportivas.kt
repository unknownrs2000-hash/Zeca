package com.example.zeca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ApostaEsportiva
import com.example.zeca.PartidaEsportiva
import com.example.zeca.ui.theme.Cores
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun TelaApostasEsportivas(
    saldoCentavos: Long,
    onCarregarPartidas: ((List<PartidaEsportiva>, Exception?) -> Unit) -> Unit,
    onCarregarApostas: ((List<ApostaEsportiva>, Exception?) -> Unit) -> Unit,
    onApostar: (Int, String, Long, String, (Exception?) -> Unit) -> Unit,
    onLiquidar: ((Int?, Exception?) -> Unit) -> Unit,
) {
    var partidas by remember { mutableStateOf<List<PartidaEsportiva>>(emptyList()) }
    var apostas by remember { mutableStateOf<List<ApostaEsportiva>>(emptyList()) }
    var valorAposta by rememberSaveable { mutableStateOf("10,00") }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }

    fun carregarDados() {
        carregando = true
        onCarregarPartidas { matches, error ->
            partidas = matches
            erro = error?.localizedMessage.orEmpty()
            onCarregarApostas { bets, betsError ->
                apostas = bets
                if (betsError != null) erro = betsError.localizedMessage.orEmpty()
                carregando = false
            }
        }
    }

    LaunchedEffect(Unit) { carregarDados() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Futebol · vencedor", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Créditos virtuais · empate perde", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }
            TextButton(onClick = {
                carregando = true
                onLiquidar { settled, error ->
                    aviso = if (error == null) {
                        if ((settled ?: 0) > 0) "${settled} aposta(s) liquidada(s)." else "Nenhuma partida terminou ainda."
                    } else ""
                    if (error != null) erro = error.localizedMessage.orEmpty()
                    carregarDados()
                }
            }, enabled = !carregando) { Text("Atualizar placares") }
        }
        Text("Saldo disponível · ${formatarSaldoEsportivo(saldoCentavos)}", color = Cores.Verde, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        OutlinedTextField(
            value = valorAposta,
            onValueChange = { valorAposta = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(12) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Valor da aposta") },
            prefix = { Text("R$ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (aviso.isNotBlank()) Text(aviso, color = Cores.Verde, fontSize = 11.sp)
        if (erro.isNotBlank()) {
            Text(
                erro,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF34272A), RoundedCornerShape(8.dp)).padding(10.dp),
                color = Color(0xFFFFB7A7),
                fontSize = 11.sp,
            )
        }

        Text("Partidas de hoje", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (partidas.isEmpty() && !carregando && erro.isBlank()) {
            Text("Não há partidas futuras com odds de vitória disponíveis hoje.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        partidas.forEach { match ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(match.campeonato, color = Cores.Turquesa, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${match.mandante}  ×  ${match.visitante}",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(formatarHorarioEsportivo(match.kickoffMs), modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.52f), fontSize = 10.sp, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotaoApostaVencedor(
                        nome = match.mandante,
                        oddsBps = match.oddMandanteBps,
                        enabled = !carregando && match.oddMandanteBps > 10_000,
                        modifier = Modifier.weight(1f),
                    ) {
                        fazerAposta(match, "home", valorAposta, saldoCentavos, onApostar) { message, error ->
                            erro = error?.localizedMessage.orEmpty()
                            aviso = message
                            if (error == null) carregarDados()
                        }
                    }
                    BotaoApostaVencedor(
                        nome = match.visitante,
                        oddsBps = match.oddVisitanteBps,
                        enabled = !carregando && match.oddVisitanteBps > 10_000,
                        modifier = Modifier.weight(1f),
                    ) {
                        fazerAposta(match, "away", valorAposta, saldoCentavos, onApostar) { message, error ->
                            erro = error?.localizedMessage.orEmpty()
                            aviso = message
                            if (error == null) carregarDados()
                        }
                    }
                }
            }
        }

        if (apostas.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("Minhas apostas", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            apostas.forEach { bet ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${bet.nomeSelecao} vence · ${bet.mandante} x ${bet.visitante}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (bet.status == "open") "Pendente · odd ${(bet.oddBps / 10_000.0).format(2)}x"
                            else when (bet.resultado) { "draw" -> "Empate · aposta perdida"; bet.selecao -> "Vitória · liquidada"; else -> "Derrota · liquidada" },
                            color = if (bet.status == "open") Cores.Turquesa else Color.White.copy(alpha = 0.55f),
                            fontSize = 9.sp,
                        )
                    }
                    Text(
                        if (bet.status == "open") formatarSaldoEsportivo(bet.valorCentavos)
                        else formatarSaldoEsportivo(bet.premioCentavos),
                        color = if (bet.status == "settled" && bet.premioCentavos > 0) Cores.Verde else Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BotaoApostaVencedor(
    nome: String,
    oddsBps: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text(if (oddsBps > 10_000) "${(oddsBps / 10_000.0).format(2)}x" else "Sem odd", color = Color(0xFF101417), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("Vitória ${nome.take(14)}", color = Color(0xFF101417), fontSize = 9.sp, maxLines = 1)
        }
    }
}

private fun fazerAposta(
    partida: PartidaEsportiva,
    selecao: String,
    valorTexto: String,
    saldoCentavos: Long,
    onApostar: (Int, String, Long, String, (Exception?) -> Unit) -> Unit,
    concluir: (String, Exception?) -> Unit,
) {
    val amount = runCatching {
        BigDecimal(valorTexto.trim().replace(',', '.')).movePointRight(2).longValueExact()
    }.getOrNull()?.takeIf { it >= 100L }
    if (amount == null) {
        concluir("Informe um valor de pelo menos R$ 1,00.", IllegalArgumentException("Aposta inválida."))
        return
    }
    if (amount > saldoCentavos) {
        concluir("Saldo insuficiente para essa aposta.", IllegalArgumentException("Saldo insuficiente."))
        return
    }
    onApostar(partida.fixtureId, selecao, amount, UUID.randomUUID().toString()) { error ->
        concluir(if (error == null) "Aposta registrada. Empate não paga." else "", error)
    }
}

private fun formatarSaldoEsportivo(centavos: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(centavos, 2))

private fun formatarHorarioEsportivo(timestampMs: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestampMs))

private fun Double.format(digits: Int): String = "%1$.${digits}f".format(Locale.ROOT, this)