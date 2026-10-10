package com.example.zeca.cards.ui

import android.app.Activity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.FirebaseRepository
import com.example.zeca.ui.theme.Cores
import com.example.zeca.cards.data.CARTAO_ATIVO
import com.example.zeca.cards.data.Cartao
import com.example.zeca.cards.data.CardsRepository
import com.example.zeca.cards.data.Cobranca
import com.example.zeca.cards.data.ComprovantePagamento
import com.example.zeca.cards.data.STATUS_PAGA
import com.example.zeca.cards.data.STATUS_PENDENTE
import com.example.zeca.cards.data.mensagemDeErro
import com.example.zeca.cards.money.formatarBase
import com.example.zeca.cards.nfc.NfcLeitor
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
    var lendoNfc by remember { mutableStateOf(false) }

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

    fun pararNfc() {
        lendoNfc = false
        contexto.encontrarActivity()?.let { NfcLeitor.parar(it) }
    }

    // Le o ID da cobranca no celular do recebedor e segue o mesmo caminho do QR (valor e nome antes de pagar).
    fun aproximar() {
        val atividade = contexto.encontrarActivity()
        if (atividade == null) {
            erro = "Não foi possível acessar a tela para ler o NFC."
            return
        }
        if (!NfcLeitor.existe(atividade)) {
            erro = "Este aparelho não tem NFC."
            return
        }
        if (!NfcLeitor.ligado(atividade)) {
            erro = "Ligue o NFC nas configurações do aparelho."
            return
        }
        erro = null
        lendoNfc = true
        NfcLeitor.iniciar(
            atividade,
            aoLer = { id ->
                pararNfc()
                carregar(id)
            },
            aoFalhar = { mensagem -> erro = mensagem },
        )
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

    DisposableEffect(Unit) {
        onDispose { contexto.encontrarActivity()?.let { NfcLeitor.parar(it) } }
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
    ) {
        CabecalhoZeca(
            titulo = "Pagar",
            subtitulo = when (etapa) {
                Etapa.Inicio -> "Leia o QR code ou encoste o celular na maquininha de quem vai receber."
                is Etapa.Confirmar -> "Confira o valor e quem recebe antes de confirmar."
                else -> null
            },
            onVoltar = onVoltar,
        )

        when (val atual = etapa) {
            Etapa.Inicio -> {
                BotaoPrimario(texto = "Ler QR code", onClick = ::escanear, modifier = Modifier.fillMaxWidth())
                BotaoSecundario(
                    texto = if (lendoNfc) "Parar leitura NFC" else "Aproximar celulares (NFC)",
                    onClick = { if (lendoNfc) pararNfc() else aproximar() },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (lendoNfc) {
                    Text(
                        "Encoste as costas deste celular no celular de quem vai receber.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
            }
            Etapa.Carregando -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Cores.Verde)
            }
            is Etapa.Confirmar -> {
                val c = atual.cobranca
                PainelZeca {
                    RotuloZeca("PAGAR PARA")
                    Text(c.recebedorNome, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    if (c.recebedorUsername.isNotBlank()) {
                        Text("@${c.recebedorUsername}", color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp)
                    }
                    Text(
                        formatarBase(c.valorBaseCentavos),
                        color = Cores.Verde,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                    )
                    if (c.descricao.isNotBlank()) {
                        Text(c.descricao, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    }
                }
                if (cartoes.isNotEmpty()) {
                    RotuloZeca("PAGAR COM")
                    OpcaoDePagamento(
                        texto = "Saldo da carteira",
                        selecionada = cartaoSelecionado == null,
                        enabled = !atual.pagando,
                        onClick = { cartaoSelecionado = null; pin = "" },
                    )
                    cartoes.forEach { cartao ->
                        OpcaoDePagamento(
                            texto = "${cartao.rotulo} •••• ${cartao.final4}",
                            selecionada = cartaoSelecionado == cartao.id,
                            enabled = !atual.pagando,
                            onClick = { cartaoSelecionado = cartao.id; pin = "" },
                        )
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
                BotaoPrimario(
                    texto = if (atual.pagando) "Pagando..." else "Confirmar pagamento",
                    onClick = { pagar(c) },
                    enabled = !atual.pagando && (cartaoSelecionado == null || pin.length == 4),
                    modifier = Modifier.fillMaxWidth(),
                )
                BotaoSecundario(
                    texto = "Cancelar",
                    onClick = { etapa = Etapa.Inicio; erro = null },
                    enabled = !atual.pagando,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is Etapa.Sucesso -> {
                PainelZeca {
                    RotuloZeca("PAGAMENTO CONCLUÍDO")
                    Text(
                        formatarBase(atual.comprovante.valorBaseCentavos),
                        color = Cores.Verde,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Saldo atual: ${formatarBase(atual.comprovante.saldoBaseCentavos)}",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 13.sp,
                    )
                }
                BotaoPrimario(texto = "Concluir", onClick = onVoltar, modifier = Modifier.fillMaxWidth())
            }
        }

        erro?.let { TextoErro(it) }
    }
}

// Opção escolhida fica em verde cheio; as outras ficam só com borda.
@Composable
private fun OpcaoDePagamento(texto: String, selecionada: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (selecionada) {
        BotaoPrimario(texto = "✓ $texto", onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth())
    } else {
        BotaoSecundario(texto = texto, onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth())
    }
}

// No Compose o contexto pode vir embrulhado em ContextWrapper; desembrulha até achar a Activity.
private fun android.content.Context.encontrarActivity(): Activity? {
    var atual: android.content.Context? = this
    while (atual is android.content.ContextWrapper) {
        if (atual is Activity) return atual
        atual = atual.baseContext
    }
    return null
}