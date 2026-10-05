package com.example.zeca.cards.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

// Roda no celular do recebedor: responde ao toque com o chargeId da cobrança na tela.
// Sem cobrança ativa (ou com o app fechado), responde "não encontrado".
class CardEmulatorService : HostApduService() {

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || !NfcProtocolo.ehSelectDoAid(commandApdu)) {
            return NfcProtocolo.SW_COMANDO_INVALIDO
        }
        val chargeId = NfcProtocolo.chargeIdAtivo ?: return NfcProtocolo.SW_NAO_ENCONTRADO
        return chargeId.toByteArray(Charsets.UTF_8) + NfcProtocolo.SW_OK
    }

    override fun onDeactivated(reason: Int) = Unit
}