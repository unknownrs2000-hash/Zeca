package com.example.zeca.cards.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.zeca.cards.data.CardsRepository
import com.example.zeca.cards.data.CobrancaMaquininha
import com.example.zeca.cards.money.formatarBase
import com.example.zeca.ui.codigoMoedaDaConta
import com.example.zeca.ui.parseValorBaseCentavos
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

private fun gerarQr(texto: String, tamanho: Int = 640): ImageBitmap {
    val matriz = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, tamanho, tamanho)
    val pixels = IntArray(tamanho * tamanho) { indice ->
        if (matriz.get(indice % tamanho, indice / tamanho)) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    }
    val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, tamanho, 0, 0, tamanho, tamanho)
    return bitmap.asImageBitmap()
}

@Composable
fun TelaMaquininha(onVoltar: () -> Unit) {
    var valorTexto by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf(CardsRepository.novoRequestId()) }
    var cobranca by remember { mutableStateOf<CobrancaMaquininha?>(null) }
    var statusAtual by remember { mutableStateOf("pending") }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var agoraMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Consulta o servidor até a cobrança deixar de estar pendente. O status do servidor manda.
    LaunchedEffect(cobranca?.id) {
        val atual = cobranca ?: return@LaunchedEffect
        while (statusAtual == "pending") {
            delay(2_000)
            agoraMs = System.currentTimeMillis()
            val (nova, falha) = CardsRepository.consultarCobranca(atual.id)
            if (falha == null && nova != null) statusAtual = nova.status
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onVoltar, modifier = Modifier.align(Alignment.Start)) { Text("Voltar") }
        Text("Maquininha", style = MaterialTheme.typography.headlineMedium)

        val atual = cobranca
        if (atual == null) {
            Text("Digite o valor, gere o QR e peça para a outra pessoa escanear.")
            OutlinedTextField(
                value = valorTexto,
                onValueChange = { valorTexto = it; requestId = CardsRepository.novoRequestId() },
                label = { Text("Valor (${codigoMoedaDaConta()})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = descricao,
                onValueChange = {
                    descricao = it.take(80)
                    requestId = CardsRepository.novoRequestId()
                },
                label = { Text("Descrição (opcional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !carregando,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val base = parseValorBaseCentavos(valorTexto)
                    if (base == null || base <= 0L) {
                        erro = "Digite um valor válido."
                    } else {
                        erro = null
                        carregando = true
                        CardsRepository.criarCobranca(requestId, base, descricao.trim()) { criada, falha ->
                            carregando = false
                            if (criada != null && falha == null) {
                                statusAtual = criada.status
                                cobranca = criada
                            } else {
                                erro = falha?.localizedMessage ?: "Não foi possível criar a cobrança."
                            }
                        }
                    }
                },
            ) {
                if (carregando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Gerar QR")
            }
        } else {
            val qr = remember(atual.id) { gerarQr(CardsRepository.textoQr(atual.id)) }
            Text(formatarBase(atual.valorBaseCentavos), style = MaterialTheme.typography.displaySmall)
            if (atual.descricao.isNotBlank()) Text(atual.descricao)
            Image(
                bitmap = qr,
                contentDescription = "QR code da cobrança",
                modifier = Modifier
                    .size(260.dp)
                    .background(Color.White),
            )
            when (statusAtual) {
                "paid" -> Text("Pago ✔", style = MaterialTheme.typography.titleLarge)
                "pending" -> {
                    val restante = ((atual.expiraEmMs - agoraMs) / 1000).coerceAtLeast(0)
                    Text("Aguardando pagamento · expira em ${restante}s")
                }
                else -> Text("Cobrança encerrada (${statusAtual}).", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    cobranca = null
                    valorTexto = ""
                    descricao = ""
                    statusAtual = "pending"
                    requestId = CardsRepository.novoRequestId()
                },
            ) { Text("Nova cobrança") }
        }
    }
}