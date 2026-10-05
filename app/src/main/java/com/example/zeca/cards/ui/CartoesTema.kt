package com.example.zeca.cards.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.zeca.ui.theme.Cores

/** Cor usada para saídas de dinheiro e ações de risco (a mesma do extrato da Carteira). */
internal val CorPerigo = Color(0xFFFF8790)

/** Diálogo no mesmo estilo do comprovante da Carteira: degradê escuro, borda clara e cantos grandes. */
@Composable
internal fun DialogoZeca(
    titulo: String,
    subtitulo: String? = null,
    onFechar: () -> Unit,
    podeFechar: Boolean = true,
    conteudo: @Composable ColumnScope.() -> Unit,
    acoes: @Composable RowScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (podeFechar) onFechar() },
        properties = DialogProperties(
            dismissOnBackPress = podeFechar,
            dismissOnClickOutside = podeFechar,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF222A2E), Cores.Cartao, Cores.Fundo)))
                .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(28.dp))
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titulo, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                if (subtitulo != null) {
                    Text(subtitulo, color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp)
                }
            }
            conteudo()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { acoes() }
        }
    }
}

@Composable
internal fun BotaoPrimario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cor: Color = Cores.Verde,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = cor,
            contentColor = Color(0xFF06110B),
            disabledContainerColor = Color.White.copy(alpha = 0.12f),
            disabledContentColor = Color.White.copy(alpha = 0.45f),
        ),
    ) {
        Text(texto, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
internal fun BotaoSecundario(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    corTexto: Color = Color.White,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Cores.Borda),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = corTexto,
            disabledContentColor = Color.White.copy(alpha = 0.4f),
        ),
    ) {
        Text(texto, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
internal fun CampoZeca(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = onValor,
        label = { Text(etiqueta) },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            disabledTextColor = Color.White.copy(alpha = 0.5f),
            focusedBorderColor = Cores.Verde,
            unfocusedBorderColor = Cores.Borda,
            focusedLabelColor = Cores.Verde,
            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
            cursorColor = Cores.Verde,
        ),
    )
}

/**
 * PIN de 4 dígitos em caixinhas com bolinhas. O texto digitado fica invisível;
 * quem aparece são as bolinhas. [avancarAoCompletar] pula para o próximo campo no quarto dígito.
 */
@Composable
internal fun CampoPin(
    valor: String,
    onValor: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    autoFoco: Boolean = true,
    avancarAoCompletar: Boolean = false,
) {
    val foco = remember { FocusRequester() }
    val gerenciadorFoco = LocalFocusManager.current
    var focado by remember { mutableStateOf(false) }

    if (autoFoco) {
        LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(etiqueta, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = valor,
            onValueChange = { novo ->
                val digitos = novo.filter { it.isDigit() }.take(4)
                onValor(digitos)
                if (avancarAoCompletar && digitos.length == 4 && valor.length < 4) {
                    gerenciadorFoco.moveFocus(FocusDirection.Next)
                }
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(foco)
                .onFocusChanged { focado = it.isFocused },
            decorationBox = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) { i ->
                        val atual = focado && enabled && i == valor.length.coerceAtMost(3)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.07f))
                                .border(
                                    if (atual) 1.5.dp else 1.dp,
                                    if (atual) Cores.Verde else Cores.Borda,
                                    RoundedCornerShape(14.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i < valor.length) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                            }
                        }
                    }
                }
            },
        )
    }
}

/** Cabeçalho das telas de cartão: "Voltar", título grande e subtítulo apagado. */
@Composable
internal fun CabecalhoZeca(titulo: String, subtitulo: String? = null, onVoltar: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = onVoltar) { Text("Voltar", color = Cores.Verde, fontWeight = FontWeight.SemiBold) }
        Text(titulo, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        if (subtitulo != null) {
            Text(subtitulo, color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp)
        }
    }
}

/** Caixa com o mesmo degradê, borda e cantos do diálogo, para agrupar valor, nome e comprovante. */
@Composable
internal fun PainelZeca(modifier: Modifier = Modifier, conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222A2E), Cores.Cartao)))
            .border(1.dp, Color.White.copy(alpha = 0.17f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = conteudo,
    )
}

@Composable
internal fun RotuloZeca(texto: String) {
    Text(texto, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun TextoErro(texto: String) {
    Text(texto, color = Cores.Laranja, fontSize = 12.sp)
}