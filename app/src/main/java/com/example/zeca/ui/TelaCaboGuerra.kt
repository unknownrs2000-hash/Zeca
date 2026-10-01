package com.example.zeca.ui

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.JogadorRanking
import com.example.zeca.SalaCaboGuerra
import com.example.zeca.ui.theme.Cores
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

@Composable
fun TelaCaboGuerra(
    uidAtual: String,
    saldoCentavos: Long,
    jogadores: List<JogadorRanking>,
    onCarregarSalas: ((List<SalaCaboGuerra>, Exception?) -> Unit) -> Unit,
    onCriarSala: (Long, List<String>, String, String, String, (SalaCaboGuerra?, Exception?) -> Unit) -> Unit,
    onEntrarSala: (String, String, String, (SalaCaboGuerra?, Exception?) -> Unit) -> Unit,
    onGerenciarSala: (String, String, String, String, Long, String, (SalaCaboGuerra?, Exception?) -> Unit) -> Unit,
    onIniciarSala: (String, (SalaCaboGuerra?, Exception?) -> Unit) -> Unit,
    onPuxarCorda: (String, Int, String, (SalaCaboGuerra?, Exception?) -> Unit) -> Unit,
) {
    var salas by remember { mutableStateOf<List<SalaCaboGuerra>>(emptyList()) }
    var salaSelecionada by remember { mutableStateOf<SalaCaboGuerra?>(null) }
    var stakeTexto by rememberSaveable { mutableStateOf("10,00") }
    var senhaSala by rememberSaveable { mutableStateOf("") }
    var senhaEntrada by rememberSaveable { mutableStateOf("") }
    var modoSala by rememberSaveable { mutableStateOf("1v1") }
    var salaParaEntrar by remember { mutableStateOf<SalaCaboGuerra?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf("") }
    var puxoesPendentes by remember { mutableStateOf(0) }
    var enviandoLotePuxoes by remember { mutableStateOf(false) }
    var ultimoToqueMs by remember { mutableStateOf(0L) }
    val convidados = remember { mutableStateListOf<String>() }
    val jogadoresDisponiveis = jogadores.filter { it.uid != uidAtual }
    val haptic = LocalHapticFeedback.current

    fun atualizarSalas() {
        onCarregarSalas { rooms, error ->
            salas = rooms
            if (error != null) erro = error.localizedMessage.orEmpty()
            salaSelecionada?.let { selected -> rooms.firstOrNull { it.id == selected.id }?.let { salaSelecionada = it } }
        }
    }

    LaunchedEffect(Unit) {
        atualizarSalas()
        while (true) {
            delay(if (salaSelecionada?.status in listOf("active", "ready")) 1_000 else 12_000)
            atualizarSalas()
        }
    }

    LaunchedEffect(salaSelecionada?.id) {
        puxoesPendentes = 0
        enviandoLotePuxoes = false
        ultimoToqueMs = 0L
    }

    LaunchedEffect(salaSelecionada?.id, salaSelecionada?.status, puxoesPendentes, enviandoLotePuxoes) {
        val room = salaSelecionada ?: return@LaunchedEffect
        if (room.status != "active" || enviandoLotePuxoes || puxoesPendentes == 0) return@LaunchedEffect
        delay(160)
        val currentRoom = salaSelecionada ?: return@LaunchedEffect
        if (currentRoom.id != room.id || currentRoom.status != "active" || enviandoLotePuxoes) return@LaunchedEffect
        val count = minOf(8, puxoesPendentes)
        puxoesPendentes -= count
        enviandoLotePuxoes = true
        onPuxarCorda(room.id, count, UUID.randomUUID().toString()) { next, error ->
            enviandoLotePuxoes = false
            if (salaSelecionada?.id == room.id) {
                if (error != null) {
                    erro = error.localizedMessage.orEmpty()
                    puxoesPendentes = 0
                    atualizarSalas()
                } else {
                    salaSelecionada = next
                }
            }
        }
    }

    fun apostarEmSala(amount: Long): Boolean {
        if (amount < 100L) {
            erro = "A aposta mínima é R$ 1,00."
            return false
        }
        if (amount > 1_000_000L) {
            erro = "A aposta máxima é R$ 10.000,00."
            return false
        }
        if (amount > saldoCentavos) {
            erro = "Saldo insuficiente para essa aposta."
            return false
        }
        return true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Cabo de guerra · ${salaSelecionada?.modo ?: modoSala}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Toque para puxar · vantagem de 8 vence", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }
            TextButton(onClick = { atualizarSalas() }, enabled = !carregando) { Text("Atualizar") }
        }
        Text("Aposta em créditos virtuais · prêmio ao vencedor: 2× a aposta", color = Cores.Turquesa, fontSize = 11.sp)

        salaSelecionada?.let { room ->
            val souCriador = room.criadorUid == uidAtual
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF11181B), RoundedCornerShape(12.dp))
                    .border(1.dp, Cores.Turquesa.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SALA ${room.id.take(8).uppercase()}", color = Cores.Turquesa, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text(formatarSaldoTug(room.apostaCentavos), color = Cores.Verde, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                val souTimeA = room.timeA.any { it.uid == uidAtual }
                val souTimeB = room.timeB.any { it.uid == uidAtual }
                val souParticipante = souTimeA || souTimeB
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("TIME A · ${room.puxoesTimeA}", color = Cores.Verde, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(room.timeA.joinToString(" + ") { it.nome }.ifBlank { "Aguardando jogador" }, color = Color.White, fontSize = 10.sp)
                    }
                    Text("×", color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("TIME B · ${room.puxoesTimeB}", color = Color(0xFFFF8B91), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text(room.timeB.joinToString(" + ") { it.nome }.ifBlank { "Aguardando jogador" }, color = Color.White, fontSize = 10.sp)
                    }
                }
                when (room.status) {
                    "waiting" -> {
                        Text(if (room.protegidaPorSenha) "Sala com senha" else "Sala aguardando jogador", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                        if (souCriador) {
                            OutlinedTextField(
                                value = stakeTexto,
                                onValueChange = { stakeTexto = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(12) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Aposta por jogador") },
                                prefix = { Text("R$ ") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val amount = parseSaldoTug(stakeTexto)
                                    if (amount == null || !apostarEmSala(amount)) return@Button
                                    carregando = true
                                    onGerenciarSala(room.id, "setStake", UUID.randomUUID().toString(), "", amount, "") { next, error ->
                                        carregando = false
                                        if (error != null) erro = error.localizedMessage.orEmpty() else salaSelecionada = next
                                    }
                                }, enabled = !carregando, colors = ButtonDefaults.buttonColors(containerColor = Cores.Turquesa)) {
                                    Text("Atualizar aposta", color = Color(0xFF101417), fontSize = 11.sp)
                                }
                                Button(onClick = {
                                    carregando = true
                                    onGerenciarSala(room.id, "setPassword", UUID.randomUUID().toString(), "", 0, senhaSala) { next, error ->
                                        carregando = false
                                        if (error != null) erro = error.localizedMessage.orEmpty() else {
                                            salaSelecionada = next
                                            senhaSala = ""
                                            aviso = if (next?.protegidaPorSenha == true) "Senha da sala atualizada." else "Senha removida."
                                        }
                                    }
                                }, enabled = !carregando, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.13f))) {
                                    Text(if (senhaSala.isBlank()) "Remover senha" else "Definir senha", color = Color.White, fontSize = 11.sp)
                                }
                            }
                            OutlinedTextField(
                                value = senhaSala,
                                onValueChange = { senhaSala = it.take(24) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Nova senha (vazio remove)") },
                                singleLine = true,
                            )
                            if (jogadoresDisponiveis.isNotEmpty()) {
                                Text("Convidar jogador", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                jogadoresDisponiveis.take(10).forEach { player ->
                                    val convidado = player.uid in room.convitesUids
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(player.apelido, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                        TextButton(onClick = {
                                            carregando = true
                                            onGerenciarSala(room.id, "addInvite", UUID.randomUUID().toString(), player.uid, 0, "") { next, error ->
                                                carregando = false
                                                if (error != null) erro = error.localizedMessage.orEmpty() else salaSelecionada = next
                                            }
                                        }, enabled = !carregando) { Text(if (convidado) "Reenviar · 30 s" else "Convidar") }
                                        if (convidado) {
                                            TextButton(onClick = {
                                                carregando = true
                                                onGerenciarSala(room.id, "removeInvite", UUID.randomUUID().toString(), player.uid, 0, "") { next, error ->
                                                    carregando = false
                                                    if (error != null) erro = error.localizedMessage.orEmpty() else salaSelecionada = next
                                                }
                                            }, enabled = !carregando) { Text("Remover", color = Color(0xFFFF8B91)) }
                                        }
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    carregando = true
                                    onGerenciarSala(room.id, "dissolve", UUID.randomUUID().toString(), "", 0, "") { _, error ->
                                        carregando = false
                                        if (error != null) {
                                            erro = error.localizedMessage.orEmpty()
                                        } else {
                                            salaSelecionada = null
                                            aviso = "Sala dissolvida. As apostas foram devolvidas."
                                            atualizarSalas()
                                        }
                                    }
                                },
                                enabled = !carregando,
                            ) { Text("Dissolver sala e devolver apostas", color = Color(0xFFFF8B91)) }
                        } else if (souParticipante) {
                            Text(
                                if (room.modo == "2v2") "Você entrou. A partida começa quando os 4 jogadores estiverem na sala."
                                else "Você entrou. Aguardando o criador iniciar a partida.",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp,
                            )
                        } else {
                            OutlinedTextField(value = senhaEntrada, onValueChange = { senhaEntrada = it.take(24) }, label = { Text("Senha da sala") }, singleLine = true)
                            Button(onClick = {
                                if (!apostarEmSala(room.apostaCentavos)) return@Button
                                carregando = true
                                onEntrarSala(room.id, senhaEntrada, UUID.randomUUID().toString()) { next, error ->
                                    carregando = false
                                    if (error != null) erro = error.localizedMessage.orEmpty() else {
                                        salaSelecionada = next
                                        aviso = "Você entrou na sala."
                                    }
                                }
                            }, enabled = !carregando, colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde)) {
                                Text("Entrar · ${formatarSaldoTug(room.apostaCentavos)}", color = Color(0xFF101417), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "ready" -> {
                        Text("${room.timeA.size + room.timeB.size} jogadores entraram · sala pronta", color = Cores.Verde, fontSize = 12.sp)
                        if (souCriador) {
                            Button(onClick = {
                                carregando = true
                                onIniciarSala(room.id) { next, error ->
                                    carregando = false
                                    if (error != null) erro = error.localizedMessage.orEmpty() else salaSelecionada = next
                                }
                            }, enabled = !carregando, colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde)) {
                                Text("Iniciar partida", color = Color(0xFF101417), fontWeight = FontWeight.Bold)
                            }
                            (room.timeA + room.timeB).filter { it.uid != uidAtual }.forEach { player ->
                                TextButton(onClick = {
                                    carregando = true
                                    onGerenciarSala(room.id, "kick", UUID.randomUUID().toString(), player.uid, 0, "") { next, error ->
                                        carregando = false
                                        if (error != null) erro = error.localizedMessage.orEmpty() else salaSelecionada = next
                                    }
                                }, enabled = !carregando) { Text("Remover ${player.nome} · reembolsar", color = Color(0xFFFF8B91)) }
                            }
                        } else Text("Aguardando o criador iniciar…", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                    }
                    "active" -> {
                        val buttonScale by animateFloatAsState(
                            targetValue = if (puxoesPendentes > 0) 0.96f else 1f,
                            animationSpec = tween(90),
                            label = "tug-tap-scale",
                        )
                        val puxoesTimeAVisual = room.puxoesTimeA + if (souTimeA) puxoesPendentes else 0
                        val puxoesTimeBVisual = room.puxoesTimeB + if (souTimeB) puxoesPendentes else 0
                        val lead = puxoesTimeAVisual - puxoesTimeBVisual
                        Text("Time A: $puxoesTimeAVisual  ·  $puxoesTimeBVisual :Time B", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        TugRopeVisual(lead)
                        Text("Toques registrados na hora${if (puxoesPendentes > 0) " · +$puxoesPendentes" else ""}", modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, textAlign = TextAlign.Center)
                        Button(
                            onClick = {
                                val nowMs = SystemClock.elapsedRealtime()
                                if (ultimoToqueMs == 0L || nowMs - ultimoToqueMs >= 120L) {
                                    ultimoToqueMs = nowMs
                                    puxoesPendentes += 1
                                    erro = ""
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            enabled = true,
                            modifier = Modifier.fillMaxWidth().height(72.dp).graphicsLayer {
                                scaleX = buttonScale
                                scaleY = buttonScale
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (souTimeA) Cores.Verde else Color(0xFFFF8B91)),
                        ) { Text("TOQUE PARA PUXAR", color = Color(0xFF101417), fontWeight = FontWeight.Black) }
                    }
                    "settled" -> {
                        val venceu = if (room.timeVencedor.isNotBlank()) {
                            if (room.timeVencedor == "A") souTimeA else souTimeB
                        } else room.vencedorUid == uidAtual
                        Text(if (venceu) "Seu time venceu · prêmio ${formatarSaldoTug(room.apostaCentavos * 2)}" else "Partida encerrada · seu time perdeu", color = if (venceu) Cores.Verde else Color(0xFFFF8B91), fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(
                    onClick = {
                        if (room.status in listOf("waiting", "ready")) {
                            val action = if (souCriador) "dissolve" else "leave"
                            carregando = true
                            onGerenciarSala(room.id, action, UUID.randomUUID().toString(), "", 0, "") { _, error ->
                                carregando = false
                                if (error != null) {
                                    erro = error.localizedMessage.orEmpty()
                                } else {
                                    salaSelecionada = null
                                    aviso = if (souCriador) "Sala dissolvida · todas as apostas foram devolvidas." else "Você saiu · sua aposta foi devolvida."
                                    atualizarSalas()
                                }
                            }
                        } else {
                            salaSelecionada = null
                            atualizarSalas()
                        }
                    },
                    enabled = !carregando,
                ) {
                    Text(
                        when {
                            room.status in listOf("waiting", "ready") && souCriador -> "Dissolver sala · reembolsar todos"
                            room.status in listOf("waiting", "ready") -> "Sair da sala · devolver aposta"
                            room.status == "active" -> "Minimizar partida"
                            else -> "Voltar às salas"
                        },
                        color = if (room.status in listOf("waiting", "ready")) Color(0xFFFF8B91) else Color.White,
                    )
                }
            }
        }

        if (salaSelecionada == null) {
            Text("Formato da sala", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1v1", "2v2").forEach { mode ->
                    Button(
                        onClick = { modoSala = mode },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (modoSala == mode) Cores.Turquesa else Color.White.copy(alpha = 0.12f),
                        ),
                    ) { Text(mode, color = if (modoSala == mode) Color(0xFF101417) else Color.White, fontWeight = FontWeight.Bold) }
                }
            }
            OutlinedTextField(
                value = stakeTexto,
                onValueChange = { stakeTexto = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(12) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Aposta por jogador") },
                prefix = { Text("R$ ") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(value = senhaSala, onValueChange = { senhaSala = it.take(24) }, modifier = Modifier.fillMaxWidth(), label = { Text("Senha opcional da sala") }, singleLine = true)
            if (jogadoresDisponiveis.isNotEmpty()) {
                Text("Convide alguém (opcional)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${convidados.size}/${if (modoSala == "2v2") 3 else 1} convites selecionados", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                jogadoresDisponiveis.take(10).forEach { player ->
                    val conviteSelecionado = player.uid in convidados
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(player.apelido, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                        TextButton(onClick = {
                            if (conviteSelecionado) convidados.remove(player.uid)
                            else if (convidados.size < if (modoSala == "2v2") 3 else 1) convidados.add(player.uid)
                            else erro = "Este formato permite até ${if (modoSala == "2v2") 3 else 1} convite(s)."
                        }) { Text(if (conviteSelecionado) "Convidado" else "Convidar") }
                    }
                }
            }
            Button(
                onClick = {
                    val stake = parseSaldoTug(stakeTexto)
                    if (stake == null || !apostarEmSala(stake)) return@Button
                    carregando = true
                    onCriarSala(stake, convidados.toList(), senhaSala, modoSala, UUID.randomUUID().toString()) { room, error ->
                        carregando = false
                        if (error != null) erro = error.localizedMessage.orEmpty() else {
                            salaSelecionada = room
                            erro = ""
                            aviso = "Sala criada. A aposta foi reservada."
                        }
                    }
                },
                enabled = !carregando,
                colors = ButtonDefaults.buttonColors(containerColor = Cores.Turquesa),
            ) { Text("Criar sala", color = Color(0xFF101417), fontWeight = FontWeight.Bold) }

            Text("Salas abertas e convites", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (salas.isEmpty()) Text("Nenhuma sala disponível agora.", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            salas.forEach { room ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color.White.copy(alpha = 0.06f)).clickable { salaSelecionada = room }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        when {
                            room.conviteParaMim -> "Convite · ${room.criadorNome}"
                            room.criadorUid == uidAtual -> "Sua sala · ${room.status}"
                            else -> "Sala pública · ${room.criadorNome}"
                        },
                        color = if (room.conviteParaMim) Cores.Turquesa else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                    val maxPlayers = if (room.modo == "2v2") 4 else 2
                    Text(
                        "${room.modo} · ${room.timeA.size + room.timeB.size}/$maxPlayers jogadores · ${formatarSaldoTug(room.apostaCentavos)} cada${if (room.protegidaPorSenha) " · senha" else ""}",
                        color = Color.White.copy(alpha = 0.64f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
        if (aviso.isNotBlank()) Text(aviso, color = Cores.Verde, fontSize = 11.sp)
        if (erro.isNotBlank()) Text(erro, modifier = Modifier.fillMaxWidth().background(Color(0xFF34272A), RoundedCornerShape(8.dp)).padding(10.dp), color = Color(0xFFFFB7A7), fontSize = 11.sp)
    }
}

@Composable
private fun TugRopeVisual(lead: Int) {
    val progress by animateFloatAsState(
        targetValue = (0.5f + lead / 16f).coerceIn(0.08f, 0.92f),
        animationSpec = tween(durationMillis = 180),
        label = "tug-rope-position",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("◀", color = Cores.Verde, fontWeight = FontWeight.Black)
        BoxWithConstraints(Modifier.weight(1f).height(28.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color(0xFF806344), RoundedCornerShape(6.dp)))
            Box(Modifier.offset(x = maxWidth * progress - 10.dp).size(20.dp).background(Color(0xFFFFD166), RoundedCornerShape(50)))
        }
        Text("▶", color = Color(0xFFFF8B91), fontWeight = FontWeight.Black)
    }
}

private fun formatarSaldoTug(centavos: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(centavos, 2))

private fun parseSaldoTug(valor: String): Long? = runCatching {
    BigDecimal(valor.trim().replace(',', '.')).movePointRight(2).longValueExact()
}.getOrNull()?.takeIf { it > 0 }