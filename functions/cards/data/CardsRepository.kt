package com.example.zeca.cards.data

import com.example.zeca.FirebaseRepository
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class CobrancaMaquininha(
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
    val saldoBaseCentavos: Long?,
)

fun textoStatusCobranca(status: String): String = when (status) {
    "pending" -> "aguardando pagamento"
    "paid" -> "paga"
    "expired" -> "expirada"
    "cancelled" -> "cancelada"
    else -> status
}

object CardsRepository {
    const val PREFIXO_QR = "zeca://charge/"
    private val ID_VALIDO = Regex("^[a-f0-9-]{36}$", RegexOption.IGNORE_CASE)

    // Um requestId por tentativa: repetir a mesma tentativa devolve a mesma cobrança no servidor.
    fun novoRequestId(): String = UUID.randomUUID().toString()

    fun textoQr(cobrancaId: String): String = PREFIXO_QR + cobrancaId

    fun idDoQr(texto: String?): String? {
        val limpo = texto?.trim().orEmpty()
        if (!limpo.startsWith(PREFIXO_QR)) return null
        return limpo.removePrefix(PREFIXO_QR).takeIf { ID_VALIDO.matches(it) }
    }

    fun criarCobranca(
        requestId: String,
        valorBaseCentavos: Long,
        descricao: String,
        callback: (CobrancaMaquininha?, Exception?) -> Unit,
    ) {
        FirebaseRepository.chamarFunction(
            "createCharge",
            mapOf(
                "requestId" to requestId,
                "amountCents" to valorBaseCentavos,
                "description" to descricao,
            ),
        ) { data, erro -> callback(data?.toCobranca(), erro) }
    }

    fun buscarCobranca(cobrancaId: String, callback: (CobrancaMaquininha?, Exception?) -> Unit) {
        FirebaseRepository.chamarFunction("getCharge", mapOf("chargeId" to cobrancaId)) { data, erro ->
            callback(data?.toCobranca(), erro)
        }
    }

    suspend fun consultarCobranca(cobrancaId: String): Pair<CobrancaMaquininha?, Exception?> =
        suspendCoroutine { continuacao ->
            buscarCobranca(cobrancaId) { cobranca, erro -> continuacao.resume(cobranca to erro) }
        }

    // O chargeId fixa o operationId no servidor: repetir o pagamento nunca debita duas vezes.
    fun pagarCobranca(cobrancaId: String, callback: (ComprovantePagamento?, Exception?) -> Unit) {
        FirebaseRepository.chamarFunction("payCharge", mapOf("chargeId" to cobrancaId)) { data, erro ->
            val comprovante = data?.let {
                ComprovantePagamento(
                    cobrancaId = it["chargeId"] as? String ?: cobrancaId,
                    valorBaseCentavos = (it["amountCents"] as? Number)?.toLong() ?: 0L,
                    taxaBaseCentavos = (it["feeCents"] as? Number)?.toLong() ?: 0L,
                    descricao = it["description"] as? String ?: "",
                    saldoBaseCentavos = (it["balanceCents"] as? Number)?.toLong(),
                )
            }
            callback(comprovante, erro)
        }
    }

    private fun Map<String, Any>.toCobranca(): CobrancaMaquininha? {
        val id = this["chargeId"] as? String ?: return null
        return CobrancaMaquininha(
            id = id,
            recebedorUid = this["merchantUid"] as? String ?: "",
            recebedorNome = this["merchantName"] as? String ?: "Jogador",
            recebedorUsername = this["merchantUsername"] as? String ?: "",
            valorBaseCentavos = (this["amountCents"] as? Number)?.toLong() ?: 0L,
            descricao = this["description"] as? String ?: "",
            status = this["status"] as? String ?: "pending",
            expiraEmMs = (this["expiresAtMs"] as? Number)?.toLong() ?: 0L,
        )
    }
}