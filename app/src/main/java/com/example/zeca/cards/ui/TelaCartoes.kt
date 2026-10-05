package com.example.zeca.cards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ui.theme.Cores
import com.example.zeca.cards.data.CARTAO_ATIVO
import com.example.zeca.cards.data.CARTAO_BLOQUEADO
import com.example.zeca.cards.data.CARTAO_CANCELADO
import com.example.zeca.cards.data.Cartao
import com.example.zeca.cards.data.CardsRepository
import com.example.zeca.cards.data.DadosCartao
import com.example.zeca.cards.data.PagamentoCartao
import com.example.zeca.cards.data.mensagemDeErro
import com.example.zeca.cards.money.formatarBase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun soDigitosPin(texto: String): String = texto.filter { it.isDigit() }.take(4)

private fun nomeDoStatus(cartao: Cartao): String = when {
    cartao.status == CARTAO_CANCELADO -> "Cancelado"
    cartao.status == CARTAO_BLOQUEADO -> "Bloqueado"
    cartao.travado -> "Travado por erros de PIN"
    else -> "Ativo"
}

private fun formatarData(ms: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(ms))

@Composable
fun TelaCartoes(onVoltar: () -> Unit) {
    var cartoes by remember { mutableStateOf<List<Cartao>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var criando by remember { mutableStateOf(false) }
    var trocandoPin by remember { mutableStateOf<Cartao?>(null) }
    var verHistorico by remember { mutableStateOf<Cartao?>(null) }
    var cancelando by remember { mutableStateOf<Cartao?>(null) }
    var verDados by remember { mutableStateOf<Cartao?>(null) }
    // Número e CVV ficam só na memória da tela e somem ao sair dela.
    var dadosVisiveis by remember { mutableStateOf<Map<String, DadosCartao>>(emptyMap()) }

    fun recarregar() {
        CardsRepository.listarCartoes { lista, e ->
            carregando = false
            if (e != null || lista == null) {
                erro = mensagemDeErro(e)
            } else {
                erro = null
                cartoes = lista
            }
        }
    }

    fun mudarStatus(cartao: Cartao, status: String) {
        ocupado = true
        erro = null
        CardsRepository.definirStatusCartao(cartao.id, status) { e ->
            ocupado = false
            if (e != null) erro = mensagemDeErro(e)
            recarregar()
        }
    }

    LaunchedEffect(Unit) { recarregar() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151A1D), Cores.Fundo, Color(0xFF090D10))))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onVoltar) { Text("Voltar", color = Cores.Turquesa) }
        Text("Meus cartões", color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
        Text(
            "Cartão virtual de débito. O pagamento usa o saldo da sua conta e pede o PIN.",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 13.sp,
        )

        BotaoPrimario(
            texto = "Criar cartão",
            onClick = { criando = true },
            enabled = !carregando && !ocupado,
            modifier = Modifier.fillMaxWidth(),
        )

        if (carregando) CircularProgressIndicator()

        if (!carregando && cartoes.isEmpty() && erro == null) {
            Text("Você ainda não tem cartões.")
        }

        cartoes.forEach { cartao ->
            val cancelado = cartao.status == CARTAO_CANCELADO
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CartaoVisual(cartao = cartao, dados = dadosVisiveis[cartao.id])
                Text(
                    "${cartao.rotulo}  ·  ${nomeDoStatus(cartao)}  ·  toque no cartão para virar",
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 12.sp,
                )
                if (!cancelado) {
                    val dadosAbertos = dadosVisiveis.containsKey(cartao.id)
                    BotaoSecundario(
                        texto = if (dadosAbertos) "Ocultar número e CVV" else "Ver número e CVV",
                        onClick = {
                            if (dadosAbertos) {
                                dadosVisiveis = dadosVisiveis - cartao.id
                            } else {
                                verDados = cartao
                            }
                        },
                        enabled = !ocupado,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoSecundario(
                            texto = if (cartao.status == CARTAO_BLOQUEADO) "Desbloquear" else "Bloquear",
                            onClick = {
                                mudarStatus(
                                    cartao,
                                    if (cartao.status == CARTAO_BLOQUEADO) CARTAO_ATIVO else CARTAO_BLOQUEADO,
                                )
                            },
                            enabled = !ocupado,
                            modifier = Modifier.weight(1f),
                        )
                        BotaoSecundario(
                            texto = "Trocar PIN",
                            onClick = { trocandoPin = cartao },
                            enabled = !ocupado,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotaoSecundario(
                            texto = "Histórico",
                            onClick = { verHistorico = cartao },
                            modifier = Modifier.weight(1f),
                        )
                        BotaoSecundario(
                            texto = "Cancelar cartão",
                            onClick = { cancelando = cartao },
                            enabled = !ocupado,
                            modifier = Modifier.weight(1f),
                            corTexto = CorPerigo,
                        )
                    }
                }
            }
        }

        erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }

    if (criando) {
        DialogoCriarCartao(
            onFechar = { criando = false },
            onCriado = {
                criando = false
                recarregar()
            },
        )
    }

    trocandoPin?.let { cartao ->
        DialogoTrocarPin(
            cartao = cartao,
            onFechar = { trocandoPin = null },
            onTrocado = { trocandoPin = null },
        )
    }

    verHistorico?.let { cartao ->
        DialogoHistorico(cartao = cartao, onFechar = { verHistorico = null })
    }

    verDados?.let { cartao ->
        DialogoVerDados(
            cartao = cartao,
            onFechar = { verDados = null },
            onDados = { dados ->
                dadosVisiveis = dadosVisiveis + (cartao.id to dados)
                verDados = null
            },
        )
    }

    cancelando?.let { cartao ->
        DialogoZeca(
            titulo = "Cancelar cartão?",
            subtitulo = "O cartão ${cartao.rotulo} (•••• ${cartao.final4}) será cancelado e não poderá ser reativado.",
            onFechar = { cancelando = null },
            conteudo = {},
            acoes = {
                BotaoSecundario(
                    texto = "Voltar",
                    onClick = { cancelando = null },
                    modifier = Modifier.weight(1f),
                )
                BotaoPrimario(
                    texto = "Cancelar cartão",
                    onClick = {
                        cancelando = null
                        mudarStatus(cartao, CARTAO_CANCELADO)
                    },
                    modifier = Modifier.weight(1f),
                    cor = CorPerigo,
                )
            },
        )
    }
}

@Composable
private fun DialogoCriarCartao(onFechar: () -> Unit, onCriado: () -> Unit) {
    // O mesmo requestId vale para repetições: se a rede falhar depois de criar, não duplica o cartão.
    val requestId = remember { UUID.randomUUID().toString() }
    var rotulo by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    DialogoZeca(
        titulo = "Novo cartão",
        subtitulo = "Cartão virtual de débito. Escolha um nome e um PIN de 4 dígitos para pagar.",
        onFechar = onFechar,
        podeFechar = !enviando,
        conteudo = {
            CampoZeca(
                valor = rotulo,
                onValor = { rotulo = it.take(24) },
                etiqueta = "Nome do cartão (opcional)",
                enabled = !enviando,
            )
            CampoPin(
                valor = pin,
                onValor = { pin = it },
                etiqueta = "PIN DE 4 DÍGITOS",
                enabled = !enviando,
                autoFoco = false,
            )
            erro?.let { TextoErro(it) }
        },
        acoes = {
            BotaoSecundario(
                texto = "Cancelar",
                onClick = onFechar,
                enabled = !enviando,
                modifier = Modifier.weight(1f),
            )
            BotaoPrimario(
                texto = if (enviando) "Criando..." else "Criar cartão",
                onClick = {
                    enviando = true
                    erro = null
                    CardsRepository.criarCartao(requestId, pin, rotulo.trim()) { cartao, e ->
                        enviando = false
                        if (e != null || cartao == null) erro = mensagemDeErro(e) else onCriado()
                    }
                },
                enabled = pin.length == 4 && !enviando,
                modifier = Modifier.weight(1f),
            )
        },
    )
}

@Composable
private fun DialogoTrocarPin(cartao: Cartao, onFechar: () -> Unit, onTrocado: () -> Unit) {
    var pinAtual by remember { mutableStateOf("") }
    var pinNovo by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    DialogoZeca(
        titulo = "Trocar PIN",
        subtitulo = "${cartao.rotulo} · •••• ${cartao.final4}",
        onFechar = onFechar,
        podeFechar = !enviando,
        conteudo = {
            CampoPin(
                valor = pinAtual,
                onValor = { pinAtual = it },
                etiqueta = "PIN ATUAL",
                enabled = !enviando,
                avancarAoCompletar = true,
            )
            CampoPin(
                valor = pinNovo,
                onValor = { pinNovo = it },
                etiqueta = "PIN NOVO",
                enabled = !enviando,
                autoFoco = false,
            )
            erro?.let { TextoErro(it) }
        },
        acoes = {
            BotaoSecundario(
                texto = "Cancelar",
                onClick = onFechar,
                enabled = !enviando,
                modifier = Modifier.weight(1f),
            )
            BotaoPrimario(
                texto = if (enviando) "Salvando..." else "Trocar PIN",
                onClick = {
                    enviando = true
                    erro = null
                    CardsRepository.trocarPin(cartao.id, pinAtual, pinNovo) { e ->
                        enviando = false
                        if (e != null) erro = mensagemDeErro(e) else onTrocado()
                    }
                },
                enabled = pinAtual.length == 4 && pinNovo.length == 4 && !enviando,
                modifier = Modifier.weight(1f),
            )
        },
    )
}

@Composable
private fun DialogoHistorico(cartao: Cartao, onFechar: () -> Unit) {
    var pagamentos by remember { mutableStateOf<List<PagamentoCartao>?>(null) }
    var erro by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(cartao.id) {
        CardsRepository.historicoCartao(cartao.id) { lista, e ->
            if (e != null || lista == null) erro = mensagemDeErro(e) else pagamentos = lista
        }
    }

    DialogoZeca(
        titulo = "Histórico",
        subtitulo = "${cartao.rotulo} · •••• ${cartao.final4}",
        onFechar = onFechar,
        conteudo = {
            val lista = pagamentos
            when {
                erro != null -> TextoErro(erro.orEmpty())
                lista == null -> CircularProgressIndicator(color = Cores.Verde)
                lista.isEmpty() -> Text(
                    "Nenhum pagamento com este cartão ainda.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                else -> lista.forEach { p ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                p.descricao.ifBlank { "Cobrança" },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Text(
                                formatarData(p.pagoEmMs),
                                color = Color.White.copy(alpha = 0.52f),
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            "-" + formatarBase(p.valorBaseCentavos),
                            color = CorPerigo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        acoes = {
            BotaoPrimario(texto = "Fechar", onClick = onFechar, modifier = Modifier.weight(1f))
        },
    )
}

@Composable
private fun DialogoVerDados(cartao: Cartao, onFechar: () -> Unit, onDados: (DadosCartao) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    DialogoZeca(
        titulo = "Ver dados do cartão",
        subtitulo = "Digite o PIN de ${cartao.rotulo} (•••• ${cartao.final4}) para mostrar o número e o CVV.",
        onFechar = onFechar,
        podeFechar = !enviando,
        conteudo = {
            CampoPin(
                valor = pin,
                onValor = { pin = it },
                etiqueta = "PIN",
                enabled = !enviando,
            )
            erro?.let { TextoErro(it) }
        },
        acoes = {
            BotaoSecundario(
                texto = "Cancelar",
                onClick = onFechar,
                enabled = !enviando,
                modifier = Modifier.weight(1f),
            )
            BotaoPrimario(
                texto = if (enviando) "Verificando..." else "Mostrar",
                onClick = {
                    enviando = true
                    erro = null
                    CardsRepository.dadosDoCartao(cartao.id, pin) { dados, e ->
                        enviando = false
                        pin = ""
                        if (e != null || dados == null) erro = mensagemDeErro(e) else onDados(dados)
                    }
                },
                enabled = pin.length == 4 && !enviando,
                modifier = Modifier.weight(1f),
            )
        },
    )
}