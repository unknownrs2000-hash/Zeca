package com.example.zeca.cards.money

import com.example.zeca.ui.codigoMoedaDaConta
import com.example.zeca.ui.textoCampoDaBase

// Única porta de entrada de moeda das telas de cartão. Toda conversão fica no MoedaConta.kt.
fun formatarBase(centavosBase: Long): String =
    "${codigoMoedaDaConta()} ${textoCampoDaBase(centavosBase)}"