package com.example.zeca.cards.data

import com.example.zeca.FirebaseRepository

const val STATUS_PENDENTE = "pending"
const val STATUS_PAGA = "paid"
const val STATUS_EXPIRADA = "expired"

data class Cobranca(
    val id: String,
    val recebedorUid: String,
    val recebedorNome: String,
    val recebedorUsername: String,
    val valorBaseCentavos: Long,
    val descricao: String,
    val status: String,
    val expiraEmMs: Long,
)

data class ComprovantePagamento(
    val cobrancaId: String,
    val valorBaseCentavos: Long,
    val taxaBaseCentavos: Long,
    val descricao: String,
    val saldoBaseCentavos: Long,
)

fun mensagemDeErro(erro: Exception?): String =
    erro?.localizedMessage?.takeIf { it.isNotBlank() } ?: "Algo deu errado. Tente de novo."

// Todas as chamadas trabalham em centavos da moeda BASE (BRL). A conversão fica em cards/money.
object CardsRepository {
    private const val PREFIXO_QR = "zeca://charge/"
    private val FORMATO_ID = Regex("^[a-fA-F0-9-]{36}$")

    fun textoQr(cobrancaId: String): String = PREFIXO_QR + cobrancaId

    fun idDoQr(texto: String): String? = texto.trim()
        .takeIf { it.startsWith(PREFIXO_QR) }
        ?.removePrefix(PREFIXO_QR)
        ?.takeIf { FORMATO_ID.matches(it) }

    fun criarCobranca(
        valorBaseCentavos: Long,
        descricao: String,
        requestId: String,
        callback: (Cobranca?, Exception?) -> Unit,
    ) {
        FirebaseRepository.chamarFunction(
            "createCharge",
            mapOf(
                "requestId" to requestId,
                "amountCents" to valorBaseCentavos,
                "description" to descricao,
            ),
        ) { data, erro -> entregarCobranca(data, erro, callback) }
    }

    fun buscarCobranca(cobrancaId: String, callback: (Cobranca?, Exception?) -> Unit) {
        FirebaseRepository.chamarFunction("getCharge", mapOf("chargeId" to cobrancaId)) { data, erro ->
            entregarCobranca(data, erro, callback)
        }
    }

    fun pagarCobranca(cobrancaId: String, callback: (ComprovantePagamento?, Exception?) -> Unit) {
        FirebaseRepository.chamarFunction("payCharge", mapOf("chargeId" to cobrancaId)) { data, erro ->
            if (erro != null || data == null) {
                callback(null, erro ?: IllegalStateException("O servidor não confirmou o pagamento."))
                return@chamarFunction
            }
            callback(
                ComprovantePagamento(
                    cobrancaId = data["chargeId"] as? String ?: cobrancaId,
                    valorBaseCentavos = (data["amountCents"] as? Number)?.toLong() ?: 0L,
                    taxaBaseCentavos = (data["feeCents"] as? Number)?.toLong() ?: 0L,
                    descricao = data["description"] as? String ?: "",
                    saldoBaseCentavos = (data["balanceCents"] as? Number)?.toLong() ?: 0L,
                ),
                null,
            )
        }
    }

    private fun entregarCobranca(
        data: Map<String, Any>?,
        erro: Exception?,
        callback: (Cobranca?, Exception?) -> Unit,
    ) {
        val cobranca = data?.toCobranca()
        callback(
            cobranca,
            erro ?: if (cobranca == null) IllegalStateException("Resposta da cobrança inválida.") else null,
        )
    }

    private fun Map<String, Any>.toCobranca(): Cobranca? {
        val id = this["chargeId"] as? String ?: return null
        return Cobranca(
            id = id,
            recebedorUid = this["merchantUid"] as? String ?: "",
            recebedorNome = this["merchantName"] as? String ?: "Jogador",
            recebedorUsername = this["merchantUsername"] as? String ?: "",
            valorBaseCentavos = (this["amountCents"] as? Number)?.toLong() ?: 0L,
            descricao = this["description"] as? String ?: "",
            status = this["status"] as? String ?: STATUS_PENDENTE,
            expiraEmMs = (this["expiresAtMs"] as? Number)?.toLong() ?: 0L,
        )
    }
}