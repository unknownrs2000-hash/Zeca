package com.example.zeca.ui

import java.math.BigDecimal
import java.math.RoundingMode

/** Cotação da conta atual (unidades da moeda da conta por 1 BRL). Nunca devolve 0. */
internal fun cotacaoDaConta(): Double =
    AppCurrencyFormatter.current.value.rate.takeIf { it > 0.0 } ?: 1.0

/** Código ISO da moeda da conta atual (ex.: BRL, USD, EUR). */
internal fun codigoMoedaDaConta(): String =
    AppCurrencyFormatter.current.value.currencyCode.ifBlank { "BRL" }

/** Converte centavos da moeda da conta para centavos da moeda base (BRL). */
internal fun centavosDaContaParaBase(centavosNaMoedaDaConta: Long): Long =
    BigDecimal.valueOf(centavosNaMoedaDaConta)
        .divide(BigDecimal.valueOf(cotacaoDaConta()), 0, RoundingMode.HALF_UP)
        .longValueExact()

/** Converte centavos da moeda base (BRL) para centavos da moeda da conta. */
internal fun centavosBaseParaConta(centavosBase: Long): Long =
    BigDecimal.valueOf(centavosBase)
        .multiply(BigDecimal.valueOf(cotacaoDaConta()))
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

/** Lê o texto digitado (na moeda da conta) e devolve centavos da moeda BASE, ou null se inválido. */
internal fun parseValorBaseCentavos(texto: String): Long? =
    parseValorCentavos(texto)?.let { centavosDaContaParaBase(it) }

/**
 * Maior valor base que, ao ir para o campo (moeda da conta) e voltar para a base,
 * NÃO ultrapassa centavosBase. Evita o "Máx" passar do saldo por arredondamento.
 */
internal fun baseNoCampo(centavosBase: Long): Long {
    var candidato = centavosBase
    var tentativas = 0
    while (candidato > 0 && tentativas < 1_000) {
        val volta = centavosDaContaParaBase(centavosBaseParaConta(candidato))
        if (volta <= centavosBase) return volta
        candidato--
        tentativas++
    }
    return centavosBase
}