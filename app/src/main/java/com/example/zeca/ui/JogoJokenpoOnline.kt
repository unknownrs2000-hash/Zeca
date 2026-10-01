package com.example.zeca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.PartidaJokenpo
import com.example.zeca.ResultadoJokenpo
import com.example.zeca.ui.theme.Cores
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

@Composable
internal fun JogoJokenpoOnline(
    uidAtual: String,
    onObservarFila: ((String, String, Boolean) -> Unit) -> ListenerRegistration,
    onObservarPartida: (String, (PartidaJokenpo?, Exception?) -> Unit) -> ListenerRegistration,
    onBuscarAdversario: (String, (String, String, Exception?) -> Unit) -> Unit,
    onCancelarFila: ((Exception?) -> Unit) -> Unit,
    onJogar: (String, String, String, (ResultadoJokenpo?, Exception?) -> Unit) -> Unit,
) {
    var statusFila by rememberSaveable { mutableStateOf("") }
    var matchId by rememberSaveable { mutableStateOf("") }
    var jaEscolheu by rememberSaveable { mutableStateOf(false) }
    var partida by remember { mutableStateOf<PartidaJokenpo?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var erro by rememberSaveable { mutableStateOf("") }
    val observarFilaAtual by rememberUpdatedState(onObservarFila)
    val observarPartidaAtual by rememberUpdatedState(onObservarPartida)

    DisposableEffect(uidAtual) {
        val registration = observarFilaAtual { status, newMatchId, hasPlayed ->
            statusFila = status
            jaEscolheu = hasPlayed
            if (matchId != newMatchId) {
                matchId = newMatchId
                partida = null
            }
            if (status.isBlank() && newMatchId.isBlank()) jaEscolheu = false
        }
        onDispose { registration.remove() }
    }

    DisposableEffect(matchId) {
        if (matchId.isBlank()) {
            partida = null
            onDispose { }
        } else {
            val registration = observarPartidaAtual(matchId) { state, error ->
                partida = state
                if (error != null) erro = error.localizedMessage ?: "Não foi possível atualizar a partida."
            }
            onDispose { registration.remove() }
        }
    }

    val adversarioUid = partida?.jogadorUids?.firstOrNull { it != uidAtual }.orEmpty()
    val statusPartida = partida?.status.orEmpty()
    val partidaConcluida = statusPartida == "completed"
    val resultadoPessoal = when {
        !partidaConcluida -> ""
        partida?.vencedorUid.isNullOrBlank() -> "Empate"
        partida?.vencedorUid == uidAtual -> "Você venceu"
        else -> "Seu adversário venceu"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.055f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Cores.Turquesa.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("1v1", color = Cores.Turquesa, fontSize = 12.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Jokenpô online", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Partida amistosa · sem aposta", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
            }
        }

        when {
            partidaConcluida -> {
                Text(resultadoPessoal, color = if (resultadoPessoal == "Você venceu") Cores.Verde else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(partida?.resultado.orEmpty(), color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                Button(
                    onClick = {
                        ocupado = true
                        erro = ""
                        onBuscarAdversario(UUID.randomUUID().toString()) { status, newMatchId, error ->
                            ocupado = false
                            if (error != null) erro = error.localizedMessage ?: "Não foi possível buscar uma partida."
                            else {
                                statusFila = status
                                matchId = newMatchId
                                jaEscolheu = false
                                partida = null
                            }
                        }
                    },
                    enabled = !ocupado,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ocupado) "Buscando..." else "Nova partida") }
            }
            statusPartida == "playing" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerChip("Você", partida?.nomesJogadores?.get(uidAtual).orEmpty(), Modifier.weight(1f))
                    Text("VS", color = Cores.Turquesa, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    PlayerChip("Adversário", partida?.nomesJogadores?.get(adversarioUid).orEmpty(), Modifier.weight(1f))
                }
                if (jaEscolheu) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Cores.Turquesa, strokeWidth = 2.dp)
                        Text("Jogada enviada · aguardando o adversário", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    }
                } else {
                    Text("Escolha sua jogada", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("rock" to "Pedra", "paper" to "Papel", "scissors" to "Tesoura").forEach { (value, label) ->
                            Button(
                                onClick = {
                                    ocupado = true
                                    erro = ""
                                    onJogar(matchId, value, UUID.randomUUID().toString()) { result, error ->
                                        ocupado = false
                                        if (error != null || result == null) {
                                            erro = error?.localizedMessage ?: "Não foi possível enviar sua jogada."
                                        } else {
                                            jaEscolheu = true
                                            if (result.status == "completed") {
                                                partida = partida?.copy(
                                                    status = result.status,
                                                    vencedorUid = result.vencedorUid,
                                                    resultado = result.resultado,
                                                )
                                            }
                                        }
                                    }
                                },
                                enabled = !ocupado,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (ocupado) Color.White.copy(alpha = 0.1f) else Cores.Turquesa.copy(alpha = 0.9f),
                                ),
                            ) {
                                Text(label, color = Color(0xFF10201D), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
            statusFila == "waiting" -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Cores.Turquesa, strokeWidth = 2.dp)
                    Text("Procurando adversário...", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        ocupado = true
                        onCancelarFila { error ->
                            ocupado = false
                            if (error != null) erro = error.localizedMessage ?: "Não foi possível cancelar a busca."
                            else {
                                statusFila = ""
                                matchId = ""
                                jaEscolheu = false
                                partida = null
                            }
                        }
                    },
                    enabled = !ocupado,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                ) { Text("Cancelar busca", color = Color.White) }
            }
            matchId.isNotBlank() -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Cores.Turquesa, strokeWidth = 2.dp)
                    Text("Abrindo a partida...", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
            else -> {
                Text("Encontre alguém online para uma partida rápida de Jokenpô.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                Button(
                    onClick = {
                        ocupado = true
                        erro = ""
                        onBuscarAdversario(UUID.randomUUID().toString()) { status, newMatchId, error ->
                            ocupado = false
                            if (error != null) erro = error.localizedMessage ?: "Não foi possível buscar uma partida."
                            else {
                                statusFila = status
                                matchId = newMatchId
                                jaEscolheu = false
                                partida = null
                            }
                        }
                    },
                    enabled = !ocupado,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (ocupado) "Buscando..." else "Buscar adversário") }
            }
        }

        if (erro.isNotBlank()) Text(erro, color = Cores.Laranja, fontSize = 12.sp)
    }
}

@Composable
private fun PlayerChip(rotulo: String, nome: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(rotulo, color = Cores.Turquesa, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(nome.ifBlank { "Jogador" }, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}