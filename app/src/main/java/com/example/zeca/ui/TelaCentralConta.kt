package com.example.zeca.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.zeca.AlteracaoConta
import com.example.zeca.AvisoApp
import com.example.zeca.MissaoWhatsApp
import com.example.zeca.PainelWhatsApp
import com.example.zeca.PainelConta
import com.example.zeca.PreferenciasConta
import com.example.zeca.RecomendacaoJogo
import com.example.zeca.ui.EventoSemanal
import com.example.zeca.ui.theme.Cores
import java.text.SimpleDateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SecaoConta(titulo: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Cores.Cartao),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(titulo, color = Cores.Turquesa, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            content()
        }
    }
}

@Composable
private fun PreferenceSwitchConta(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun TelaCentralConta(
    painel: PainelConta?,
    eventoSemanal: EventoSemanal?,
    carregando: Boolean,
    erro: String,
    bloqueioDispositivoAtivo: Boolean,
    isAdmin: Boolean,
    onRecarregar: () -> Unit,
    onSalvarPreferencias: (PreferenciasConta, (Exception?) -> Unit) -> Unit,
    onCriarCodigoVinculoWhatsApp: ((String?, Long, Exception?) -> Unit) -> Unit,
    onDesvincularWhatsApp: ((Exception?) -> Unit) -> Unit,
    onCarregarPainelWhatsApp: ((PainelWhatsApp?, Exception?) -> Unit) -> Unit,
    onResgatarMissaoWhatsApp: (String, (Long?, Exception?) -> Unit) -> Unit,
    onAbrirWhatsApp: (String) -> Unit,
    onCompartilharFigurinha: (Uri) -> Unit,
    onGerenciarAmigo: (String, String, String, (Exception?) -> Unit) -> Unit,
    onDefinirBloqueio: (Boolean) -> Unit,
    onPublicarAviso: (String, String, String, Long, (Exception?) -> Unit) -> Unit,
    onEnviarSuporte: (String, String, String, (Exception?) -> Unit) -> Unit,
    onVoltar: () -> Unit,
) {
    val preferences = painel?.preferences ?: PreferenciasConta()
    var usernameAmigo by rememberSaveable { mutableStateOf("") }
    var avisoTitulo by rememberSaveable { mutableStateOf("") }
    var avisoDetalhe by rememberSaveable { mutableStateOf("") }
    var avisoMensagem by rememberSaveable { mutableStateOf("") }
    var statusMensagem by rememberSaveable { mutableStateOf("") }
    var suporteTitulo by rememberSaveable { mutableStateOf("") }
    var suporteDetalhes by rememberSaveable { mutableStateOf("") }
    var suporteMensagem by rememberSaveable { mutableStateOf("") }
    var codigoVinculoWhatsApp by rememberSaveable { mutableStateOf("") }
    var codigoVinculoExpiraEm by rememberSaveable { mutableStateOf(0L) }
    var criandoCodigoVinculo by remember { mutableStateOf(false) }
    var confirmarDesvinculoWhatsApp by rememberSaveable { mutableStateOf(false) }
    var painelWhatsApp by remember { mutableStateOf<PainelWhatsApp?>(null) }
    var carregandoPainelWhatsApp by remember { mutableStateOf(false) }
    var erroPainelWhatsApp by rememberSaveable { mutableStateOf("") }
    val seletorFigurinha = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(onCompartilharFigurinha)
    }

    fun save(updated: PreferenciasConta) {
        statusMensagem = "Salvando..."
        onSalvarPreferencias(updated) { error ->
            statusMensagem = error?.localizedMessage ?: "Configurações salvas e sincronizadas."
            if (error == null) {
                com.example.zeca.ui.theme.Cores.aplicarTema(updated.theme)
                com.example.zeca.ui.theme.Cores.aplicarAcessibilidade(
                    updated.accessibilityFontScale.toFloat(),
                    updated.highContrast,
                    updated.reduceMotion,
                )
                onRecarregar()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Central da conta", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
                Text("Privacidade, amigos, acessibilidade e segurança", color = Color.White.copy(alpha = 0.68f), fontSize = 13.sp)
            }
            TextButton(onClick = onVoltar) { Text("Voltar", color = Cores.Turquesa) }
        }
        if (carregando) Text("Carregando suas configurações...", color = Color.White.copy(alpha = 0.7f))
        if (erro.isNotBlank()) Text(erro, color = Color(0xFFFF8B91))
        if (statusMensagem.isNotBlank()) Text(statusMensagem, color = Cores.Turquesa, fontSize = 12.sp)

        SecaoConta("Privacidade e sincronização com o bot") {
            Text(
                "Os dados ficam privados nesta conta e só são consultados quando você ativa cada opção. Desative uma opção para interromper novas consultas.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
            )
            PreferenceSwitchConta("Perfil, XP e progresso por grupo", preferences.shareBotProfile) {
                save(preferences.copy(shareBotProfile = it))
                if (!it) painelWhatsApp = painelWhatsApp?.copy(profile = null)
            }
            PreferenceSwitchConta("Pet e inventário", preferences.shareBotPetInventory) {
                save(preferences.copy(shareBotPetInventory = it))
                if (!it) painelWhatsApp = painelWhatsApp?.copy(pet = null, inventory = emptyMap())
            }
            PreferenceSwitchConta("Missões do bot e eventos cruzados", preferences.shareBotMissions) {
                save(preferences.copy(shareBotMissions = it))
                if (!it) painelWhatsApp = painelWhatsApp?.copy(missions = emptyList())
            }
            PreferenceSwitchConta("Carteira e extrato compartilhados", preferences.shareBotEconomy) {
                save(preferences.copy(shareBotEconomy = it))
                if (!it) painelWhatsApp = painelWhatsApp?.copy(economy = null)
            }
        }

        SecaoConta("Privacidade e perfil") {
            Text("Visibilidade", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("public" to "Público", "private" to "Somente amigos").forEach { (value, label) ->
                    if (preferences.profileVisibility == value) {
                        Button(onClick = { }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { save(preferences.copy(profileVisibility = value)) }) { Text(label) }
                    }
                }
            }
            Text("Perfis privados só podem ser abertos por amigos aceitos. Saldo, inventário e chave Pix não são publicados.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            var statusEditavel by remember(preferences.customStatus) { mutableStateOf(preferences.customStatus) }
            OutlinedTextField(
                value = statusEditavel,
                onValueChange = { statusEditavel = it.take(40) },
                label = { Text("Editar status") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedButton(
                onClick = { save(preferences.copy(customStatus = statusEditavel.trim())) },
                enabled = statusEditavel.trim() != preferences.customStatus,
            ) { Text("Salvar status") }
        }

        SecaoConta("Lista de amigos") {
            OutlinedTextField(
                value = usernameAmigo,
                onValueChange = { usernameAmigo = it.filter { char -> char.isLetterOrDigit() || char == '_' }.take(20) },
                label = { Text("Nome de usuário") },
                prefix = { Text("@") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    onGerenciarAmigo("request", usernameAmigo, "") { error ->
                        statusMensagem = error?.localizedMessage ?: "Convite de amizade enviado."
                        if (error == null) usernameAmigo = ""
                        onRecarregar()
                    }
                },
                enabled = usernameAmigo.length >= 3 && !carregando,
            ) { Text("Enviar convite") }
            painel?.friendRequests.orEmpty().forEach { friend ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${friend.nome} (@${friend.username})", color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        onGerenciarAmigo("accept", "", friend.uid) { error ->
                            statusMensagem = error?.localizedMessage ?: "Amizade adicionada."
                            onRecarregar()
                        }
                    }) { Text("Aceitar") }
                    TextButton(onClick = {
                        onGerenciarAmigo("reject", "", friend.uid) { error ->
                            statusMensagem = error?.localizedMessage ?: "Convite recusado."
                            onRecarregar()
                        }
                    }) { Text("Recusar") }
                }
            }
            painel?.friends.orEmpty().forEach { friend ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${friend.nome} (@${friend.username})", color = Color.White)
                        Text(
                            when {
                                friend.online -> "● Online${friend.status.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}"
                                friend.status.isNotBlank() -> friend.status
                                else -> "○ Offline"
                            },
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 11.sp,
                        )
                    }
                    TextButton(onClick = {
                        onGerenciarAmigo("remove", "", friend.uid) { error ->
                            statusMensagem = error?.localizedMessage ?: "Amigo removido."
                            onRecarregar()
                        }
                    }) { Text("Remover") }
                }
            }
            if (painel?.friends.isNullOrEmpty() && painel?.friendRequests.isNullOrEmpty()) {
                Text("Seus amigos e convites recebidos aparecerão aqui.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }

        SecaoConta("Conectar ao WhatsApp") {
            Text(
                "Vincule sua conta para consultar dados escolhidos por voce, sincronizar desafios e compartilhar a mesma carteira e moeda do app com o bot.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
            )
                    if (painel?.whatsappLink?.linked == true) {
            Text("✅ Conta do WhatsApp vinculada", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
            val numeroWhatsApp = painel?.whatsappLink?.phoneNumber?.takeIf { it.isNotBlank() }
            if (numeroWhatsApp != null) {
                Text("Número vinculado: $numeroWhatsApp", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
            }
            OutlinedButton(onClick = { onAbrirWhatsApp("") }) { Text("Escolher conversa do bot") }
            OutlinedButton(
                onClick = {
                    onRecarregar()
                    carregandoPainelWhatsApp = true
                    erroPainelWhatsApp = ""
                    onCarregarPainelWhatsApp { result, error ->
                        carregandoPainelWhatsApp = false
                        painelWhatsApp = result
                        erroPainelWhatsApp = error?.localizedMessage.orEmpty()
                    }
                },
                enabled = !carregandoPainelWhatsApp,
            ) { Text(if (carregandoPainelWhatsApp) "Sincronizando..." else "Sincronizar dados permitidos") }
            OutlinedButton(
                onClick = { confirmarDesvinculoWhatsApp = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFF8B91), contentColor = Color(0xFFFF8B91)),
            ) { Text("Desvincular WhatsApp") }
            if (erroPainelWhatsApp.isNotBlank()) {
                Text(erroPainelWhatsApp, color = Color(0xFFFF8B91), fontSize = 12.sp)
            }
            if (preferences.shareBotProfile || preferences.shareBotPetInventory || preferences.shareBotMissions || preferences.shareBotEconomy) {
                painelWhatsApp?.let { botPanel ->
                    val formatGold = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")) }
                    Text(
                        "Pontos de integração: ${formatGold.format(botPanel.integrationPoints)}",
                        color = Cores.Turquesa,
                        fontWeight = FontWeight.Bold,
                    )
                    botPanel.profile?.let { profile ->
                        Text("Perfil do bot · ${profile.displayName}", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Nível ${profile.level} · ${formatGold.format(profile.xp)} XP · ${formatGold.format(profile.quizPoints)} pontos de quiz · ${formatGold.format(profile.messages)} mensagens",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 12.sp,
                        )
                        profile.groups.forEach { group ->
                            Text(
                                "${group.name} · nível ${group.level} · ${formatGold.format(group.xp)} XP",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (preferences.shareBotPetInventory) {
                        botPanel.pet?.let { pet ->
                            val petNome = pet.name.ifBlank { pet.type.ifBlank { "Sem nome" } }
                            val petRaridade = pet.rarity.ifBlank { "raridade nao informada" }
                            Text("Pet · $petNome", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "Nivel ${pet.level} · $petRaridade · energia ${pet.energy}% · fome ${pet.fullness}% · felicidade ${pet.happiness}%",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        } ?: Text("Nenhum pet encontrado na conta do bot.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                        if (botPanel.inventory.isNotEmpty()) {
                            Text("Inventário do bot", color = Color.White, fontWeight = FontWeight.Bold)
                            botPanel.inventory.entries.take(30).forEach { (item, quantity) ->
                                Text("$item × $quantity", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                            }
                        } else {
                            Text("O inventário do bot está vazio.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                        }
                    }
                    if (preferences.shareBotMissions) {
                        Text("Missões cruzadas do bot", color = Color.White, fontWeight = FontWeight.Bold)
                        botPanel.missions.forEach { mission ->
                            MissaoWhatsAppLinha(
                                mission = mission,
                                onResgatar = {
                                    onResgatarMissaoWhatsApp(mission.id) { points, error ->
                                        statusMensagem = error?.localizedMessage ?: "Missão resgatada: +${points ?: 10L} pontos de integração."
                                        if (error == null) {
                                            carregandoPainelWhatsApp = true
                                            onCarregarPainelWhatsApp { updated, loadError ->
                                                carregandoPainelWhatsApp = false
                                                if (loadError == null) painelWhatsApp = updated
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        if (botPanel.missions.isEmpty()) {
                            Text("Nenhuma missão aberta no bot agora.", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                        }
                    }
                    if (preferences.shareBotEconomy) {
                        botPanel.economy?.let { economy ->
                            Text("Economia do bot", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                "Saldo unificado: ${formatarReais(economy.totalGold)}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        } ?: Text("Nenhuma economia compartilhada do bot.", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    criandoCodigoVinculo = true
                    onCriarCodigoVinculoWhatsApp { code, expiresAtMs, error ->
                        criandoCodigoVinculo = false
                        if (error != null) {
                            statusMensagem = error.localizedMessage ?: "Não foi possível gerar o código."
                        } else {
                            codigoVinculoWhatsApp = code.orEmpty()
                            codigoVinculoExpiraEm = expiresAtMs
                            statusMensagem = "Código gerado. Envie-o ao bot em até 10 minutos."
                        }
                    }
                },
                enabled = !criandoCodigoVinculo && !carregando,
            ) {
                Text(if (criandoCodigoVinculo) "Gerando código..." else "Gerar código de vínculo")
            }
            if (codigoVinculoWhatsApp.isNotBlank()) {
                Text(
                    codigoVinculoWhatsApp.chunked(4).joinToString("-"),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                )
                val expiryTime = remember(codigoVinculoExpiraEm) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(codigoVinculoExpiraEm))
                }
                Text("Expira às $expiryTime. Use apenas em uma conversa privada com o bot.", color = Color.White.copy(alpha = 0.62f), fontSize = 11.sp)
                Button(
                    onClick = { onAbrirWhatsApp("!vincular $codigoVinculoWhatsApp") },
                ) { Text("Escolher bot e enviar código") }
            }
            OutlinedButton(onClick = { seletorFigurinha.launch("image/*") }) {
                Text("Compartilhar imagem como figurinha no WhatsApp")
            }
        }
        }

        if (confirmarDesvinculoWhatsApp) {
            AlertDialog(
                onDismissRequest = { confirmarDesvinculoWhatsApp = false },
                title = { Text("Desvincular WhatsApp?") },
                text = { Text("O bot deixará de estar associado a esta conta do app e o número vinculado será removido do perfil desta conta. Seus saldos e progressos não serão alterados.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmarDesvinculoWhatsApp = false
                            onDesvincularWhatsApp { error ->
                                statusMensagem = error?.localizedMessage ?: "Conta do WhatsApp desvinculada."
                                if (error == null) {
                                    codigoVinculoWhatsApp = ""
                                    codigoVinculoExpiraEm = 0L
                                    painelWhatsApp = null
                                    onRecarregar()
                                }
                            }
                        },
                    ) { Text("Desvincular", color = Color(0xFFFF8B91)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmarDesvinculoWhatsApp = false }) { Text("Cancelar") }
                },
            )
        }
        SecaoConta("Preferências sincronizadas") {
            PreferenceSwitchConta("Confirmar ações importantes", preferences.confirmImportant) {
                save(preferences.copy(confirmImportant = it))
            }
            PreferenceSwitchConta("Recomendações personalizadas", preferences.personalizedRecommendations) {
                save(preferences.copy(personalizedRecommendations = it))
            }
            PreferenceSwitchConta("Sincronizar configurações entre dispositivos", preferences.syncSettings) {
                save(preferences.copy(syncSettings = it))
            }
            Text("Tema visual", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("dark" to "Escuro", "amoled" to "AMOLED", "blue" to "Azul").forEach { (value, label) ->
                    if (preferences.theme == value) Button(onClick = {}) { Text(label) }
                    else OutlinedButton(onClick = { save(preferences.copy(theme = value)) }) { Text(label) }
                }
            }
            Text("Idioma e localização · formato de data", color = Color.White, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pt-BR" to "Português (Brasil)", "en-US" to "English", "es" to "Español").forEach { (value, label) ->
                    if (preferences.locale == value) Button(onClick = {}) { Text(label) }
                    else OutlinedButton(onClick = { save(preferences.copy(locale = value)) }) { Text(label) }
                }
            }
            Text("A preferência é sincronizada e aplicada à formatação de datas; a tradução das telas será ampliada gradualmente.", color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        }

        SecaoConta("Acessibilidade") {
            var fontScaleEditavel by remember(preferences.accessibilityFontScale) {
                mutableStateOf(preferences.accessibilityFontScale.toFloat())
            }
            Text("Tamanho do texto: ${(fontScaleEditavel * 100).toInt()}%", color = Color.White)
            Slider(
                value = fontScaleEditavel,
                onValueChange = { fontScaleEditavel = it },
                valueRange = 1f..1.5f,
                steps = 9,
            )
            OutlinedButton(
                onClick = { save(preferences.copy(accessibilityFontScale = fontScaleEditavel.toDouble())) },
                enabled = fontScaleEditavel.toDouble() != preferences.accessibilityFontScale,
            ) { Text("Aplicar tamanho do texto") }
            PreferenceSwitchConta("Alto contraste", preferences.highContrast) { save(preferences.copy(highContrast = it)) }
            PreferenceSwitchConta("Reduzir animações", preferences.reduceMotion) { save(preferences.copy(reduceMotion = it)) }
            Text("Ajude a melhorar a acessibilidade: envie comentários sobre contraste, navegação, leitor de tela ou tamanho do texto.", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            OutlinedTextField(
                value = suporteDetalhes,
                onValueChange = { suporteDetalhes = it.take(2_000) },
                label = { Text("Comentário de acessibilidade") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedButton(
                onClick = {
                    onEnviarSuporte("Feedback de acessibilidade", suporteDetalhes.trim(), "accessibility") { error ->
                        suporteMensagem = error?.localizedMessage ?: "Comentário enviado para a equipe."
                        if (error == null) suporteDetalhes = ""
                    }
                },
                enabled = suporteDetalhes.trim().length >= 10 && !carregando,
            ) { Text("Enviar feedback") }
            if (suporteMensagem.isNotBlank()) Text(suporteMensagem, color = Cores.Turquesa, fontSize = 12.sp)
        }

        SecaoConta("Proteção do dispositivo") {
            Text("Use o PIN, padrão ou senha de bloqueio configurado no Android. O app nunca recebe nem armazena sua credencial.", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
            PreferenceSwitchConta("Solicitar autenticação do dispositivo", bloqueioDispositivoAtivo, onDefinirBloqueio)
        }

        SecaoConta("Recomendações para você") {
            val recommendations = if (preferences.personalizedRecommendations) painel?.recommendations.orEmpty() else emptyList()
            if (recommendations.isEmpty()) {
                Text("Jogue alguns minigames para receber recomendações baseadas no seu histórico.", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
            } else {
                recommendations.forEach { recommendation ->
                    Text(
                        "${nomeJogoConta(recommendation)} · ${recommendation.played} partidas · ${recommendation.wins} vitórias",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        SecaoConta("Avisos de manutenção e novidades") {
            painel?.announcements.orEmpty().forEach { announcement -> AvisoConta(announcement) }
            if (painel?.announcements.isNullOrEmpty()) {
                Text("Nenhum aviso de manutenção ou novidade ativo.", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
            }
            if (isAdmin) {
                var announcementType by rememberSaveable { mutableStateOf("news") }
                Text("Publicar aviso administrativo", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = avisoTitulo,
                    onValueChange = { avisoTitulo = it.take(80) },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = avisoDetalhe,
                    onValueChange = { avisoDetalhe = it.take(500) },
                    label = { Text("Detalhes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("news" to "Novidade", "maintenance" to "Manutenção").forEach { (value, label) ->
                        if (announcementType == value) Button(onClick = {}) { Text(label) }
                        else OutlinedButton(onClick = { announcementType = value }) { Text(label) }
                    }
                }
                Button(
                    onClick = {
                        onPublicarAviso(avisoTitulo.trim(), avisoDetalhe.trim(), announcementType, System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) { error ->
                            avisoMensagem = error?.localizedMessage ?: "Aviso publicado por sete dias."
                            if (error == null) {
                                avisoTitulo = ""
                                avisoDetalhe = ""
                                onRecarregar()
                            }
                        }
                    },
                    enabled = avisoTitulo.trim().length >= 3 && avisoDetalhe.trim().length >= 3 && !carregando,
                ) { Text("Publicar aviso") }
                if (avisoMensagem.isNotBlank()) Text(avisoMensagem, color = Cores.Turquesa, fontSize = 12.sp)
            }
        }

        SecaoConta("Registro de alterações da conta") {
            painel?.activity.orEmpty().forEach { activity -> AlteracaoContaLinha(activity, preferences.locale) }
            if (painel?.activity.isNullOrEmpty()) {
                Text("Alterações recentes de perfil e configurações aparecerão aqui.", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
            }
        }

        SecaoConta("Ajuda e suporte") {
            Text("Perguntas frequentes", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
            Text("• Convites de sala: compartilhe o link pela tela da sala; a outra pessoa precisa ter o Zeca instalado.", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
            Text("• Privacidade: escolha Público ou Somente amigos na Central da conta.", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
            Text("• Segurança: o bloqueio usa a credencial já configurada no Android; o app não recebe seu PIN.", color = Color.White.copy(alpha = 0.76f), fontSize = 12.sp)
            OutlinedTextField(
                value = suporteTitulo,
                onValueChange = { suporteTitulo = it.take(80) },
                label = { Text("Assunto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = suporteDetalhes,
                onValueChange = { suporteDetalhes = it.take(2_000) },
                label = { Text("Como podemos ajudar?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                onClick = {
                    onEnviarSuporte(suporteTitulo.trim(), suporteDetalhes.trim(), "help") { error ->
                        suporteMensagem = error?.localizedMessage ?: "Solicitação enviada. Obrigado."
                        if (error == null) {
                            suporteTitulo = ""
                            suporteDetalhes = ""
                        }
                    }
                },
                enabled = suporteTitulo.trim().length >= 3 && suporteDetalhes.trim().length >= 10 && !carregando,
            ) { Text("Enviar solicitação") }
            if (suporteMensagem.isNotBlank()) Text(suporteMensagem, color = Cores.Turquesa, fontSize = 12.sp)
        }

        if (painel != null) {
            OutlinedButton(onClick = onRecarregar, modifier = Modifier.fillMaxWidth()) { Text("Atualizar informações") }
        }
    }
}

@Composable
private fun MissaoWhatsAppLinha(mission: MissaoWhatsApp, onResgatar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cores.Fundo, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(mission.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(
            "${mission.progress.coerceAtMost(mission.target)}/${mission.target} · " +
                when {
                    mission.claimedInApp -> "recompensa resgatada no app"
                    mission.claimedInBot -> "resgatada no bot"
                    mission.completed -> "concluída"
                    else -> "em andamento"
                },
            color = if (mission.completed) Cores.Turquesa else Color.White.copy(alpha = 0.64f),
            fontSize = 11.sp,
        )
        if (mission.completed && !mission.claimedInApp) {
            Button(onClick = onResgatar) { Text("Resgatar 10 pontos virtuais") }
        }
    }
}

@Composable
private fun AvisoConta(announcement: AvisoApp) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (announcement.type == "maintenance") "Manutenção · ${announcement.title}" else "Novidade · ${announcement.title}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(announcement.details, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
    }
}

@Composable
private fun AlteracaoContaLinha(activity: AlteracaoConta, localeTag: String) {
    val date = remember(activity.createdAtMs) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag(localeTag)).format(Date(activity.createdAtMs))
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(date, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(activity.fields.joinToString { nomeCampoConta(it) }, color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp)
    }
}

private fun nomeCampoConta(field: String): String = when (field) {
    "displayName" -> "nome de exibição"
    "username" -> "nome de usuário"
    "bio" -> "biografia"
    "avatarUrl" -> "foto de perfil"
    "avatarAsProfilePhoto" -> "preferência de avatar"
    "profileVisibility" -> "privacidade do perfil"
    "customStatus" -> "status personalizado"
    "theme" -> "tema visual"
    "locale" -> "idioma"
    else -> field
}

private fun nomeJogoConta(recommendation: RecomendacaoJogo): String = when (recommendation.gameId) {
    "rps" -> "Pedra, papel e tesoura"
    "tug" -> "Cabo de guerra"
    "teamRace" -> "Corrida em equipe"
    "teamRelay" -> "Revezamento"
    "teamBlitz" -> "Quiz relâmpago"
    else -> recommendation.gameId.replaceFirstChar { it.uppercase() }
}
