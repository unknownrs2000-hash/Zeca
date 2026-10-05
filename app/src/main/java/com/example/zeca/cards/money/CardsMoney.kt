package com.example.zeca.cards.money

import com.example.zeca.ui.centavosBaseParaConta
import com.example.zeca.ui.codigoMoedaDaConta
import com.example.zeca.ui.parseValorBaseCentavos
import java.math.BigDecimal
import java.util.Locale

// Têm que bater com LIMITS em functions/cards/cards-logic.js. O servidor é quem decide de fato.
const val COBRANCA_MIN_BASE_CENTAVOS = 100L
const val COBRANCA_MAX_BASE_CENTAVOS = 1_000_000L

// As telas de cartão só usam este arquivo para falar de dinheiro. A conversão em si
// continua em MoedaConta.kt (uma única fonte).
fun moedaDaConta(): String = codigoMoedaDaConta()

/** Texto digitado (moeda da conta) -> centavos da moeda base, ou null se inválido. */
fun valorDigitadoParaBase(texto: String): Long? = parseValorBaseCentavos(texto)

/** Centavos da moeda base -> texto na moeda da conta, ex.: "BRL 12,50". */
fun formatarBase(centavosBase: Long): String {
    val naConta = BigDecimal.valueOf(centavosBaseParaConta(centavosBase), 2)
    return String.format(Locale.forLanguageTag("pt-BR"), "%s %,.2f", codigoMoedaDaConta(), naConta)
}