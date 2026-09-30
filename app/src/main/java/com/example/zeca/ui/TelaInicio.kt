package com.example.zeca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.Aba
import com.example.zeca.JogadorRanking
import com.example.zeca.ui.theme.Cores

@Composable
fun TelaInicio(
    saldoCentavos: Long,
    atividadeRecente: List<String>,
    ranking: List<JogadorRanking> = emptyList(),
    erroSincronizacao: String = "",
    onAbrirAba: (Aba) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF151A1D), Cores.Fundo, Color(0xFF090D10)),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 36.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("ZECA  /  SOCIAL CLUB", color = Cores.Verde, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Boa sorte hoje.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                if (erroSincronizacao.isNotBlank()) {
                    Text(erroSincronizacao, color = Cores.Laranja, fontSize = 12.sp)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.19f), Color.White.copy(alpha = 0.07f), Cores.Verde.copy(alpha = 0.12f)),
                        ),
                        RoundedCornerShape(24.dp),
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.12f))),
                        RoundedCornerShape(24.dp),
                    )
                    .padding(20.dp),
            ) {
                Text("SALDO", color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(formatarReais(saldoCentavos), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AcaoCarteira("Adicionar via Pix", Modifier.weight(1f), true) { onAbrirAba(Aba.Carteira) }
                    AcaoCarteira("Sacar", Modifier.weight(1f), false) { onAbrirAba(Aba.Carteira) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Jogos populares", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    JogoItem("Slots", Cores.Laranja, Modifier.weight(1f)) { onAbrirAba(Aba.Jogos) }
                    JogoItem("Roleta", Cores.Turquesa, Modifier.weight(1f)) { onAbrirAba(Aba.Jogos) }
                    JogoItem("Crash", Color(0xFFFF8290), Modifier.weight(1f)) { onAbrirAba(Aba.Jogos) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Atividade recente", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                ) {
                    if (atividadeRecente.isEmpty()) {
                        Text("Suas partidas e movimentações aparecerão aqui.", color = Color.White.copy(alpha = 0.65f), fontSize = 14.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            atividadeRecente.forEach { item ->
                                Text(item, color = Color.White.copy(alpha = 0.82f), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ranking de saldo", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (ranking.isEmpty()) {
                    Text("O ranking aparecerá quando outros jogadores entrarem.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                } else {
                    ranking.take(10).forEachIndexed { index, jogador ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(15.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", color = Cores.Verde, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(jogador.apelido, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Nível ${jogador.nivel}", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
                            }
                            Text(formatarReais(jogador.saldoCentavos), color = Cores.Turquesa, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text("A chave Pix dos jogadores não é pública; use-a na Carteira para transferir.", color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun JogoItem(nome: String, cor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(88.dp)
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Cores.Cartao.copy(alpha = 0.72f))),
                RoundedCornerShape(18.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.19f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(nome, color = cor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun AcaoCarteira(texto: String, modifier: Modifier, destaque: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (destaque) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Text(
            texto,
            color = if (destaque) Color(0xFF101416) else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun TelaEmBreve(titulo: String, descricao: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(titulo, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(descricao, color = Cores.Cinza, fontSize = 16.sp)
    }
}