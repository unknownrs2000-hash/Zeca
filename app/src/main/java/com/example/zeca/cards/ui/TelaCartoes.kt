package com.example.zeca.cards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.zeca.cards.data.CARTAO_ATIVO
import com.example.zeca.cards.data.CARTAO_BLOQUEADO
import com.example.zeca.cards.data.CARTAO_CANCELADO
import com.example.zeca.cards.data.Cartao
import com.example.zeca.cards.data.CardsRepository
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
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onVoltar) { Text("Voltar") }
        Text("Meus cartões", style = MaterialTheme.typography.headlineSmall)
        Text("Cartão virtual de débito. O pagamento usa o saldo da sua conta e pede o PIN.")

        Button(
            onClick = { criando = true },
            enabled = !carregando && !ocupado,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Criar cartão") }

        if (carregando) CircularProgressIndicator()

        if (!carregando && cartoes.isEmpty() && erro == null) {
            Text("Você ainda não tem cartões.")
        }

        cartoes.forEach { cartao ->
            val cancelado = cartao.status == CARTAO_CANCELADO
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(cartao.rotulo, style = MaterialTheme.typography.titleMedium)
                Text("•••• ${cartao.final4}  ·  ${nomeDoStatus(cartao)}")
                if (!cancelado) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                mudarStatus(
                                    cartao,
                                    if (cartao.status == CARTAO_BLOQUEADO) CARTAO_ATIVO else CARTAO_BLOQUEADO,
                                )
                            },
                            enabled = !ocupado,
                            modifier = Modifier.weight(1f),
                        ) { Text(if (cartao.status == CARTAO_BLOQUEADO) "Desbloquear" else "Bloquear") }
                        OutlinedButton(
                            onClick = { trocandoPin = cartao },
                            enabled = !ocupado,
                            modifier = Modifier.weight(1f),
                        ) { Text("Trocar PIN") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { verHistorico = cartao },
                            modifier = Modifier.weight(1f),
                        ) { Text("Histórico") }
                        TextButton(
                            onClick = { cancelando = cartao },
                            enabled = !ocupado,
                            modifier = Modifier.weight(1f),
                        ) { Text("Cancelar cartão", color = MaterialTheme.colorScheme.error) }
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

    cancelando?.let { cartao ->
        AlertDialog(
            onDismissRequest = { cancelando = null },
            title = { Text("Cancelar cartão?") },
            text = { Text("O cartão ${cartao.rotulo} (•••• ${cartao.final4}) será cancelado e não poderá ser reativado.") },
            confirmButton = {
                TextButton(onClick = {
                    cancelando = null
                    mudarStatus(cartao, CARTAO_CANCELADO)
                }) { Text("Cancelar cartão") }
            },
            dismissButton = {
                TextButton(onClick = { cancelando = null }) { Text("Voltar") }
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

    AlertDialog(
        onDismissRequest = { if (!enviando) onFechar() },
        title = { Text("Novo cartão") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rotulo,
                    onValueChange = { rotulo = it.take(24) },
                    label = { Text("Nome do cartão (opcional)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = soDigitosPin(it) },
                    label = { Text("PIN de 4 dígitos") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length == 4 && !enviando,
                onClick = {
                    enviando = true
                    erro = null
                    CardsRepository.criarCartao(requestId, pin, rotulo.trim()) { cartao, e ->
                        enviando = false
                        if (e != null || cartao == null) erro = mensagemDeErro(e) else onCriado()
                    }
                },
            ) { Text(if (enviando) "Criando..." else "Criar") }
        },
        dismissButton = {
            TextButton(onClick = onFechar, enabled = !enviando) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DialogoTrocarPin(cartao: Cartao, onFechar: () -> Unit, onTrocado: () -> Unit) {
    var pinAtual by remember { mutableStateOf("") }
    var pinNovo by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!enviando) onFechar() },
        title = { Text("Trocar PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${cartao.rotulo} (•••• ${cartao.final4})")
                OutlinedTextField(
                    value = pinAtual,
                    onValueChange = { pinAtual = soDigitosPin(it) },
                    label = { Text("PIN atual") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    value = pinNovo,
                    onValueChange = { pinNovo = soDigitosPin(it) },
                    label = { Text("PIN novo") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pinAtual.length == 4 && pinNovo.length == 4 && !enviando,
                onClick = {
                    enviando = true
                    erro = null
                    CardsRepository.trocarPin(cartao.id, pinAtual, pinNovo) { e ->
                        enviando = false
                        if (e != null) erro = mensagemDeErro(e) else onTrocado()
                    }
                },
            ) { Text(if (enviando) "Salvando..." else "Trocar") }
        },
        dismissButton = {
            TextButton(onClick = onFechar, enabled = !enviando) { Text("Cancelar") }
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

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Histórico · •••• ${cartao.final4}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val lista = pagamentos
                when {
                    erro != null -> Text(erro.orEmpty(), color = MaterialTheme.colorScheme.error)
                    lista == null -> CircularProgressIndicator()
                    lista.isEmpty() -> Text("Nenhum pagamento com este cartão ainda.")
                    else -> lista.forEach { p ->
                        Column {
                            Text(formatarBase(p.valorBaseCentavos), style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(
                                    p.descricao.ifBlank { "Cobrança" },
                                    formatarData(p.pagoEmMs),
                                ).joinToString(" · "),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
    )
}