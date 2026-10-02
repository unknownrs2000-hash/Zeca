package com.example.zeca.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.ui.theme.Cores
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private val secoesAtividadesExtras = listOf("Treino", "Pedra-papel-tesoura", "Regras e retornos", "Calendário")
private val iconesSecoesExtras = listOf("🎯", "✊", "📘", "🗓️")
private val descricoesSecoesExtras = listOf("Pratique com o bot", "Jogue no mesmo aparelho", "Saiba como funciona", "Desafios da semana")
private val sequenciaTreinoExtras = listOf(1, 3, 0, 2)
private val perguntaTreinoExtras = "Qual é o maior oceano da Terra?"
private val respostasTreinoExtras = listOf("Atlântico", "Índico", "Pacífico", "Ártico")

@Composable
fun TelaAtividadesExtras(eventoSemanal: EventoSemanal?, onClose: () -> Unit) {
    var secaoSelecionada by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Cores.Fundo)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 16.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Cores.Cartao, Cores.Turquesa.copy(alpha = 0.22f)),
                    ),
                    shape = RoundedCornerShape(24.dp),
                )
                .border(1.dp, Cores.Turquesa.copy(alpha = 0.26f), RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(Cores.Turquesa.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎮", fontSize = 27.sp)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "ZECA  ·  JOGUE DO SEU JEITO",
                        color = Cores.Turquesa,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Text("Atividades extras", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        "Treine, desafie alguém ou confira as regras.",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 12.sp,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("Fechar", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (rowIndex in 0..1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (columnIndex in 0..1) {
                        val index = rowIndex * 2 + columnIndex
                        SectionCardExtras(
                            title = secoesAtividadesExtras[index],
                            description = descricoesSecoesExtras[index],
                            icon = iconesSecoesExtras[index],
                            selected = secaoSelecionada == index,
                            modifier = Modifier.weight(1f),
                        ) { secaoSelecionada = index }
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            when (secaoSelecionada) {
                0 -> TreinoCasualExtras()
                1 -> JokenpoLocalExtras()
                2 -> ExplicacaoRegrasExtras()
                else -> CalendarioAtividadeExtras(eventoSemanal)
            }
        }
    }
}

@Composable
private fun SectionCardExtras(
    title: String,
    description: String,
    icon: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(
                color = if (selected) Cores.Turquesa.copy(alpha = 0.14f) else Cores.Cartao,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 1.dp,
                color = if (selected) Cores.Turquesa.copy(alpha = 0.7f) else Cores.Borda,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = if (selected) Cores.Turquesa.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 18.sp)
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = if (selected) Cores.Turquesa else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                description,
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SectionChipExtras(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White.copy(alpha = 0.82f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(if (selected) Cores.Turquesa else Cores.Cartao, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ExtrasCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Cores.Cartao),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun TreinoCasualExtras() {
    var treinoSelecionado by rememberSaveable { mutableStateOf("quizSprint") }
    var respostaQuiz by rememberSaveable { mutableStateOf<Int?>(null) }
    var mostrarSequencia by rememberSaveable { mutableStateOf(false) }
    val tentativasMemoria = remember { mutableStateListOf<Int>() }
    var resultadoMemoria by rememberSaveable { mutableStateOf("") }
    var escolhaJogadorBot by rememberSaveable { mutableStateOf<EscolhaJokenpoExtras?>(null) }
    var escolhaBot by rememberSaveable { mutableStateOf<EscolhaJokenpoExtras?>(null) }
    var resultadoBot by rememberSaveable { mutableStateOf("") }

    Text("Prática casual contra o bot", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text(
        "Treinos locais inspirados nos minijogos simples. Sem aposta, prêmio ou alteração de saldo.",
        color = Color.White.copy(alpha = 0.68f),
        fontSize = 13.sp,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            "quizSprint" to "Perguntas",
            "memorySequence" to "Memória",
            "rpsBot" to "Jokenpô vs bot",
        ).forEach { (id, label) ->
            SectionChipExtras(label, treinoSelecionado == id) {
                treinoSelecionado = id
                respostaQuiz = null
                mostrarSequencia = false
                tentativasMemoria.clear()
                resultadoMemoria = ""
                escolhaJogadorBot = null
                escolhaBot = null
                resultadoBot = ""
            }
        }
    }
    ExtrasCard {
        Text(
            when (treinoSelecionado) {
                "quizSprint" -> "Desafio relâmpago · treino"
                "memorySequence" -> "Memória em sequência · treino"
                else -> "Jokenpô · partida contra bot"
            },
            color = Cores.Turquesa,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        if (treinoSelecionado != "rpsBot") {
            Text("Bot de treino: demonstração local, sem classificação.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
        }
        if (treinoSelecionado == "quizSprint") {
            Text(perguntaTreinoExtras, color = Color.White, fontWeight = FontWeight.SemiBold)
            respostasTreinoExtras.forEachIndexed { index, resposta ->
                OutlinedButton(
                    onClick = { respostaQuiz = index },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = respostaQuiz == null,
                ) { Text(resposta, color = Color.White) }
            }
            respostaQuiz?.let { escolha ->
                Text(
                    if (escolha == 2) "Acertou! O bot conferiu a resposta no treino." else "Resposta do treino: Pacífico.",
                    color = if (escolha == 2) Cores.Verde else Color.White.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Medium,
                )
                TextButton(onClick = { respostaQuiz = null }) { Text("Tentar novamente", color = Cores.Turquesa) }
            }
        } else if (treinoSelecionado == "memorySequence") {
            Text(
                if (mostrarSequencia) "Memorize: ${sequenciaTreinoExtras.joinToString(" · ") { (it + 1).toString() }}"
                else "A sequência do treino tem ${sequenciaTreinoExtras.size} sinais.",
                color = Color.White,
            )
            if (!mostrarSequencia && tentativasMemoria.isEmpty()) {
                Button(
                    onClick = { mostrarSequencia = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.Turquesa, contentColor = Color.Black),
                ) { Text("Mostrar sequência") }
                Text("Quando estiver pronto, toque em esconder e repita os números na ordem.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
                TextButton(onClick = { mostrarSequencia = false }) { Text("Esconder sequência", color = Cores.Turquesa) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0..3).forEach { item ->
                        OutlinedButton(
                            onClick = {
                                if (tentativasMemoria.size < sequenciaTreinoExtras.size) {
                                    tentativasMemoria.add(item)
                                    if (tentativasMemoria.size == sequenciaTreinoExtras.size) {
                                        resultadoMemoria = if (tentativasMemoria.toList() == sequenciaTreinoExtras) {
                                            "Sequência correta! O bot validou o treino."
                                        } else {
                                            "Quase! A sequência de referência era ${sequenciaTreinoExtras.joinToString(" · ") { (it + 1).toString() }}."
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("${item + 1}", color = Color.White) }
                    }
                }
                Text("Sua sequência: ${tentativasMemoria.joinToString(" · ") { (it + 1).toString() }}", color = Color.White.copy(alpha = 0.76f))
                if (resultadoMemoria.isNotBlank()) {
                    Text(resultadoMemoria, color = Cores.Verde)
                    TextButton(onClick = {
                        tentativasMemoria.clear()
                        resultadoMemoria = ""
                        mostrarSequencia = false
                    }) { Text("Novo treino", color = Cores.Turquesa) }
                } else {
                    TextButton(onClick = { mostrarSequencia = false }) { Text("Esconder referência", color = Cores.Turquesa) }
                }
            }
        } else {
            Text("Partida casual contra um bot · sem aposta, prêmio ou classificação.", color = Color.White.copy(alpha = 0.7f))
            if (escolhaJogadorBot == null) {
                OpcoesJokenpoExtras { choice ->
                    val botChoice = EscolhaJokenpoExtras.values().random()
                    escolhaJogadorBot = choice
                    escolhaBot = botChoice
                    val playerWins = (choice == EscolhaJokenpoExtras.PEDRA && botChoice == EscolhaJokenpoExtras.TESOURA)
                        || (choice == EscolhaJokenpoExtras.PAPEL && botChoice == EscolhaJokenpoExtras.PEDRA)
                        || (choice == EscolhaJokenpoExtras.TESOURA && botChoice == EscolhaJokenpoExtras.PAPEL)
                    resultadoBot = when {
                        choice == botChoice -> "Empate com o bot."
                        playerWins -> "Você venceu esta rodada!"
                        else -> "O bot venceu esta rodada."
                    }
                }
            } else {
                Text("Você: ${escolhaJogadorBot?.rotulo} · Bot: ${escolhaBot?.rotulo}", color = Color.White)
                Text(resultadoBot, color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                TextButton(onClick = {
                    escolhaJogadorBot = null
                    escolhaBot = null
                    resultadoBot = ""
                }) { Text("Jogar outra rodada", color = Cores.Turquesa) }
            }
        }
    }
}

private enum class EscolhaJokenpoExtras(val rotulo: String, val emoji: String) {
    PEDRA("Pedra", "✊"),
    PAPEL("Papel", "✋"),
    TESOURA("Tesoura", "✌️"),
}

@Composable
private fun JokenpoLocalExtras() {
    val escolhas = remember { mutableStateListOf<EscolhaJokenpoExtras?>(null, null) }
    var vencedorDaRodada by remember { mutableStateOf<String?>(null) }
    var rodadas by remember { mutableStateOf(0) }
    var pontosP1 by remember { mutableStateOf(0) }
    var pontosP2 by remember { mutableStateOf(0) }
    var empate by remember { mutableStateOf(false) }
    var partidaFinalizada by remember { mutableStateOf(false) }

    fun registrarEscolha(jogador: Int, escolha: EscolhaJokenpoExtras) {
        escolhas[jogador] = escolha
        val p1 = escolhas[0] ?: return
        val p2 = escolhas[1] ?: return
        empate = p1 == p2
        val p1Ganha = (p1 == EscolhaJokenpoExtras.PEDRA && p2 == EscolhaJokenpoExtras.TESOURA) ||
            (p1 == EscolhaJokenpoExtras.PAPEL && p2 == EscolhaJokenpoExtras.PEDRA) ||
            (p1 == EscolhaJokenpoExtras.TESOURA && p2 == EscolhaJokenpoExtras.PAPEL)
        rodadas += 1
        if (!empate) {
            if (p1Ganha) pontosP1 += 1 else pontosP2 += 1
        }
        vencedorDaRodada = if (empate) "Empate na rodada." else if (p1Ganha) "Ponto para o Jogador 1." else "Ponto para o Jogador 2."
        partidaFinalizada = pontosP1 == 3 || pontosP2 == 3 || rodadas == 5
    }

    Text("Pedra, papel e tesoura", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text("Tela dividida no mesmo aparelho · melhor de cinco · sem aposta ou alteração de saldo.", color = Color.White.copy(alpha = 0.68f), fontSize = 13.sp)
    ExtrasCard {
        Text("Placar  ·  Jogador 1  $pontosP1 — $pontosP2  Jogador 2", color = Color.White, fontWeight = FontWeight.Bold)
        when {
            partidaFinalizada -> {
                Text(
                    when {
                        pontosP1 > pontosP2 -> "Jogador 1 venceu a partida."
                        pontosP2 > pontosP1 -> "Jogador 2 venceu a partida."
                        else -> "Partida empatada."
                    },
                    color = Cores.Turquesa,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (vencedorDaRodada != null) {
                    Text("Rodada ${rodadas}/5 · ${vencedorDaRodada}", color = Color.White.copy(alpha = 0.82f))
                    Text(
                        "Jogador 1: ${escolhas[0]?.let { "${it.emoji} ${it.rotulo}" }}   ·   Jogador 2: ${escolhas[1]?.let { "${it.emoji} ${it.rotulo}" }}",
                        color = Color.White,
                    )
                }
                Button(
                    onClick = {
                        escolhas[0] = null
                        escolhas[1] = null
                        vencedorDaRodada = null
                        rodadas = 0
                        pontosP1 = 0
                        pontosP2 = 0
                        empate = false
                        partidaFinalizada = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.Turquesa, contentColor = Color.Black),
                ) { Text("Jogar novamente") }
            }
            vencedorDaRodada != null -> {
                Text("Rodada ${rodadas}/5 · ${vencedorDaRodada}", color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                Text(
                    "Jogador 1: ${escolhas[0]?.let { "${it.emoji} ${it.rotulo}" }}   ·   Jogador 2: ${escolhas[1]?.let { "${it.emoji} ${it.rotulo}" }}",
                    color = Color.White,
                )
                Button(
                    onClick = {
                        escolhas[0] = null
                        escolhas[1] = null
                        vencedorDaRodada = null
                        empate = false
                    },
                    enabled = !partidaFinalizada,
                    colors = ButtonDefaults.buttonColors(containerColor = Cores.Turquesa, contentColor = Color.Black),
                ) { Text(if (partidaFinalizada) "Partida concluída" else "Próxima rodada") }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Jogador 1", "Jogador 2").forEachIndexed { jogador, titulo ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(titulo, color = Cores.Turquesa, fontWeight = FontWeight.Bold)
                            if (escolhas[jogador] == null) {
                                Text("Escolha na sua metade:", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                                OpcoesJokenpoExtras { escolha -> registrarEscolha(jogador, escolha) }
                            } else {
                                Text("Escolha registrada · aguardando o outro jogador.", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    Text("As escolhas só são reveladas quando ambos confirmam. Gire o aparelho ou passe-o entre as metades.", color = Color.White.copy(alpha = 0.56f), fontSize = 11.sp)
}

@Composable
private fun OpcoesJokenpoExtras(onSelect: (EscolhaJokenpoExtras) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EscolhaJokenpoExtras.values().forEach { escolha ->
            OutlinedButton(onClick = { onSelect(escolha) }, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(escolha.emoji, fontSize = 22.sp)
                    Text(escolha.rotulo, color = Color.White, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ExplicacaoRegrasExtras() {
    Text("Resultados e retornos", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text(
        "Informação sobre os jogos solo existentes. Esta página não estima probabilidades nem substitui as regras exibidas no jogo.",
        color = Color.White.copy(alpha = 0.68f),
        fontSize = 13.sp,
    )
    ExtrasCard {
        Text("Regras factuais", color = Cores.Turquesa, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        RuleLineExtras("• O servidor é a autoridade para resultados e liquidação dos jogos solo com aposta.")
        RuleLineExtras("• O aplicativo envia escolhas ou ações e apresenta o resultado recebido; a interface não deve ser tratada como fonte de aleatoriedade.")
        RuleLineExtras("• Cada jogo pode ter regras de liquidação próprias. Consulte o valor e as condições mostrados na tela daquele jogo antes de confirmar.")
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Text("Como ler a matemática", color = Color.White, fontWeight = FontWeight.Bold)
        RuleLineExtras("A = valor apostado; R = retorno bruto informado na liquidação.")
        RuleLineExtras("Resultado líquido = R − A. Exemplo aritmético: se A = 100 e R = 180, o líquido é 80; o retorno bruto inclui a aposta devolvida.")
        RuleLineExtras("O exemplo é apenas uma demonstração de cálculo, não uma cotação nem uma promessa de pagamento.")
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Text("Sem odds inventadas", color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "Não há porcentagens ou probabilidades nesta explicação: elas dependem das regras específicas de cada jogo e não são inferidas aqui.",
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RuleLineExtras(text: String) {
    Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun CalendarioAtividadeExtras(eventoSemanal: EventoSemanal?) {
    val hoje = remember { LocalDate.now(java.time.ZoneOffset.UTC) }
    val iso = WeekFields.ISO
    val numeroSemana = hoje.get(iso.weekOfWeekBasedYear())
    val anoSemana = hoje.get(iso.weekBasedYear())
    val segundaFeira = hoje.with(iso.dayOfWeek(), 1)
    val datasSemana = (0L..6L).map(segundaFeira::plusDays)
    val localePt = Locale.forLanguageTag("pt-BR")
    val formatoDia = DateTimeFormatter.ofPattern("EEE", localePt)
    val formatoData = DateTimeFormatter.ofPattern("dd/MM", localePt)

    Text("Calendário de atividade", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text(
        "Semana ISO $numeroSemana · $anoSemana",
        color = Cores.Turquesa,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        "Hoje: ${hoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
        color = Color.White.copy(alpha = 0.74f),
        fontSize = 13.sp,
    )
    ExtrasCard {
        Text(
            eventoSemanal?.tema ?: "Nenhum evento semanal disponível.",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        eventoSemanal?.let { event ->
            Text(event.descricao, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            Text("Progresso: ${event.progresso}/${event.meta} · Recompensa: ${event.recompensa}", color = Cores.Turquesa, fontSize = 12.sp)
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Text(
            "Datas desta semana: ${datasSemana.first().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))} – ${datasSemana.last().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            datasSemana.forEach { data ->
                val ehHoje = data == hoje
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (ehHoje) Cores.Turquesa.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.045f), RoundedCornerShape(10.dp))
                        .border(
                            width = if (ehHoje) 1.dp else 0.dp,
                            color = if (ehHoje) Cores.Turquesa else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(vertical = 9.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(data.format(formatoDia).replaceFirstChar { it.uppercase(localePt) }, color = Color.White.copy(alpha = 0.72f), fontSize = 9.sp)
                    Text(data.format(formatoData), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (ehHoje) Text("Hoje", color = Cores.Turquesa, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("A atividade semanal vigente permanece ativa até o fim desta semana.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
    }
}
