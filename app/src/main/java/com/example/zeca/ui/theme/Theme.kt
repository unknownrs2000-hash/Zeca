package com.example.zeca.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Cores {
    val Fundo = Color(0xFF090D10)
    val Cartao = Color(0xFF171C20)
    val Barra = Color(0xD9272D32)
    val Borda = Color(0x45FFFFFF)
    val Verde = Color(0xFF2BD576)
    val Laranja = Color(0xFFFF7A2F)
    val Turquesa = Color(0xFF22C7C1)
    val Roxo = Color(0xFF8B5CF6)
    val Cinza = Color(0xFF8E8E93)
}

private val Tipografia = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
)

@Composable
fun CassinoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Cores.Fundo,
            surface = Cores.Cartao,
            primary = Cores.Verde,
            onPrimary = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        typography = Tipografia,
        content = content,
    )
}