package com.example.zeca.cards.nfc

// Único lugar com as regras do toque: AID, códigos de resposta e leitura do chargeId.
internal object NfcProtocolo {
    // F0 + "ZECAPAY". Precisa ser igual ao aid-filter de res/xml/apdu_service.xml.
    val AID = byteArrayOf(0xF0.toByte(), 0x5A, 0x45, 0x43, 0x41, 0x50, 0x41, 0x59)
    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_NAO_ENCONTRADO = byteArrayOf(0x6A, 0x82.toByte())
    val SW_COMANDO_INVALIDO = byteArrayOf(0x6D, 0x00)

    private val CABECALHO_SELECT = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)
    private val ID_REGEX = Regex("^[a-f0-9-]{36}$", RegexOption.IGNORE_CASE)

    // Cobrança que o celular do recebedor está mostrando agora. Fica só na memória.
    @Volatile
    var chargeIdAtivo: String? = null

    fun comandoSelect(): ByteArray =
        CABECALHO_SELECT + byteArrayOf(AID.size.toByte()) + AID

    fun ehSelectDoAid(apdu: ByteArray): Boolean {
        if (apdu.size < 5 + AID.size) return false
        if (!apdu.copyOfRange(0, 4).contentEquals(CABECALHO_SELECT)) return false
        if ((apdu[4].toInt() and 0xFF) != AID.size) return false
        return apdu.copyOfRange(5, 5 + AID.size).contentEquals(AID)
    }

    // Devolve o chargeId ou lança IllegalArgumentException com mensagem para a tela.
    fun lerResposta(resposta: ByteArray): String {
        require(resposta.size >= 2) { "Resposta vazia. Tente encostar de novo." }
        val status = resposta.copyOfRange(resposta.size - 2, resposta.size)
        require(status.contentEquals(SW_OK)) { "Esse aparelho não tem cobrança ativa." }
        val texto = String(resposta, 0, resposta.size - 2, Charsets.UTF_8)
        require(ID_REGEX.matches(texto)) { "Cobrança inválida." }
        return texto
    }
}