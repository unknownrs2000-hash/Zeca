package com.example.zeca.cards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.zeca.FirebaseRepository
import com.example.zeca.cards.data.CardsRepository
import com.example.zeca.cards.data.CobrancaMaquininha
import com.example.zeca.cards.data.ComprovantePagamento
import com.example.zeca.cards.data.textoStatusCobranca
import com.example.zeca.cards.money.formatarBase
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun TelaPagar(onVoltar: () -> Unit) {
    val contexto = LocalContext.current
    var cobranca by remember { mutableStateOf<CobrancaMaquininha?>(null) }
    var comprovante by remember { mutableStateOf<ComprovantePagamento?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    fun abrirCobranca(id: String) {
        carregando = true
        CardsRepository.buscarCobranca(id) { encontrada, falha ->
            carregando = false
            if (encontrada != null && falha == null) cobranca = encontrada
            else erro = falha?.localizedMessage ?: "Não foi possível abrir a cobrança."
        }
    }

    fun lerQr() {
        erro = null
        val opcoes = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(contexto, opcoes).startScan()
            .addOnSuccessListener { leitura ->
                val id = CardsRepository.idDoQr(leitura.rawValue)
                if (id == null) erro = "Este QR code não é uma cobrança do Zeca." else abrirCobranca(id)
            }
            .addOnCanceledListener { }
            .addOnFailureListener { falha ->
                erro = falha.localizedMessage ?: "Não foi possível ler o QR code."
            }
    }

    fun confirmarPagamento(atual: CobrancaMaquininha) {
        erro = null
        carregando = true
        // Confere o saldo antes, para mostrar uma mensagem clara em vez de um erro genérico do servidor.
        FirebaseRepository.carregarSaldo { saldo, _ ->
            if (saldo != null && saldo < atual.valorBaseCentavos) {
                carregando = false
                erro = "Saldo insuficiente para pagar esta cobrança."
            } else {
                CardsRepository.pagarCobranca(atual.id) { recibo, falha ->
                    carregando = false
                    if (recibo != null && falha == null) comprovante = recibo
                    else erro = falha?.localizedMessage ?: "Não foi possível concluir o pagamento."
                }
            }
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
        Text("Pagar com QR", style = MaterialTheme.typography.headlineMedium)

        val recibo = comprovante
        val atual = cobranca
        when {
            recibo != null -> {
                Text("Pagamento confirmado ✔", style = MaterialTheme.typography.titleLarge)
                Text(formatarBase(recibo.valorBaseCentavos), style = MaterialTheme.typography.displaySmall)
                if (recibo.descricao.isNotBlank()) Text(recibo.descricao)
                recibo.saldoBaseCentavos?.let { Text("Saldo atual: ${formatarBase(it)}") }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onVoltar) { Text("Concluir") }
            }

            atual != null -> {
                Text("Pagar para", style = MaterialTheme.typography.labelLarge)
                Text(atual.recebedorNome, style = MaterialTheme.typography.titleLarge)
                if (atual.recebedorUsername.isNotBlank()) Text("@${atual.recebedorUsername}")
                Text(formatarBase(atual.valorBaseCentavos), style = MaterialTheme.typography.displaySmall)
                if (atual.descricao.isNotBlank()) Text(atual.descricao)
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (atual.status == "pending") {
                    Button(
                        enabled = !carregando,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmarPagamento(atual) },
                    ) {
                        if (carregando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("Pagar ${formatarBase(atual.valorBaseCentavos)}")
                    }
                } else {
                    Text(
                        "Esta cobrança está ${textoStatusCobranca(atual.status)}.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { cobranca = null; erro = null },
                ) { Text("Cancelar") }
            }

            else -> {
                Text("Escaneie o QR code da maquininha de quem está cobrando.")
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = !carregando,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { lerQr() },
                ) {
                    if (carregando) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Ler QR code")
                }
            }
        }
    }
}