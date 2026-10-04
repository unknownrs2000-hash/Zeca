package com.example.zeca.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ui.theme.Cores
import java.util.Locale

internal fun formatarValorCampo(centavosBase: Long): String = textoCampoDaBase(centavosBase)

@Composable
internal fun SaldoAnimado(saldoCentavos: Long) {
    val valor by animateFloatAsState(
        targetValue = saldoCentavos.toFloat(),
        animationSpec = tween(900),
        label = "saldo-animado",
    )
    Text(
        text = formatarReais(valor.toLong()),
        color = Cores.Verde,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
internal fun ChipsAposta(
    saldoCentavos: Long,
    apostaCentavos: Long?,
    habilitado: Boolean,
    onEscolher: (Long) -> Unit,
) {
    val moeda = AppCurrencyFormatter.current.value
    val maximo = baseNoCampo(saldoCentavos.coerceAtMost(1_000_000L))
    val metade = baseNoCampo((saldoCentavos / 2).coerceIn(100L, 1_000_000L))
    val opcoes: List<Pair<String, Long>> = listOf(5L, 10L, 50L, 100L).map { inteiro ->
        val centavosNaConta = inteiro * 100L
        formatarValorNaMoeda(codigoMoedaDaConta(), moeda.countryCode, centavosNaConta) to
            centavosDaContaParaBase(centavosNaConta)
    } + listOf("½" to metade, "Máx" to maximo)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        opcoes.forEach { (rotulo, valor) ->
            val disponivel = habilitado && valor in 100L..saldoCentavos
            val selecionado = apostaCentavos == valor
            Box(
                modifier = Modifier
                    .background(
                        if (selecionado) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(50),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .clickable(enabled = disponivel) { onEscolher(valor) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rotulo,
                    color = when {
                        selecionado -> Color(0xFF111418)
                        disponivel -> Color.White
                        else -> Color.White.copy(alpha = 0.35f)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}