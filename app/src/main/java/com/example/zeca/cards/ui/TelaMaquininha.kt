package com.example.zeca.cards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ui.theme.Cores
import com.example.zeca.cards.data.CardsRepository
import com.example.zeca.cards.data.Cobranca
import com.example.zeca.cards.data.STATUS_EXPIRADA
import com.example.zeca.cards.data.STATUS_PAGA
import com.example.zeca.cards.data.STATUS_PENDENTE
import com.example.zeca.cards.data.mensagemDeErro
import com.example.zeca.cards.money.COBRANCA_MAX_BASE_CENTAVOS
import com.example.zeca.cards.money.COBRANCA_MIN_BASE_CENTAVOS
import com.example.zeca.cards.money.formatarBase
import com.example.zeca.cards.money.moedaDaConta
import com.example.zeca.cards.money.valorDigitadoParaBase
import com.example.zeca.cards.nfc.NfcProtocolo
import kotlinx.coroutines.delay
import java.util.UUID

@Composable
fun TelaMaquininha(onVoltar: () -> Unit) {
    var valorTexto by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var cobranca by remember { mutableStateOf<Cobranca?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var agoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Relógio só para o contador. Quem decide se expirou é o servidor (status da cobrança).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            agoraMs = System.currentTimeMillis()
        }
    }

    // Enquanto a cobrança está pendente, pergunta ao servidor a cada 2 s se já foi paga.
    LaunchedEffect(cobranca?.id) {
        val id = cobranca?.id ?: return@LaunchedEffect
        while (cobranca?.status == STATUS_PENDENTE) {
            delay(2_000)
            CardsRepository.buscarCobranca(id) { atual, _ ->
                if (atual != null && cobranca?.id == atual.id) cobranca = atual
            }
        }
    }

    // Enquanto a cobranca esta pendente, o toque NFC entrega o ID dela. Limpa ao pagar, expirar ou sair.
    DisposableEffect(cobranca?.id, cobranca?.status) {
        NfcProtocolo.chargeIdAtivo = if (cobranca?.status == STATUS_PENDENTE) cobranca?.id else null
        onDispose { NfcProtocolo.chargeIdAtivo = null }
    }

    fun gerar() {
        val base = valorDigitadoParaBase(valorTexto)
        if (base == null || base < COBRANCA_MIN_BASE_CENTAVOS || base > COBRANCA_MAX_BASE_CENTAVOS) {
            erro = "Informe um valor entre ${formatarBase(COBRANCA_MIN_BASE_CENTAVOS)} e " +
                formatarBase(COBRANCA_MAX_BASE_CENTAVOS) + "."
            return
        }
        erro = null
        carregando = true
        CardsRepository.criarCobranca(base, descricao.trim(), UUID.randomUUID().toString()) { nova, e ->
            carregando = false
            if (e != null || nova == null) erro = mensagemDeErro(e) else cobranca = nova
        }
    }

    fun reiniciar() {
        cobranca = null
        valorTexto = ""
        descricao = ""
        erro = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CabecalhoZeca(
            titulo = "Maquininha",
            subtitulo = if (cobranca == null) "Cobre por QR code ou por aproximação do celular." else null,
            onVoltar = onVoltar,
        )

        val atual = cobranca
        if (atual == null) {
            CampoZeca(
                valor = valorTexto,
                onValor = { valorTexto = it },
                etiqueta = "Valor (${moedaDaConta()})",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CampoZeca(
                valor = descricao,
                onValor = { if (it.length <= 80) descricao = it },
                etiqueta = "Descrição (opcional)",
            )
            BotaoPrimario(
                texto = if (carregando) "Gerando..." else "Gerar cobrança",
                onClick = ::gerar,
                enabled = !carregando,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PainelZeca {
                RotuloZeca(
                    when (atual.status) {
                        STATUS_PAGA -> "PAGAMENTO RECEBIDO"
                        STATUS_EXPIRADA -> "COBRANÇA EXPIRADA"
                        else -> "AGUARDANDO PAGAMENTO"
                    },
                )
                Text(
                    formatarBase(atual.valorBaseCentavos),
                    color = if (atual.status == STATUS_EXPIRADA) CorPerigo else Cores.Verde,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                )
                if (atual.descricao.isNotBlank()) {
                    Text(atual.descricao, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                }
            }

            when (atual.status) {
                STATUS_PAGA, STATUS_EXPIRADA -> {
                    BotaoPrimario(texto = "Nova cobrança", onClick = ::reiniciar, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        QrDaCobranca(CardsRepository.textoQr(atual.id))
                    }
                    Text(
                        "Ou encoste o celular de quem vai pagar neste aparelho, com a tela acesa.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                    val restante = ((atual.expiraEmMs - agoraMs) / 1_000).coerceAtLeast(0)
                    Text(
                        "Expira em %d:%02d".format(restante / 60, restante % 60),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    BotaoSecundario(texto = "Fechar", onClick = ::reiniciar, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        erro?.let { TextoErro(it) }
    }
}