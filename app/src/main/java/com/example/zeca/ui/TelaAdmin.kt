package com.example.zeca.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ConfiguracaoMinas
import com.example.zeca.DetalhesAdmin
import com.example.zeca.MovimentoAdmin
import com.example.zeca.UsuarioAdmin
import com.example.zeca.ui.theme.Cores
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private data class ItemInventarioAdmin(val id: String, val nome: String)
private const val LIMITE_AJUSTE_ADMIN_CENTAVOS = 1_000_000L

private val itensInventarioAdmin = listOf(
    ItemInventarioAdmin("frame_aurora", "Moldura Aurora"),
    ItemInventarioAdmin("title_lucky", "Título: Sorte Grande"),
    ItemInventarioAdmin("frame_neon", "Moldura Neon"),
    ItemInventarioAdmin("frame_gold", "Moldura Dourada"),
    ItemInventarioAdmin("title_highroller", "Título: Alto Rolo"),
    ItemInventarioAdmin("frame_emerald", "Moldura Esmeralda"),
    ItemInventarioAdmin("title_champion", "Título: Campeão"),
    ItemInventarioAdmin("frame_royal", "Moldura Real"),
    ItemInventarioAdmin("title_jucineia", "Título: Jucineia"),
    ItemInventarioAdmin("title_donizete", "Título: Donizete"),
    ItemInventarioAdmin("title_erasmo", "Título: Erasmo"),
    ItemInventarioAdmin("title_milena", "Título: Milena"),
    ItemInventarioAdmin("avatar_hair_wave", "Cabelo Ondulado"),
    ItemInventarioAdmin("avatar_hair_curls", "Cachos"),
    ItemInventarioAdmin("avatar_hair_silver", "Cor Prateada"),
    ItemInventarioAdmin("avatar_skin_sun", "Tom Solar"),
    ItemInventarioAdmin("avatar_skin_cocoa", "Tom Cacau"),
    ItemInventarioAdmin("avatar_top_hoodie", "Moletom Neon"),
    ItemInventarioAdmin("avatar_top_jacket", "Jaqueta Aurora"),
    ItemInventarioAdmin("avatar_glasses_round", "Óculos Redondos"),
    ItemInventarioAdmin("avatar_crown_neon", "Coroa Neon"),
)

@Composable
fun TelaAdmin(
    onCarregarUsuarios: (String, (List<UsuarioAdmin>, String, Exception?) -> Unit) -> Unit,
    onCarregarDetalhes: (String, (DetalhesAdmin?, Exception?) -> Unit) -> Unit,
    onCarregarConfiguracao: ((ConfiguracaoMinas?, Exception?) -> Unit) -> Unit,
    onAtualizarConfiguracao: (ConfiguracaoMinas, String, String, (Exception?) -> Unit) -> Unit,
    onAjustarSaldo: (String, Long, String, String, (Long?, Exception?) -> Unit) -> Unit,
    onAtualizarInventario: (String, String, String, String, String, (Exception?) -> Unit) -> Unit,
    onDefinirBloqueio: (String, Boolean, String, String, (Exception?) -> Unit) -> Unit,
    onExcluirUsuario: (String, String, String, String, (Exception?) -> Unit) -> Unit,
) {
    var usuarios by remember { mutableStateOf<List<UsuarioAdmin>>(emptyList()) }
    var cursor by remember { mutableStateOf("") }
    var proximoCursor by remember { mutableStateOf("") }
    var detalhes by remember { mutableStateOf<DetalhesAdmin?>(null) }
    var selecionado by remember { mutableStateOf<UsuarioAdmin?>(null) }
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf("") }
    var configuracao by remember { mutableStateOf(ConfiguracaoMinas(9_800, 1, 24)) }
    var rtpTexto by rememberSaveable { mutableStateOf("98") }
    var minimoMinasTexto by rememberSaveable { mutableStateOf("1") }
    var maximoMinasTexto by rememberSaveable { mutableStateOf("24") }
    var motivoConfiguracao by rememberSaveable { mutableStateOf("") }
    var valorAjuste by rememberSaveable { mutableStateOf("10,00") }
    var motivoAcao by rememberSaveable { mutableStateOf("") }
    var credito by rememberSaveable { mutableStateOf(true) }
    var itemSelecionado by rememberSaveable { mutableStateOf("frame_aurora") }
    var menuInventarioAberto by remember { mutableStateOf(false) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    var uidConfirmacao by rememberSaveable { mutableStateOf("") }
    val valorAjusteCentavos = parseSaldoAdmin(valorAjuste)
    val contexto = LocalContext.current

    LaunchedEffect(Unit) {
        carregando = true
        onCarregarUsuarios("") { encontrados, proximo, error ->
            usuarios = encontrados
            proximoCursor = proximo
            carregando = false
            mensagem = error?.localizedMessage.orEmpty()
        }
        onCarregarConfiguracao { settings, error ->
            if (settings != null) {
                configuracao = settings
                rtpTexto = (settings.rtpBps / 100.0).toString().removeSuffix(".0")
                minimoMinasTexto = settings.minimoMinas.toString()
                maximoMinasTexto = settings.maximoMinas.toString()
            } else if (error != null) {
                mensagem = error.localizedMessage.orEmpty()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, top = 24.dp, end = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Administração", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (carregando) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Cores.Verde,
                trackColor = Color.White.copy(alpha = 0.1f),
            )
            Text("Aguardando resposta do servidor...", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
        }
        if (mensagem.isNotBlank()) {
            Text(
                mensagem,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF34272A))
                    .padding(12.dp),
                color = Color(0xFFFFB7A7),
                fontSize = 12.sp,
            )
        }

        Text("Minas · regras", color = Cores.Verde, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = rtpTexto,
            onValueChange = { rtpTexto = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("RTP %") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = minimoMinasTexto,
                onValueChange = { minimoMinasTexto = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.weight(1f),
                label = { Text("Mín. minas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = maximoMinasTexto,
                onValueChange = { maximoMinasTexto = it.filter(Char::isDigit).take(2) },
                modifier = Modifier.weight(1f),
                label = { Text("Máx. minas") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            value = motivoConfiguracao,
            onValueChange = { motivoConfiguracao = it.take(200) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Motivo da alteração") },
            singleLine = true,
        )
        if (motivoConfiguracao.trim().length < 8) {
            Text("Descreva o motivo da mudança (mínimo 8 caracteres).", color = Cores.Laranja, fontSize = 10.sp)
        }
        Button(
            onClick = {
                val rtp = rtpTexto.replace(',', '.').toDoubleOrNull()
                val minimum = minimoMinasTexto.toIntOrNull()
                val maximum = maximoMinasTexto.toIntOrNull()
                if (rtp == null || minimum == null || maximum == null || motivoConfiguracao.trim().length < 8) {
                    mensagem = "Informe RTP, limites e motivo válido."
                } else {
                    val next = ConfiguracaoMinas((rtp * 100).roundToInt(), minimum, maximum)
                    carregando = true
                    onAtualizarConfiguracao(next, motivoConfiguracao.trim(), UUID.randomUUID().toString()) { error ->
                        carregando = false
                        if (error == null) {
                            configuracao = next
                            motivoConfiguracao = ""
                            mensagem = "Configuração salva. Partidas em andamento mantêm o RTP original."
                        } else mensagem = error.localizedMessage.orEmpty()
                    }
                }
            },
            enabled = !carregando,
            colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde),
        ) { Text("Salvar regras", color = Color(0xFF101417), fontWeight = FontWeight.Bold) }
        Text(
            "RTP atual ${(configuracao.rtpBps / 100.0)}% · ${configuracao.minimoMinas}–${configuracao.maximoMinas} minas",
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Contas", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                cursor = ""
                carregando = true
                onCarregarUsuarios("") { encontrados, proximo, error ->
                    usuarios = encontrados
                    proximoCursor = proximo
                    carregando = false
                    mensagem = error?.localizedMessage.orEmpty()
                }
            }, enabled = !carregando) { Text("Atualizar") }
        }
        detalhes?.let { info ->
            val user = info.usuario
            fun recarregarDetalhesAdmin(uid: String) {
                onCarregarDetalhes(uid) { next, error ->
                    detalhes = next
                    if (error != null) mensagem = error.localizedMessage.orEmpty()
                    next?.usuario?.let { updated -> usuarios = usuarios.map { if (it.uid == updated.uid) updated else it } }
                }
            }
            fun alterarInventario(uid: String, acao: String, itemId: String) {
                val reason = motivoAcao.trim()
                if (reason.length < 8) {
                    mensagem = "Informe um motivo com pelo menos 8 caracteres."
                    return
                }
                carregando = true
                onAtualizarInventario(uid, acao, itemId, reason, UUID.randomUUID().toString()) { error ->
                    carregando = false
                    mensagem = error?.localizedMessage ?: if (acao == "add") "Item adicionado ao inventário." else "Item removido do inventário."
                    if (error == null) recarregarDetalhesAdmin(uid)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF11181B))
                    .border(1.dp, Cores.Verde.copy(alpha = 0.34f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(user.nome, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(user.email, color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                    }
                    IconButton(onClick = {
                        contexto.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("UID do usuário", user.uid))
                        Toast.makeText(contexto, "UID copiado", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copiar UID", tint = Cores.Verde)
                    }
                }
                Text(user.uid, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo ${formatarSaldoAdmin(user.saldoCentavos)}", color = Cores.Verde, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${info.partidas} partidas · ${info.vitorias} vitórias", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
                }

                Text("Extrato · 50 últimas movimentações", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (info.movimentacoes.isEmpty()) {
                    Text("Nenhuma movimentação registrada.", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                } else {
                    info.movimentacoes.forEach { movement -> MovimentoAdminLinha(movement) }
                }

                Text("Inventário · ${info.inventario.size} itens", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = motivoAcao,
                    onValueChange = { motivoAcao = it.take(200) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Motivo para inventário, saldo ou bloqueio") },
                    singleLine = true,
                )
                if (motivoAcao.trim().length < 8) {
                    Text("Informe o motivo para habilitar as ações de saldo e inventário.", color = Cores.Laranja, fontSize = 10.sp)
                }
                if (info.inventario.isEmpty()) {
                    Text("Nenhum item no inventário.", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                } else {
                    info.inventario.forEach { ownedItemId ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                nomeItemInventario(ownedItemId),
                                modifier = Modifier.weight(1f),
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 11.sp,
                            )
                            TextButton(
                                onClick = { alterarInventario(user.uid, "remove", ownedItemId) },
                                enabled = !carregando && motivoAcao.trim().length >= 8,
                            ) { Text("Remover", color = Color(0xFFFF8B91)) }
                        }
                    }
                }
                val disponiveis = itensInventarioAdmin.filterNot { it.id in info.inventario }
                val itemParaAdicionar = disponiveis.firstOrNull { it.id == itemSelecionado } ?: disponiveis.firstOrNull()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box {
                        TextButton(
                            onClick = { menuInventarioAberto = true },
                            enabled = disponiveis.isNotEmpty() && !carregando,
                        ) { Text(itemParaAdicionar?.nome ?: "Inventário completo", color = Cores.Verde) }
                        DropdownMenu(
                            expanded = menuInventarioAberto,
                            onDismissRequest = { menuInventarioAberto = false },
                        ) {
                            disponiveis.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.nome) },
                                    onClick = {
                                        itemSelecionado = item.id
                                        menuInventarioAberto = false
                                    },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { itemParaAdicionar?.let { alterarInventario(user.uid, "add", it.id) } },
                        enabled = itemParaAdicionar != null && !carregando && motivoAcao.trim().length >= 8,
                        colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde),
                    ) { Text("Adicionar item", color = Color(0xFF101417), fontWeight = FontWeight.Bold) }
                }

                OutlinedTextField(
                    value = valorAjuste,
                    onValueChange = { valorAjuste = it.filter { char -> char.isDigit() || char == ',' || char == '.' }.take(12) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Valor · saldo virtual") },
                    prefix = { Text("R$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (valorAjusteCentavos == null) {
                    Text("Digite um valor maior que R$ 0,00 usando vírgula nos centavos.", color = Cores.Laranja, fontSize = 10.sp)
                } else if (valorAjusteCentavos > LIMITE_AJUSTE_ADMIN_CENTAVOS) {
                    Text("O limite por ajuste é R$ 10.000,00.", color = Cores.Laranja, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { credito = true }, colors = ButtonDefaults.buttonColors(containerColor = if (credito) Cores.Verde else Color.White.copy(alpha = 0.14f))) {
                        Text("Adicionar saldo", color = if (credito) Color(0xFF101417) else Color.White)
                    }
                    Button(onClick = { credito = false }, colors = ButtonDefaults.buttonColors(containerColor = if (!credito) Color(0xFFFF7C83) else Color.White.copy(alpha = 0.14f))) {
                        Text("Retirar saldo", color = if (!credito) Color(0xFF101417) else Color.White)
                    }
                }
                Button(
                    onClick = {
                        val amount = valorAjusteCentavos
                        if (amount == null || amount > LIMITE_AJUSTE_ADMIN_CENTAVOS || motivoAcao.trim().length < 8) {
                            mensagem = "Informe um valor de até R$ 10.000,00 e um motivo com pelo menos 8 caracteres."
                        } else {
                            carregando = true
                            onAjustarSaldo(user.uid, if (credito) amount else -amount, motivoAcao.trim(), UUID.randomUUID().toString()) { _, error ->
                                carregando = false
                                mensagem = error?.localizedMessage ?: "Saldo virtual ajustado."
                                if (error == null) recarregarDetalhesAdmin(user.uid)
                            }
                        }
                    },
                    enabled = !carregando && valorAjusteCentavos != null
                        && valorAjusteCentavos <= LIMITE_AJUSTE_ADMIN_CENTAVOS
                        && motivoAcao.trim().length >= 8,
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.Verde),
                ) { Text("Aplicar ajuste", color = Color(0xFF101417), fontWeight = FontWeight.Bold) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            carregando = true
                            onDefinirBloqueio(user.uid, !user.bloqueado, motivoAcao.trim(), UUID.randomUUID().toString()) { error ->
                                carregando = false
                                mensagem = error?.localizedMessage ?: if (user.bloqueado) "Conta desbloqueada." else "Conta bloqueada."
                                if (error == null) recarregarDetalhesAdmin(user.uid)
                            }
                        },
                        enabled = !carregando && motivoAcao.trim().length >= 8,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    ) { Text(if (user.bloqueado) "Desbloquear" else "Bloquear", color = Color.White) }
                    Button(
                        onClick = { confirmarExclusao = true; uidConfirmacao = "" },
                        enabled = !carregando,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB73C52)),
                    ) { Text("Excluir conta", color = Color.White) }
                }
            }
        }
        usuarios.forEach { user ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selecionado?.uid == user.uid) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .clickable {
                        selecionado = user
                        carregando = true
                        onCarregarDetalhes(user.uid) { result, error ->
                            detalhes = result
                            carregando = false
                            mensagem = error?.localizedMessage.orEmpty()
                        }
                    }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("${user.nome}${if (user.bloqueado) " · bloqueada" else ""}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${user.email} · ${formatarSaldoAdmin(user.saldoCentavos)}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                Text(user.uid, color = Color.White.copy(alpha = 0.44f), fontSize = 9.sp)
            }
        }
        if (proximoCursor.isNotBlank()) {
            TextButton(
                onClick = {
                    val nextCursor = proximoCursor
                    carregando = true
                    onCarregarUsuarios(nextCursor) { encontrados, next, error ->
                        usuarios = usuarios + encontrados
                        cursor = nextCursor
                        proximoCursor = next
                        carregando = false
                        mensagem = error?.localizedMessage.orEmpty()
                    }
                },
                enabled = !carregando,
            ) { Text("Carregar mais contas") }
        }

    }

    if (confirmarExclusao && selecionado != null) {
        val user = selecionado!!
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir conta permanentemente?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Apaga perfil, autenticação, saldo virtual, conversas diretas, mensagens e históricos de transferência compartilhados. Esta ação não pode ser desfeita.")
                    Text("Digite o UID da conta para confirmar:", fontSize = 12.sp)
                    OutlinedTextField(value = uidConfirmacao, onValueChange = { uidConfirmacao = it }, singleLine = true)
                    if (uidConfirmacao != user.uid) {
                        Text("O UID deve ser idêntico ao da conta selecionada.", color = Cores.Laranja, fontSize = 10.sp)
                    }
                    OutlinedTextField(value = motivoAcao, onValueChange = { motivoAcao = it.take(200) }, label = { Text("Motivo da exclusão") }, singleLine = true)
                    if (motivoAcao.trim().length < 8) {
                        Text("Informe o motivo da exclusão (mínimo 8 caracteres).", color = Cores.Laranja, fontSize = 10.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarExclusao = false
                        carregando = true
                        onExcluirUsuario(user.uid, uidConfirmacao, motivoAcao.trim(), UUID.randomUUID().toString()) { error ->
                            carregando = false
                            mensagem = error?.localizedMessage ?: "Conta excluída."
                            if (error == null) {
                                detalhes = null
                                selecionado = null
                                usuarios = usuarios.filterNot { it.uid == user.uid }
                            }
                        }
                    },
                    enabled = uidConfirmacao == user.uid && motivoAcao.trim().length >= 8,
                ) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MovimentoAdminLinha(movimento: MovimentoAdmin) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(movimento.descricao, color = Color.White.copy(alpha = 0.82f), fontSize = 10.sp)
            if (movimento.criadoEmMs > 0L) {
                Text(formatarDataAdmin(movimento.criadoEmMs), color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
            }
        }
        Text(
            formatarSaldoAdmin(movimento.deltaCentavos),
            color = if (movimento.deltaCentavos >= 0) Cores.Verde else Color(0xFFFF7C83),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatarDataAdmin(timestampMs: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(timestampMs))

private fun formatarSaldoAdmin(centavos: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(BigDecimal.valueOf(centavos, 2))

private fun nomeItemInventario(itemId: String): String =
    itensInventarioAdmin.firstOrNull { it.id == itemId }?.nome ?: itemId

private fun parseSaldoAdmin(valor: String): Long? = runCatching {
    BigDecimal(valor.trim().replace(',', '.')).movePointRight(2).longValueExact()
}.getOrNull()?.takeIf { it > 0 }