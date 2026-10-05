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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = onVoltar, modifier = Modifier.align(Alignment.Start)) { Text("Voltar") }
        Text("Maquininha", style = MaterialTheme.typography.headlineSmall)

        val atual = cobranca
        if (atual == null) {
            OutlinedTextField(
                value = valorTexto,
                onValueChange = { valorTexto = it },
                label = { Text("Valor (${moedaDaConta()})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = descricao,
                onValueChange = { if (it.length <= 80) descricao = it },
                label = { Text("Descrição (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::gerar, enabled = !carregando, modifier = Modifier.fillMaxWidth()) {
                if (carregando) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Gerar cobrança")
            }
        } else {
            Text(formatarBase(atual.valorBaseCentavos), style = MaterialTheme.typography.headlineMedium)
            if (atual.descricao.isNotBlank()) Text(atual.descricao)

            when (atual.status) {
                STATUS_PAGA -> {
                    Text("Pagamento recebido!", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = ::reiniciar, modifier = Modifier.fillMaxWidth()) { Text("Nova cobrança") }
                }
                STATUS_EXPIRADA -> {
                    Text("Cobrança expirada.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = ::reiniciar, modifier = Modifier.fillMaxWidth()) { Text("Nova cobrança") }
                }
                else -> {
                    QrDaCobranca(CardsRepository.textoQr(atual.id))
                    Text("Ou encoste o celular de quem vai pagar neste aparelho, com a tela acesa.")
                    val restante = ((atual.expiraEmMs - agoraMs) / 1_000).coerceAtLeast(0)
                    Text("Aguardando pagamento · expira em %d:%02d".format(restante / 60, restante % 60))
                    OutlinedButton(onClick = ::reiniciar, modifier = Modifier.fillMaxWidth()) { Text("Fechar") }
                }
            }
        }

        erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}