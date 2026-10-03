package com.example.zeca.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zeca.EstadoCarreiraTrabalho
import com.example.zeca.EstadoDesafioTrabalho
import com.example.zeca.ResultadoTurnoTrabalho
import com.example.zeca.VagaTrabalho
import com.example.zeca.ui.theme.Cores
import java.util.UUID

@Composable
fun TelaTrabalho(
    onVoltar: () -> Unit,
    onCarregar: ((EstadoCarreiraTrabalho?, Exception?) -> Unit) -> Unit,
    onCandidatar: (String, (Exception?) -> Unit) -> Unit,
    onDemitir: ((Exception?) -> Unit) -> Unit,
    onPromover: ((Exception?) -> Unit) -> Unit,
    onIniciarTurno: (String, (EstadoDesafioTrabalho?, Exception?) -> Unit) -> Unit,
    onConcluirTurno: (String, Int, (ResultadoTurnoTrabalho?, Exception?) -> Unit) -> Unit,
) {
    var carreira by remember { mutableStateOf<EstadoCarreiraTrabalho?>(null) }
    var desafio by remember { mutableStateOf<EstadoDesafioTrabalho?>(null) }
    var resultado by remember { mutableStateOf<ResultadoTurnoTrabalho?>(null) }
    var erro by remember { mutableStateOf("") }
    var carregando by remember { mutableStateOf(false) }
    var acaoEmAndamento by remember { mutableStateOf(false) }

    fun carregar() {
        carregando = true
        onCarregar { state, error ->
            carregando = false
            if (error != null) erro = error.localizedMessage ?: "Não foi possível carregar os empregos."
            else {
                carreira = state
                desafio = state?.desafioAtivo
                erro = ""
            }
        }
    }

    LaunchedEffect(Unit) { carregar() }
    BackHandler(onBack = onVoltar)

    Column(
        Modifier.fillMaxSize().background(Cores.Fundo).verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Trabalho", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Escolha uma carreira e conclua o minijogo do turno.", color = Cores.Cinza, fontSize = 13.sp)
            }
            TextButton(onClick = onVoltar) { Text("Voltar", color = Cores.Turquesa) }
        }
        erro.takeIf(String::isNotBlank)?.let { Text(it, color = Cores.Laranja, fontSize = 12.sp) }
        if (carregando && carreira == null) {
            Text("Carregando vagas...", color = Cores.Cinza)
        }
        carreira?.let { estado ->
            val cargo = estado.cargoAtual
            if (cargo != null) {
                BlocoTrabalho {
                    Text("SEU CARGO", color = Cores.Verde, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(cargo.nome, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(cargo.nomeTier, color = Cores.Turquesa, fontSize = 12.sp)
                    Text(
                        "Salário por turno: ${formatarReais(cargo.salarioMinimoCentavos)}–${formatarReais(cargo.salarioMaximoCentavos)}",
                        color = Cores.Cinza,
                        fontSize = 13.sp,
                    )
                    val progresso = (estado.carreira.turnosNaCategoria.toFloat() / cargo.turnosParaPromocao.coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progresso },
                        modifier = Modifier.fillMaxWidth(),
                        color = Cores.Verde,
                        trackColor = Cores.Borda,
                    )
                    Text(
                        "${estado.carreira.turnosNaCategoria}/${cargo.turnosParaPromocao} turnos para promoção",
                        color = Cores.Cinza,
                        fontSize = 11.sp,
                    )
                    if (estado.esperaProximoTurnoMs > 0) {
                        Text(
                            "Próximo turno em ${formatarTempo(estado.esperaProximoTurnoMs)}.",
                            color = Cores.Laranja,
                            fontSize = 12.sp,
                        )
                    }
                    if (estado.carreira.categoriaDesbloqueada < 5
                        && estado.nivel >= estado.carreira.categoriaDesbloqueada.let { estado.vagas.firstOrNull { vaga -> vaga.tier == it + 1 }?.nivelMinimo ?: Int.MAX_VALUE }
                        && estado.carreira.turnosNaCategoria >= cargo.turnosParaPromocao
                    ) {
                        OutlinedButton(
                            onClick = {
                                acaoEmAndamento = true
                                onPromover { error ->
                                    acaoEmAndamento = false
                                    erro = error?.localizedMessage ?: "Categoria promovida! Escolha uma vaga nova."
                                    if (error == null) carregar()
                                }
                            },
                            enabled = !acaoEmAndamento,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (acaoEmAndamento) "Promovendo..." else "Desbloquear próxima categoria")
                        }
                    }
                    if (desafio == null && estado.esperaProximoTurnoMs == 0L) {
                        Button(
                            onClick = {
                                acaoEmAndamento = true
                                resultado = null
                                onIniciarTurno(UUID.randomUUID().toString()) { challenge, error ->
                                    acaoEmAndamento = false
                                    if (error != null) erro = error.localizedMessage ?: "Não foi possível iniciar o turno."
                                    else {
                                        desafio = challenge
                                        erro = ""
                                    }
                                }
                            },
                            enabled = !acaoEmAndamento,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (acaoEmAndamento) "Preparando turno..." else "Iniciar turno") }
                    }
                    OutlinedButton(
                        onClick = {
                            acaoEmAndamento = true
                            onDemitir { error ->
                                acaoEmAndamento = false
                                erro = error?.localizedMessage ?: "Você saiu do emprego. O período de descanso é de 20 minutos."
                                if (error == null) carregar()
                            }
                        },
                        enabled = !acaoEmAndamento,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Pedir demissão") }
                }
            } else {
                BlocoTrabalho {
                    Text("VAGAS DISPONÍVEIS", color = Cores.Verde, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Nível ${estado.nivel} · categoria ${estado.carreira.categoriaDesbloqueada} liberada", color = Cores.Cinza, fontSize = 12.sp)
                    val vagas = estado.vagas.filter {
                        it.tier <= estado.carreira.categoriaDesbloqueada && it.nivelMinimo <= estado.nivel
                    }
                    if (vagas.isEmpty()) {
                        Text("Nenhuma vaga disponível para o seu nível.", color = Cores.Cinza)
                    }
                    vagas.forEach { vaga ->
                        VagaCard(
                            vaga = vaga,
                            enabled = !acaoEmAndamento && estado.carreira.recontratacaoAposMs <= System.currentTimeMillis(),
                        ) {
                            acaoEmAndamento = true
                            onCandidatar(vaga.slug) { error ->
                                acaoEmAndamento = false
                                erro = error?.localizedMessage ?: "Contratado como ${vaga.nome}."
                                if (error == null) carregar()
                            }
                        }
                    }
                    if (estado.carreira.recontratacaoAposMs > System.currentTimeMillis()) {
                        Text("Novo emprego disponível em ${formatarTempo(estado.carreira.recontratacaoAposMs - System.currentTimeMillis())}.", color = Cores.Laranja, fontSize = 12.sp)
                    }
                }
            }

            resultado?.let { turno ->
                BlocoTrabalho {
                    Text(if (turno.respostaCorreta) "Turno concluído!" else "Turno concluído com desconto", color = Cores.Verde, fontWeight = FontWeight.Bold)
                    Text("Salário: +${formatarReais(turno.salarioCentavos)}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            desafio?.let { current ->
                BlocoTrabalho {
                    Text("MINIJOGO DO TURNO · ${current.cargo}", color = Cores.Turquesa, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(current.pergunta, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Conclua em até 5 minutos. A resposta correta paga o salário integral.", color = Cores.Cinza, fontSize = 11.sp)
                    current.opcoes.forEachIndexed { index, option ->
                        OutlinedButton(
                            onClick = {
                                acaoEmAndamento = true
                                onConcluirTurno(current.id, index) { result, error ->
                                    acaoEmAndamento = false
                                    if (error != null) erro = error.localizedMessage ?: "Não foi possível concluir o turno."
                                    else {
                                        resultado = result
                                        desafio = null
                                        erro = ""
                                        carregar()
                                    }
                                }
                            },
                            enabled = !acaoEmAndamento,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option.toString()) }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun BlocoTrabalho(conteudo: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(Cores.Cartao, RoundedCornerShape(18.dp))
            .border(1.dp, Cores.Borda, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = { conteudo() },
    )
}

@Composable
private fun VagaCard(vaga: VagaTrabalho, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Cores.Fundo, RoundedCornerShape(12.dp))
            .border(1.dp, Cores.Borda, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(vaga.nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("${vaga.nomeTier} · Nível ${vaga.nivelMinimo}+", color = Cores.Cinza, fontSize = 11.sp)
        Text(
            "${formatarReais(vaga.salarioMinimoCentavos)}–${formatarReais(vaga.salarioMaximoCentavos)} por turno",
            color = Cores.Turquesa,
            fontSize = 12.sp,
        )
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(if (enabled) "Candidatar-se" else "Aguarde")
        }
    }
}

private fun formatarTempo(milisegundos: Long): String {
    val minutos = ((milisegundos.coerceAtLeast(0) + 59_999) / 60_000)
    val horas = minutos / 60
    val resto = minutos % 60
    return if (horas > 0) "${horas}h ${resto}min" else "${resto}min"
}
