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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zeca.FirebaseRepository
import com.example.zeca.cards.data.CARTAO_ATIVO
import com.example.zeca.cards.data.Cartao
import com.example.zeca.cards.data.CardsRepository
import com.example.zeca.cards.data.Cobranca
import com.example.zeca.cards.data.ComprovantePagamento
import com.example.zeca.cards.data.STATUS_PAGA
import com.example.zeca.cards.data.STATUS_PENDENTE
import com.example.zeca.cards.data.mensagemDeErro
import com.example.zeca.cards.money.formatarBase
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private sealed interface Etapa {
    object Inicio : Etapa
    object Carregando : Etapa
    data class Confirmar(val cobranca: Cobranca, val pagando: Boolean = false) : Etapa
    data class Sucesso(val comprovante: ComprovantePagamento) : Etapa
}

/** [chargeIdInicial] é opcional: serve para abrir direto uma cobrança (ex.: link) sem ler o QR. */
@Composable
fun TelaPagar(chargeIdInicial: String? = null, onVoltar: () -> Unit) {
    val contexto = LocalContext.current
    var etapa by remember { mutableStateOf<Etapa>(Etapa.Inicio) }
    var erro by remember { mutableStateOf<String?>(null) }
    var cartoes by remember { mutableStateOf<List<Cartao>>(emptyList()) }
    var cartaoSelecionado by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }

    fun carregar(id: String) {
        erro = null
        etapa = Etapa.Carregando
        CardsRepository.buscarCobranca(id) { c, e ->
            if (e != null || c == null) {
                erro = mensagemDeErro(e)
                etapa = Etapa.Inicio
            } else if (c.recebedorUid == FirebaseRepository.auth.currentUser?.uid) {
                erro = "Você não pode pagar a própria cobrança."
                etapa = Etapa.Inicio
            } else if (c.status != STATUS_PENDENTE) {
                erro = if (c.status == STATUS_PAGA) "Esta cobrança já foi paga." else "Esta cobrança expirou. Peça uma nova."
                etapa = Etapa.Inicio
            } else {
                etapa = Etapa.Confirmar(c)
            }
        }
    }

    fun escanear() {
        erro = null
        val opcoes = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        GmsBarcodeScanning.getClient(contexto, opcoes).startScan()
            .addOnSuccessListener { codigo ->
                val id = CardsRepository.idDoQr(codigo.rawValue.orEmpty())
                if (id == null) erro = "Este QR code não é de uma cobrança do Zeca." else carregar(id)
            }
            .addOnFailureListener { erro = "Não foi possível abrir a câmera para ler o QR." }
    }

    fun pagar(c: Cobranca) {
        erro = null
        etapa = Etapa.Confirmar(c, pagando = true)
        val cartaoId = cartaoSelecionado
        val pinEnviado = if (cartaoId != null) pin else null
        CardsRepository.pagarCobranca(c.id, cartaoId, pinEnviado) { comprovante, e ->
            pin = ""
            if (e != null || comprovante == null) {
                // Continua na confirmação: repetir é seguro, o servidor usa um ID fixo por cobrança.
                erro = mensagemDeErro(e)
                etapa = Etapa.Confirmar(c)
            } else {
                etapa = Etapa.Sucesso(comprovante)
            }
        }
    }

    LaunchedEffect(chargeIdInicial) {
        if (!chargeIdInicial.isNullOrBlank()) carregar(chargeIdInicial)
    }

    // Se falhar (ex.: servidor sem a Fase 2), segue só com a carteira.
    LaunchedEffect(Unit) {
        CardsRepository.listarCartoes { lista, e ->
            if (e == null && lista != null) {
                cartoes = lista.filter { it.status == CARTAO_ATIVO && !it.travado }
            }
        }
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
        Text("Pagar", style = MaterialTheme.typography.headlineSmall)

        when (val atual = etapa) {
            Etapa.Inicio -> {
                Text("Leia o QR code da maquininha de quem vai receber.")
                Button(onClick = ::escanear, modifier = Modifier.fillMaxWidth()) { Text("Ler QR code") }
            }
            Etapa.Carregando -> CircularProgressIndicator()
            is Etapa.Confirmar -> {
                val c = atual.cobranca
                Text("Pagar para", style = MaterialTheme.typography.labelLarge)
                Text(c.recebedorNome, style = MaterialTheme.typography.titleLarge)
                if (c.recebedorUsername.isNotBlank()) Text("@${c.recebedorUsername}")
                Text(formatarBase(c.valorBaseCentavos), style = MaterialTheme.typography.headlineMedium)
                if (c.descricao.isNotBlank()) Text(c.descricao)
                if (cartoes.isNotEmpty()) {
                    Text("Pagar com", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = { cartaoSelecionado = null; pin = "" },
                        enabled = !atual.pagando,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (cartaoSelecionado == null) "✓ Saldo da carteira" else "Saldo da carteira") }
                    cartoes.forEach { cartao ->
                        OutlinedButton(
                            onClick = { cartaoSelecionado = cartao.id; pin = "" },
                            enabled = !atual.pagando,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text((if (cartaoSelecionado == cartao.id) "✓ " else "") + "${cartao.rotulo} •••• ${cartao.final4}")
                        }
                    }
                    if (cartaoSelecionado != null) {
                        CampoPin(
                            valor = pin,
                            onValor = { pin = it },
                            etiqueta = "PIN DO CARTÃO",
                            enabled = !atual.pagando,
                            autoFoco = false,
                        )
                    }
                }
                Button(
                    onClick = { pagar(c) },
                    enabled = !atual.pagando && (cartaoSelecionado == null || pin.length == 4),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (atual.pagando) CircularProgressIndicator(modifier = Modifier.padding(2.dp)) else Text("Confirmar pagamento")
                }
                OutlinedButton(
                    onClick = { etapa = Etapa.Inicio; erro = null },
                    enabled = !atual.pagando,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancelar") }
            }
            is Etapa.Sucesso -> {
                Text("Pagamento concluído!", style = MaterialTheme.typography.titleLarge)
                Text(formatarBase(atual.comprovante.valorBaseCentavos), style = MaterialTheme.typography.headlineMedium)
                Text("Saldo atual: ${formatarBase(atual.comprovante.saldoBaseCentavos)}")
                Button(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) { Text("Concluir") }
            }
        }

        erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}