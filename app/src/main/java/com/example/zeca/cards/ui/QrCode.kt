package com.example.zeca.cards.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private fun gerarQrBitmap(texto: String, tamanho: Int = 720): Bitmap {
    val matriz = QRCodeWriter().encode(
        texto,
        BarcodeFormat.QR_CODE,
        tamanho,
        tamanho,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(tamanho * tamanho) { i ->
        if (matriz.get(i % tamanho, i / tamanho)) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, tamanho, tamanho, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrDaCobranca(texto: String, modifier: Modifier = Modifier) {
    val imagem = remember(texto) { gerarQrBitmap(texto).asImageBitmap() }
    // Fundo branco fixo: o leitor precisa de contraste mesmo no tema escuro.
    Box(modifier = modifier.background(androidx.compose.ui.graphics.Color.White).padding(12.dp)) {
        Image(bitmap = imagem, contentDescription = "QR code da cobrança", modifier = Modifier.size(240.dp))
    }
}