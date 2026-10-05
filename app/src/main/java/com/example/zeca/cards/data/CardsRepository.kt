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

const val CARTAO_ATIVO = "active"
const val CARTAO_BLOQUEADO = "blocked"
const val CARTAO_CANCELADO = "cancelled"

data class Cartao(
    val id: String,
    val rotulo: String,
    val final4: String,
    val status: String,
    val travado: Boolean,
    val travadoAteMs: Long,
)

data class PagamentoCartao(
    val cobrancaId: String,
    val valorBaseCentavos: Long,
    val descricao: String,
    val pagoEmMs: Long,
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

    fun pagarCobranca(
        cobrancaId: String,
        cartaoId: String? = null,
        pin: String? = null,
        callback: (ComprovantePagamento?, Exception?) -> Unit,
    ) {
        val parametros = mutableMapOf<String, Any>("chargeId" to cobrancaId)
        if (cartaoId != null && pin != null) {
            parametros["cardId"] = cartaoId
            parametros["pin"] = pin
        }
        FirebaseRepository.chamarFunction("payCharge", parametros) { data, erro ->
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

    fun listarCartoes(callback: (List<Cartao>?, Exception?) -> Unit) {
        FirebaseRepository.chamarFunction("listCards", emptyMap<String, Any>()) { data, erro ->
            if (erro != null || data == null) {
                callback(null, erro ?: IllegalStateException("Resposta dos cartões inválida."))
                return@chamarFunction
            }
            val lista = (data["cards"] as? List<*>).orEmpty()
                .mapNotNull { (it as? Map<*, *>)?.let { mapa -> mapa.toCartao() } }
            callback(lista, null)
        }
    }

    fun criarCartao(
        requestId: String,
        pin: String,
        rotulo: String,
        callback: (Cartao?, Exception?) -> Unit,
    ) {
        FirebaseRepository.chamarFunction(
            "createCard",
            mapOf("requestId" to requestId, "pin" to pin, "label" to rotulo),
        ) { data, erro ->
            val cartao = data?.toCartao()
            callback(
                cartao,
                erro ?: if (cartao == null) IllegalStateException("Resposta do cartão inválida.") else null,
            )
        }
    }

    fun definirStatusCartao(cartaoId: String, status: String, callback: (Exception?) -> Unit) {
        FirebaseRepository.chamarFunction(
            "setCardStatus",
            mapOf("cardId" to cartaoId, "status" to status),
        ) { _, erro -> callback(erro) }
    }

    fun trocarPin(cartaoId: String, pinAtual: String, pinNovo: String, callback: (Exception?) -> Unit) {
        FirebaseRepository.chamarFunction(
            "changeCardPin",
            mapOf("cardId" to cartaoId, "currentPin" to pinAtual, "newPin" to pinNovo),
        ) { _, erro -> callback(erro) }
    }

    fun historicoCartao(cartaoId: String, callback: (List<PagamentoCartao>?, Exception?) -> Unit) {
        FirebaseRepository.chamarFunction("getCardHistory", mapOf("cardId" to cartaoId)) { data, erro ->
            if (erro != null || data == null) {
                callback(null, erro ?: IllegalStateException("Resposta do histórico inválida."))
                return@chamarFunction
            }
            val lista = (data["payments"] as? List<*>).orEmpty().mapNotNull { item ->
                val mapa = item as? Map<*, *> ?: return@mapNotNull null
                PagamentoCartao(
                    cobrancaId = mapa["chargeId"] as? String ?: return@mapNotNull null,
                    valorBaseCentavos = (mapa["amountCents"] as? Number)?.toLong() ?: 0L,
                    descricao = mapa["description"] as? String ?: "",
                    pagoEmMs = (mapa["paidAtMs"] as? Number)?.toLong() ?: 0L,
                )
            }
            callback(lista, null)
        }
    }

    private fun Map<*, *>.toCartao(): Cartao? {
        val id = this["cardId"] as? String ?: return null
        return Cartao(
            id = id,
            rotulo = this["label"] as? String ?: "Cartão Zeca",
            final4 = this["last4"] as? String ?: "0000",
            status = this["status"] as? String ?: CARTAO_ATIVO,
            travado = this["locked"] as? Boolean ?: false,
            travadoAteMs = (this["lockedUntilMs"] as? Number)?.toLong() ?: 0L,
        )
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