package com.example.zeca.cards.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.cards.data.CARTAO_ATIVO
import com.example.zeca.cards.data.Cartao
import com.example.zeca.cards.data.DadosCartao

/**
 * Cartão virtual no formato de um cartão de verdade (proporção 85,6 x 54 mm).
 * Toque para virar. Sem [dados], número e CVV aparecem escondidos.
 */
@Composable
fun CartaoVisual(cartao: Cartao, dados: DadosCartao?, modifier: Modifier = Modifier) {
    var verso by remember { mutableStateOf(false) }
    val rotacao by animateFloatAsState(
        targetValue = if (verso) 180f else 0f,
        animationSpec = tween(450),
        label = "giro-cartao",
    )
    val inativo = cartao.status != CARTAO_ATIVO
    val fundo = if (inativo) {
        Brush.linearGradient(listOf(Color(0xFF4A5459), Color(0xFF22282B)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF0B8F5A), Color(0xFF12383A), Color(0xFF0B1F2A)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.586f)
            .graphicsLayer {
                rotationY = rotacao
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(18.dp))
            .background(fundo)
            .clickable { verso = !verso },
    ) {
        if (rotacao <= 90f) {
            FrenteDoCartao(cartao, dados)
        } else {
            // O verso precisa ser espelhado de volta, senão o texto aparece invertido.
            Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) {
                VersoDoCartao(dados)
            }
        }
    }
}

@Composable
private fun FrenteDoCartao(cartao: Cartao, dados: DadosCartao?) {
    val numero = dados?.numero?.chunked(4)?.joinToString(" ") ?: "•••• •••• •••• ${cartao.final4}"
    val validade = cartao.validade.ifBlank { "••/••" }
    val titular = cartao.titular.ifBlank { "TITULAR" }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ZECA", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("DÉBITO", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            Modifier
                .size(width = 44.dp, height = 32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFC9972B)))),
        )

        Text(
            numero,
            color = Color.White,
            fontSize = 19.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            maxLines = 1,
            softWrap = false,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text("TITULAR", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
                Text(
                    titular,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("VALIDADE", color = Color.White.copy(alpha = 0.6f), fontSize = 8.sp)
                Text(
                    validade,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun VersoDoCartao(dados: DadosCartao?) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF0A0A0A)))
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(34.dp).background(Color(0xFFEDEDED)))
            Box(
                modifier = Modifier.width(64.dp).height(34.dp).background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dados?.cvv ?: "•••",
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            "CVV",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Cartão virtual fictício do Zeca. Sem valor fora do app.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
            modifier = Modifier.padding(18.dp),
        )
    }
}