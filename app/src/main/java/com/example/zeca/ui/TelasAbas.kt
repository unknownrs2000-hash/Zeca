package com.example.zeca.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.MensagemChat
import com.example.zeca.ConversaChat
import com.example.zeca.Movimento
import com.example.zeca.JogadorRanking
import com.example.zeca.JogadorDestino
import com.example.zeca.ResultadoTransferencia
import com.example.zeca.ui.theme.Cores
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

fun formatarReais(centavos: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(centavos, 2))

@Composable
fun TelaCarteira(
    saldoCentavos: Long,
    chavePix: String,
    tipoChavePix: String,
    historico: List<Movimento>,
    onSalvarChave: (String, String, (String?) -> Unit) -> Unit,
    onBuscarDestinatario: (String, (JogadorDestino?, String?) -> Unit) -> Unit,
    onTransferir: (String, Long, String, (ResultadoTransferencia?, String?) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var tipoEdicao by rememberSaveable { mutableStateOf(tipoChavePix.ifBlank { "E-mail" }) }
    var chaveEditavel by rememberSaveable { mutableStateOf(chavePix) }
    var mensagem by rememberSaveable { mutableStateOf("") }
    var chaveDestinatario by rememberSaveable { mutableStateOf("") }
    var valorTransferencia by rememberSaveable { mutableStateOf("10,00") }
    var mensagemTransferencia by rememberSaveable { mutableStateOf("") }
    var destinatario by remember { mutableStateOf<JogadorDestino?>(null) }
    var transferenciaOcupada by remember { mutableStateOf(false) }
    val chaveValida = when (tipoEdicao) {
        "E-mail" -> Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(chaveEditavel.trim())
        else -> Regex("^[A-Fa-f0-9]{32}$").matches(chaveEditavel.trim())
    }
    TelaBase("Carteira", "Saldo e movimentações.") {
        GlassCard {
            Text("SALDO DISPONÍVEL", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(formatarReais(saldoCentavos), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }

        GlassCard {
            Text("Minha chave Pix", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (chavePix.isNotBlank()) {
                Text("${tipoChavePix}: $chavePix", color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp)
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Chave Pix do Zeca", chavePix))
                    Toast.makeText(context, "Chave copiada", Toast.LENGTH_SHORT).show()
                }) { Text("Copiar chave") }
            } else {
                Text("Cadastre um e-mail ou gere uma chave aleatória.", color = Color.White.copy(alpha = 0.68f), fontSize = 13.sp)
            }

            Opcoes(listOf("E-mail", "Aleatória"), tipoEdicao) { novoTipo ->
                tipoEdicao = novoTipo
                chaveEditavel = if (novoTipo == tipoChavePix) chavePix else ""
                mensagem = ""
            }
            if (tipoEdicao == "E-mail") {
                OutlinedTextField(
                    value = chaveEditavel,
                    onValueChange = { chaveEditavel = it; mensagem = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail da chave") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            } else {
                OutlinedTextField(
                    value = chaveEditavel,
                    onValueChange = { chaveEditavel = it; mensagem = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chave aleatória") },
                    readOnly = true,
                    singleLine = true,
                )
                TextButton(onClick = {
                    chaveEditavel = UUID.randomUUID().toString().replace("-", "")
                    mensagem = ""
                }) { Text("Gerar chave aleatória") }
            }
            Button(
                onClick = {
                    onSalvarChave(tipoEdicao, chaveEditavel.trim()) { erro ->
                        mensagem = erro ?: "Chave salva."
                    }
                },
                enabled = chaveValida,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar chave") }
            if (mensagem.isNotBlank()) Text(mensagem, color = Cores.Verde, fontSize = 13.sp)
        }

        GlassCard {
            Text("Enviar para um usuário", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = chaveDestinatario,
                onValueChange = { chaveDestinatario = it; destinatario = null; mensagemTransferencia = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("E-mail ou chave aleatória") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            Button(
                onClick = {
                    transferenciaOcupada = true
                    mensagemTransferencia = ""
                    onBuscarDestinatario(chaveDestinatario.trim()) { encontrado, erro ->
                        transferenciaOcupada = false
                        destinatario = encontrado
                        mensagemTransferencia = erro ?: if (encontrado != null) "Confira o destinatário antes de continuar." else "Conta não encontrada."
                    }
                },
                enabled = chaveDestinatario.isNotBlank() && !transferenciaOcupada,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (transferenciaOcupada) "Buscando..." else "Buscar usuário") }
            destinatario?.let { jogador ->
                Text("${jogador.apelido} · Nível ${jogador.nivel}", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = valorTransferencia,
                    onValueChange = { valorTransferencia = it; mensagemTransferencia = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor da transferência") },
                    prefix = { Text("R$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                val valorEnvio = parseValorCentavos(valorTransferencia)
                Button(
                    onClick = {
                        val valor = valorEnvio ?: return@Button
                        transferenciaOcupada = true
                        onTransferir(chaveDestinatario.trim(), valor, UUID.randomUUID().toString()) { resultado, erro ->
                            transferenciaOcupada = false
                            destinatario = null
                            mensagemTransferencia = erro ?: "Enviado para ${resultado?.nomeDestino}: ${formatarReais(valor)}."
                            if (erro == null) chaveDestinatario = ""
                        }
                    },
                    enabled = valorEnvio != null && valorEnvio > 0 && !transferenciaOcupada,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (transferenciaOcupada) "Enviando..." else "Confirmar envio") }
            }
            if (mensagemTransferencia.isNotBlank()) {
                Text(
                    mensagemTransferencia,
                    color = if (mensagemTransferencia.contains("encontrada")) Cores.Laranja else Cores.Verde,
                    fontSize = 13.sp,
                )
            }
            Text("A transferência é entre saldos do Zeca; não movimenta valores bancários.", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        }

        GlassCard {
            Text("Histórico", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (historico.isEmpty()) {
                Text("Nenhuma movimentação por enquanto.", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
            } else {
                historico.take(8).forEach { movimento ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(movimento.titulo, color = Color.White, fontSize = 14.sp)
                            Text(movimento.horario, color = Color.White.copy(alpha = 0.52f), fontSize = 11.sp)
                        }
                        Text(
                            (if (movimento.variacaoCentavos >= 0) "+" else "-") + formatarReais(kotlin.math.abs(movimento.variacaoCentavos)),
                            color = if (movimento.variacaoCentavos >= 0) Cores.Verde else Color(0xFFFF8790),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TelaPerfil(
    apelido: String,
    email: String,
    saldoCentavos: Long,
    nivel: Int,
    partidas: Int,
    vitorias: Int,
    avatarUrl: String,
    onSalvarApelido: (String, (String?) -> Unit) -> Unit,
    onAbrirLoja: () -> Unit,
    onSair: () -> Unit,
) {
    var apelidoEditavel by rememberSaveable { mutableStateOf(apelido) }
    var mensagemPerfil by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(apelido) { apelidoEditavel = apelido }

    TelaBase("Perfil", "Seu espaço no Zeca.") {
        GlassCard {
            Box(
                modifier = Modifier.size(72.dp).background(Cores.Verde.copy(alpha = 0.2f), CircleShape).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(apelido.take(1).uppercase(), color = Cores.Verde, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
            OutlinedTextField(
                value = apelidoEditavel,
                onValueChange = { apelidoEditavel = it.take(24); mensagemPerfil = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Apelido") },
                singleLine = true,
            )
            Text(email, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
            Text("Nível $nivel", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Button(
                onClick = {
                    onSalvarApelido(apelidoEditavel.trim()) { erro ->
                        mensagemPerfil = erro ?: "Apelido atualizado."
                    }
                },
                enabled = apelidoEditavel.trim().length >= 2 && apelidoEditavel.trim() != apelido,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar apelido") }
            if (mensagemPerfil.isNotBlank()) Text(mensagemPerfil, color = if (mensagemPerfil.contains("atualizado")) Cores.Verde else Cores.Laranja, fontSize = 12.sp)
        }
        GlassCard {
            Text("Estatísticas", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Estatistica("Partidas", partidas.toString())
                Estatistica("Vitórias", vitorias.toString())
                Estatistica("Saldo", formatarReais(saldoCentavos))
            }
        }
        Button(onClick = onAbrirLoja, modifier = Modifier.fillMaxWidth()) { Text("Abrir Loja") }
        TextButton(onClick = onSair, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta") }
    }
}

@Composable
fun TelaLoja(
    saldoCentavos: Long,
    itensComprados: List<String>,
    onVoltar: () -> Unit,
    onComprar: (String, (String?) -> Unit) -> Unit,
) {
    val produtos = listOf(
        Produto("frame_aurora", "Moldura Aurora", "Um brilho suave em volta do seu avatar.", 1_299L, Cores.Turquesa, "✦"),
        Produto("title_lucky", "Sorte Grande", "Um título para mostrar no seu perfil.", 799L, Cores.Laranja, "★"),
        Produto("frame_neon", "Moldura Neon", "Uma borda vibrante para o seu avatar.", 1_999L, Color(0xFFFF8290), "◈"),
        Produto("frame_gold", "Moldura Dourada", "Um contorno dourado de alto nível.", 2_499L, Color(0xFFFFD166), "❖"),
        Produto("title_highroller", "Alto Rolo", "Um título para quem aposta grande.", 1_499L, Color(0xFFB388FF), "♦"),
        Produto("frame_emerald", "Moldura Esmeralda", "Um verde profundo em volta do avatar.", 1_699L, Color(0xFF3DDC97), "✧"),
        Produto("title_champion", "Campeão", "O título de quem domina o ranking.", 2_999L, Color(0xFF64B5F6), "✪"),
        Produto("frame_royal", "Moldura Real", "Uma moldura digna de realeza.", 3_999L, Color(0xFFE040FB), "♛"),
    )
    var mensagem by rememberSaveable { mutableStateOf("") }
    var mensagemErro by rememberSaveable { mutableStateOf(false) }
    var itemEmCompra by rememberSaveable { mutableStateOf("") }

    TelaBase("Loja", "Escolha um detalhe para o seu perfil.") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Cores.Verde.copy(alpha = 0.2f), Color.White.copy(alpha = 0.06f))), RoundedCornerShape(18.dp))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("SEU SALDO", color = Color.White.copy(alpha = 0.62f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(formatarReais(saldoCentavos), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Text("${itensComprados.size} itens", color = Cores.Verde, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        produtos.forEach { produto ->
            val comprado = produto.id in itensComprados
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(produto.cor.copy(alpha = 0.2f), Color.White.copy(alpha = 0.08f), Cores.Cartao.copy(alpha = 0.75f))),
                        RoundedCornerShape(20.dp),
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
                    .animateContentSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .background(produto.cor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .border(1.dp, produto.cor.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(produto.simbolo, color = produto.cor, fontSize = 30.sp, fontWeight = FontWeight.Black)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(produto.nome, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(produto.descricao, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatarReais(produto.precoCentavos),
                        modifier = Modifier.weight(1f),
                        color = produto.cor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                    Button(
                        onClick = {
                            itemEmCompra = produto.id
                            mensagem = ""
                            onComprar(produto.id) { erro ->
                                itemEmCompra = ""
                                mensagemErro = erro != null
                                mensagem = erro ?: "Item adicionado à coleção."
                            }
                        },
                        enabled = !comprado && saldoCentavos >= produto.precoCentavos && itemEmCompra.isBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (comprado) Color.White.copy(alpha = 0.14f) else Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.14f),
                        ),
                    ) {
                        val buttonLabel = when {
                            comprado -> "Na coleção"
                            itemEmCompra == produto.id -> "Comprando..."
                            saldoCentavos < produto.precoCentavos -> "Saldo insuficiente"
                            else -> "Desbloquear"
                        }
                        Crossfade(targetState = buttonLabel, label = "store-purchase-${produto.id}") { label ->
                            Text(
                                label,
                                color = if (comprado || label == "Saldo insuficiente" || label == "Comprando...") Color.White else Color(0xFF111418),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        if (mensagem.isNotBlank()) Text(mensagem, color = if (mensagemErro) Cores.Laranja else Cores.Verde, fontSize = 13.sp)
        TextButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) { Text("Voltar ao perfil") }
    }
}

@Composable
private fun TelaBase(titulo: String, subtitulo: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF151A1D), Cores.Fundo, Color(0xFF090D10)))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(titulo, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Black)
            Text(subtitulo, color = Color.White.copy(alpha = 0.62f), fontSize = 13.sp)
            content()
        }
    }
}

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.055f))),
                RoundedCornerShape(22.dp),
            )
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.1f))), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun Opcoes(opcoes: List<String>, selecionada: String, onSelecionar: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        opcoes.forEach { opcao ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (opcao == selecionada) Color.White.copy(alpha = 0.9f) else Color.Transparent)
                    .clickable { onSelecionar(opcao) }
                    .padding(horizontal = 8.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(opcao, color = if (opcao == selecionada) Color(0xFF111418) else Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Estatistica(rotulo: String, valor: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(rotulo, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
    }
}

private data class Produto(val id: String, val nome: String, val descricao: String, val precoCentavos: Long, val cor: Color, val simbolo: String)

internal fun parseValorCentavos(texto: String): Long? {
    val entrada = texto.trim().replace("R$", "").replace(" ", "")
    val normalizada = if (entrada.contains(',')) entrada.replace(".", "").replace(',', '.') else entrada
    val decimal = normalizada.toBigDecimalOrNull() ?: return null
    if (decimal <= BigDecimal.ZERO) return null
    return runCatching {
        decimal.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}

