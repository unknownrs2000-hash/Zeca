package com.example.zeca.cards.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Handler
import android.os.Looper

// Roda no celular do pagador: lê o chargeId do aparelho do recebedor.
internal object NfcLeitor {

    fun existe(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context) != null

    fun ligado(context: Context): Boolean = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true

    // aoLer e aoFalhar são chamados na thread principal.
    fun iniciar(activity: Activity, aoLer: (String) -> Unit, aoFalhar: (String) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val principal = Handler(Looper.getMainLooper())
        adapter.enableReaderMode(
            activity,
            { tag ->
                val resultado = lerChargeId(tag)
                principal.post {
                    resultado.fold(
                        onSuccess = { aoLer(it) },
                        onFailure = { aoFalhar(it.message ?: "Não consegui ler o toque.") },
                    )
                }
            },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    fun parar(activity: Activity) {
        NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity)
    }

    private fun lerChargeId(tag: Tag): Result<String> = runCatching {
        val iso = IsoDep.get(tag) ?: throw IllegalArgumentException("Esse toque não é de um aparelho Zeca.")
        iso.use {
            it.connect()
            it.timeout = 3000
            NfcProtocolo.lerResposta(it.transceive(NfcProtocolo.comandoSelect()))
        }
    }
}