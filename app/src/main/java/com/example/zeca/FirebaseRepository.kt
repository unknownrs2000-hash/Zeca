package com.example.zeca

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import android.content.ContentResolver
import android.net.Uri

data class PerfilJogador(
    val uid: String,
    val apelido: String,
    val email: String,
    val username: String,
    val profileSetupComplete: Boolean,
    val saldoCentavos: Long,
    val nivel: Int,
    val avatarUrl: String,
    val chavePix: String,
    val tipoChavePix: String,
    val partidas: Int,
    val vitorias: Int,
    val inventario: List<String>,
    val ganhoTotalCentavos: Long,
    val perdaTotalCentavos: Long,
    val molduraEquipada: String,
    val avatarItensEquipados: List<String> = emptyList(),
    val avatarComoFotoPerfil: Boolean = false,
)

data class JogadorRanking(
    val uid: String,
    val apelido: String,
    val saldoCentavos: Long,
    val nivel: Int,
    val avatarUrl: String,
    val username: String = "",
    val avatarItensEquipados: List<String> = emptyList(),
    val avatarComoFotoPerfil: Boolean = false,
)

data class MensagemChat(
    val id: String,
    val autorUid: String,
    val autor: String,
    val texto: String,
    val minha: Boolean,
    val enviadaEmMs: Long,
    val respostaId: String = "",
    val respostaAutor: String = "",
    val respostaTexto: String = "",
    val apagadaParaTodos: Boolean = false,
    val encaminhada: Boolean = false,
    val usernameAutor: String = "",
    val avatarUrlAutor: String = "",
    val avatarItensAutor: List<String> = emptyList(),
    val avatarComoFotoAutor: Boolean = false,
)

data class RespostaChat(
    val id: String,
    val autor: String,
    val texto: String,
)

data class ConversaChat(
    val id: String,
    val outroUid: String,
    val ultimaMensagem: String,
    val atualizadaEmMs: Long,
    val ultimaMensagemId: String = "",
    val ultimoRemetenteUid: String = "",
    val tipo: String = "direct",
    val nome: String = "",
    val descricao: String = "",
    val criadoPorUid: String = "",
    val politicaEditar: String = "creator",
    val politicaEnviar: String = "everyone",
    val participantes: List<String> = emptyList(),
    val diasFoguinho: Int = 0,
    val nivelFoguinho: Int = 0,
    val nomeFoguinho: String = "Nosso foguinho",
    val ultimaSequenciaUtc: String = "",
)

data class JogadorDestino(
    val uid: String,
    val apelido: String,
    val nivel: Int,
    val avatarUrl: String,
    val avatarItensEquipados: List<String> = emptyList(),
    val avatarComoFotoPerfil: Boolean = false,
)

data class PerfilPublico(
    val uid: String,
    val apelido: String,
    val username: String,
    val nivel: Int,
    val avatarUrl: String,
    val saldoCentavos: Long,
    val partidas: Int,
    val vitorias: Int,
    val inventario: List<String>,
    val chavePix: String,
    val tipoChavePix: String,
    val molduraEquipada: String,
    val avatarItensEquipados: List<String> = emptyList(),
    val avatarComoFotoPerfil: Boolean = false,
)

data class ResultadoTransferencia(
    val id: String,
    val nomeDestino: String,
    val saldoCentavos: Long,
)

data class ResultadoJogo(
    val resultado: String,
    val variacaoCentavos: Long,
    val saldoCentavos: Long,
    val partidas: Int,
    val vitorias: Int,
    val multiplicador: Int,
)

data class SessaoCrash(
    val gameId: String,
    val apostaCentavos: Long,
    val iniciadoEmMs: Long,
    val retomada: Boolean,
)

data class ResultadoCrash(
    val caiu: Boolean,
    val multiplicadorBps: Int,
    val premioCentavos: Long,
    val lucroCentavos: Long,
    val saldoCentavos: Long,
    val partidas: Int,
    val vitorias: Int,
)

data class CartaBlackjack(val rank: String, val suit: String)

data class EstadoBlackjack(
    val gameId: String,
    val status: String,
    val cartasJogador: List<CartaBlackjack>,
    val cartasDealer: List<CartaBlackjack>,
    val cartaOculta: Boolean,
    val apostaCentavos: Long,
    val resultado: String,
    val premioCentavos: Long,
    val lucroCentavos: Long,
    val saldoCentavos: Long,
)

object FirebaseRepository {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val database by lazy { FirebaseFirestore.getInstance() }
    private const val SERVER_URL = "https://zeca-jvic.onrender.com"
    private const val CLOUDINARY_CLOUD_NAME = "vwctfu9u"
    private const val CLOUDINARY_UPLOAD_PRESET = "zeca_unsigned"
    private const val REQUEST_TIMEOUT_MS = 30_000L
    private val principal = android.os.Handler(android.os.Looper.getMainLooper())

    fun garantirPerfil(user: FirebaseUser, callback: (Exception?) -> Unit) {
        val userRef = database.collection("users").document(user.uid)
        val rankRef = database.collection("leaderboard").document(user.uid)
        val nomeConta = (user.displayName ?: user.email?.substringBefore('@') ?: "Jogador")
            .trim()
            .take(24)
            .let { if (it.length >= 2) it else "Jogador" }
        database.runTransaction { transaction ->
            val userSnapshot = transaction.get(userRef)
            val rankSnapshot = transaction.get(rankRef)
            if (!userSnapshot.exists()) {
                val profile = mapOf(
                    "uid" to user.uid,
                    "displayName" to nomeConta,
                    "username" to "",
                    "profileSetupComplete" to false,
                    "email" to (user.email ?: ""),
                    "balanceCents" to 50_000L,
                    "balanceInitialized" to true,
                    "level" to 1L,
                    "avatarUrl" to (user.photoUrl?.toString() ?: ""),
                    "avatarAsProfilePhoto" to false,
                    "equippedAvatarItems" to emptyList<String>(),
                    "pixKey" to "",
                    "pixKeyType" to "",
                    "pixKeyHash" to "",
                    "gamesPlayed" to 0L,
                    "wins" to 0L,
                    "inventory" to emptyList<String>(),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                )
                transaction.set(userRef, profile)
                transaction.set(
                    rankRef,
                    mapOf(
                        "displayName" to nomeConta,
                        "username" to "",
                        "balanceCents" to 50_000L,
                        "level" to 1L,
                        "avatarUrl" to (user.photoUrl?.toString() ?: ""),
                        "avatarAsProfilePhoto" to false,
                        "equippedAvatarItems" to emptyList<String>(),
                    ),
                )
            } else {
                val profile = userSnapshot.data ?: emptyMap()
                val balance = (profile["balanceCents"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
                val initialized = profile["balanceInitialized"] == true
                val gamesPlayed = (profile["gamesPlayed"] as? Number)?.toLong() ?: 0L
                val wins = (profile["wins"] as? Number)?.toLong() ?: 0L
                val canReceiveStartingBalance = !initialized && balance == 0L && gamesPlayed == 0L && wins == 0L
                val nextBalance = if (canReceiveStartingBalance) 50_000L else balance
                val updates = mutableMapOf<String, Any>()
                if (!initialized || nextBalance != balance) {
                    updates["balanceInitialized"] = true
                    updates["balanceCents"] = nextBalance
                }
                val profileName = profile["displayName"] as? String ?: nomeConta
                val username = profile["username"] as? String ?: ""
                val profileLevel = (profile["level"] as? Number)?.toLong()?.takeIf { it > 0L } ?: 1L
                val profileAvatar = (profile["avatarUrl"] as? String).orEmpty()
                    .ifBlank { user.photoUrl?.toString().orEmpty() }
                val avatarAsProfilePhoto = profile["avatarAsProfilePhoto"] as? Boolean ?: false
                val equippedAvatarItems = (profile["equippedAvatarItems"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                if ("avatarAsProfilePhoto" !in profile) updates["avatarAsProfilePhoto"] = false
                if ("equippedAvatarItems" !in profile) updates["equippedAvatarItems"] = emptyList<String>()
                if (updates.isNotEmpty()) transaction.update(userRef, updates)
                val publicProfile = mapOf(
                    "displayName" to profileName,
                    "username" to username,
                    "balanceCents" to nextBalance,
                    "level" to profileLevel,
                    "avatarUrl" to profileAvatar,
                    "avatarAsProfilePhoto" to avatarAsProfilePhoto,
                    "equippedAvatarItems" to equippedAvatarItems,
                )
                if (!rankSnapshot.exists()) transaction.set(rankRef, publicProfile)
                else transaction.update(
                    rankRef,
                    mapOf(
                        "displayName" to profileName,
                        "username" to username,
                        "balanceCents" to nextBalance,
                        "level" to profileLevel,
                        "avatarUrl" to profileAvatar,
                        "avatarAsProfilePhoto" to avatarAsProfilePhoto,
                        "equippedAvatarItems" to equippedAvatarItems,
                    ),
                )
            }
            null
        }
            .addOnSuccessListener {
                callback(null)
                if (!user.photoUrl?.toString().isNullOrBlank()) {
                    chamarFunction("ensurePlayerProfile", emptyMap()) { _, _ -> }
                }
            }
            .addOnFailureListener {
                chamarFunction("ensurePlayerProfile", emptyMap()) { _, fallbackError ->
                    callback(fallbackError?.let(::erroParaUsuario))
                }
            }
    }

    fun observarPerfil(uid: String, callback: (PerfilJogador?) -> Unit): ListenerRegistration =
        database.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            callback(snapshot?.let(::toPerfil))
        }

    fun observarRanking(callback: (List<JogadorRanking>) -> Unit): ListenerRegistration =
        database.collection("leaderboard")
            .orderBy("balanceCents", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                callback(snapshot?.documents.orEmpty().mapNotNull(::toJogadorRanking))
            }

    fun observarMensagensChat(
        chatId: String,
        uidAtual: String,
        callback: (List<MensagemChat>, Exception?, Boolean) -> Unit,
    ): ListenerRegistration = database.collection("chats").document(chatId).collection("messages")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(80)
        .addSnapshotListener { snapshot, error ->
            val mensagens = snapshot?.documents.orEmpty().mapNotNull { document ->
                val data = document.data ?: return@mapNotNull null
                val ocultaPara = (data["deletedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (uidAtual in ocultaPara) return@mapNotNull null
                MensagemChat(
                    id = document.id,
                    autorUid = data["senderUid"] as? String ?: return@mapNotNull null,
                    autor = data["senderName"] as? String ?: "Jogador",
                    texto = data["text"] as? String ?: return@mapNotNull null,
                    minha = data["senderUid"] == uidAtual,
                    enviadaEmMs = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
                    respostaId = data["replyToId"] as? String ?: "",
                    respostaAutor = data["replyToName"] as? String ?: "",
                    respostaTexto = data["replyToText"] as? String ?: "",
                    apagadaParaTodos = data["deletedForAll"] as? Boolean ?: false,
                    encaminhada = data["forwarded"] as? Boolean ?: false,
                    usernameAutor = data["senderUsername"] as? String ?: "",
                    avatarUrlAutor = data["senderAvatarUrl"] as? String ?: "",
                    avatarItensAutor = (data["senderAvatarItems"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    avatarComoFotoAutor = data["senderAvatarAsProfilePhoto"] as? Boolean ?: false,
                )
            }.sortedBy { it.enviadaEmMs }
            callback(mensagens, error, snapshot?.metadata?.isFromCache ?: true)
        }

    fun observarConversasChat(
        uid: String,
        callback: (List<ConversaChat>, Exception?, Boolean) -> Unit,
    ): ListenerRegistration =
        database.collection("chats")
            .whereArrayContains("participantUids", uid)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                val conversas = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val participantes = (document.get("participantUids") as? List<*>)
                        ?.filterIsInstance<String>() ?: return@mapNotNull null
                    if (uid !in participantes) return@mapNotNull null
                    ConversaChat(
                        id = document.id,
                        outroUid = participantes.firstOrNull { it != uid }.orEmpty(),
                        ultimaMensagem = document.getString("lastMessage") ?: "",
                        atualizadaEmMs = document.getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L,
                        ultimaMensagemId = document.getString("lastMessageId") ?: "",
                        ultimoRemetenteUid = document.getString("lastMessageSenderUid") ?: "",
                        tipo = document.getString("type") ?: "direct",
                        nome = document.getString("name") ?: "",
                        descricao = document.getString("description") ?: "",
                        criadoPorUid = document.getString("createdBy") ?: "",
                        politicaEditar = document.getString("editPolicy") ?: "creator",
                        politicaEnviar = document.getString("sendPolicy") ?: "everyone",
                        participantes = participantes,
                        diasFoguinho = document.getLong("streakDays")?.toInt() ?: 0,
                        nivelFoguinho = document.getLong("streakLevel")?.toInt() ?: 0,
                        nomeFoguinho = document.getString("streakName") ?: "Nosso foguinho",
                        ultimaSequenciaUtc = document.getString("streakLastQualifiedDate") ?: "",
                    )
                }.sortedByDescending { it.atualizadaEmMs }
                callback(conversas, error, snapshot?.metadata?.isFromCache ?: true)
            }

    fun idConversaPrivada(uidUm: String, uidDois: String): String = listOf(uidUm, uidDois).sorted().joinToString("_")

    fun enviarMensagemChat(
        destinatarioUid: String?,
        texto: String,
        requestId: String,
        resposta: RespostaChat?,
        encaminhada: Boolean = false,
        chatId: String? = null,
        callback: (String?, Exception?) -> Unit,
    ) {
        val message = texto.trim()
        if (message.isEmpty() || message.length > 500) {
            callback(null, IllegalArgumentException("A mensagem deve ter entre 1 e 500 caracteres."))
            return
        }
        val dados = mutableMapOf<String, Any>("text" to message, "requestId" to requestId)
        destinatarioUid?.let { dados["recipientUid"] = it }
        chatId?.let { dados["chatId"] = it }
        resposta?.let { dados["reply"] = mapOf("id" to it.id, "name" to it.autor, "text" to it.texto.take(200)) }
        if (encaminhada) dados["forwarded"] = true
        chamarFunction("sendChatMessage", dados) { result, error ->
            callback(result?.get("chatId") as? String, error)
        }
        }

    fun criarGrupo(
        nome: String,
        descricao: String,
        memberUids: List<String>,
        editPolicy: String,
        sendPolicy: String,
        requestId: String,
        callback: (String?, Exception?) -> Unit,
    ) {
        chamarFunction(
            "createChatGroup",
            mapOf(
                "name" to nome,
                "description" to descricao,
                "memberUids" to memberUids,
                "editPolicy" to editPolicy,
                "sendPolicy" to sendPolicy,
                "requestId" to requestId,
            ),
        ) { data, error -> callback(data?.get("chatId") as? String, error) }
    }

    fun atualizarGrupo(
        chatId: String,
        nome: String,
        descricao: String,
        editPolicy: String,
        sendPolicy: String,
        callback: (Exception?) -> Unit,
    ) {
        chamarFunction(
            "updateChatGroup",
            mapOf(
                "chatId" to chatId,
                "name" to nome,
                "description" to descricao,
                "editPolicy" to editPolicy,
                "sendPolicy" to sendPolicy,
            ),
        ) { _, error -> callback(error) }
    }

    fun renomearFoguinho(chatId: String, nome: String, callback: (Exception?) -> Unit) {
        chamarFunction("renameChatFlame", mapOf("chatId" to chatId, "name" to nome)) { _, error -> callback(error) }
    }

    fun apagarMensagemParaMim(chatId: String, mensagemId: String, callback: (Exception?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(IllegalStateException("Entre na sua conta novamente."))
            return
        }
        database.collection("chats").document(chatId).collection("messages").document(mensagemId)
            .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(uid))
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(erroParaUsuario(it)) }
    }

    fun apagarMensagemParaTodos(chatId: String, mensagemId: String, callback: (Exception?) -> Unit) {
        if (auth.currentUser == null) {
            callback(IllegalStateException("Entre na sua conta novamente."))
            return
        }
        database.collection("chats").document(chatId).collection("messages").document(mensagemId)
            .update(mapOf("deletedForAll" to true, "text" to ""))
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(erroParaUsuario(it)) }
    }

    fun observarMovimentos(uid: String, callback: (List<Movimento>, Boolean) -> Unit): ListenerRegistration =
        database.collection("users").document(uid).collection("transactions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                callback(snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Movimento(
                        titulo = data["description"] as? String ?: "Movimentação",
                        variacaoCentavos = (data["deltaCents"] as? Number)?.toLong() ?: 0L,
                        horario = (data["createdAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.let { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.forLanguageTag("pt-BR")).format(it) }
                            ?: "Agora",
                        id = doc.id,
                        ehTransferenciaPix = data["type"] == "pix_transfer",
                        ehPremioNivel = data["type"] == "level_reward",
                    )
                }, snapshot.metadata.isFromCache)
            }

    fun atualizarPerfil(
        username: String,
        displayName: String,
        avatarUrl: String,
        avatarComoFotoPerfil: Boolean,
        callback: (Exception?) -> Unit,
    ) {
        chamarFunction(
            "updatePlayerProfile",
            mapOf(
                "username" to username,
                "displayName" to displayName,
                "avatarUrl" to avatarUrl,
                "avatarAsProfilePhoto" to avatarComoFotoPerfil,
            ),
        ) { _, erro -> callback(erro) }
    }

    fun equiparItemAvatar(slot: String, itemId: String?, callback: (Exception?) -> Unit) {
        chamarFunction(
            "equipAvatarItem",
            mapOf("slot" to slot, "itemId" to (itemId ?: "")),
        ) { _, error -> callback(error) }
    }

    fun enviarFotoPerfil(uri: Uri, contentResolver: ContentResolver, callback: (String?, Exception?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            callback(null, IllegalStateException("Entre na sua conta novamente."))
            return
        }
        Thread {
            var urlFoto: String? = null
            var erro: Exception? = null
            try {
                val mimeType = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") }
                    ?: "image/jpeg"
                val extensao = mimeType.substringAfter('/', "jpeg").substringBefore('+')
                val boundary = "----ZecaUpload${java.util.UUID.randomUUID()}"
                val conexao = java.net.URL(
                    "https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload",
                ).openConnection() as java.net.HttpURLConnection
                try {
                    conexao.requestMethod = "POST"
                    conexao.connectTimeout = 15_000
                    conexao.readTimeout = 60_000
                    conexao.doOutput = true
                    conexao.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    conexao.outputStream.use { output ->
                        val writer = output.bufferedWriter(Charsets.UTF_8)
                        writer.append("--$boundary\r\n")
                        writer.append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
                        writer.append("$CLOUDINARY_UPLOAD_PRESET\r\n")
                        writer.append("--$boundary\r\n")
                        writer.append("Content-Disposition: form-data; name=\"folder\"\r\n\r\n")
                        writer.append("zeca/avatars/$uid\r\n")
                        writer.append("--$boundary\r\n")
                        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"avatar.$extensao\"\r\n")
                        writer.append("Content-Type: $mimeType\r\n\r\n")
                        writer.flush()
                        contentResolver.openInputStream(uri)?.use { input -> input.copyTo(output) }
                            ?: throw IllegalStateException("Não foi possível abrir a imagem escolhida.")
                        output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                    val status = conexao.responseCode
                    val fluxo = if (status in 200..299) conexao.inputStream else conexao.errorStream
                    val resposta = fluxo?.bufferedReader()?.use { it.readText() }.orEmpty()
                    val json = runCatching { org.json.JSONObject(resposta) }.getOrNull()
                    if (status !in 200..299) {
                        val detalhe = json?.optJSONObject("error")?.optString("message").orEmpty()
                        throw IllegalStateException(detalhe.ifBlank { "O Cloudinary recusou o upload ($status)." })
                    }
                    urlFoto = json?.optString("secure_url")?.takeIf { it.startsWith("https://res.cloudinary.com/") }
                        ?: throw IllegalStateException("O Cloudinary não retornou uma URL segura para a imagem.")
                } finally {
                    conexao.disconnect()
                }
            } catch (exception: Exception) {
                erro = exception
            }
            principal.post { callback(urlFoto, erro) }
        }.start()
    }

    fun registrarChavePix(tipo: String, chave: String, callback: (Exception?) -> Unit) {
        chamarFunction("registerPixKey", mapOf("type" to tipo, "key" to chave.trim())) { _, erro -> callback(erro) }
    }

    fun reenviarVerificacaoEmail(callback: (Exception?) -> Unit) {
        val usuario = auth.currentUser
        if (usuario == null) {
            callback(IllegalStateException("Entre na sua conta novamente."))
            return
        }
        usuario.sendEmailVerification()
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(erroParaUsuario(it)) }
    }

    fun conferirEmailVerificado(callback: (Boolean, Exception?) -> Unit) {
        val usuario = auth.currentUser
        if (usuario == null) {
            callback(false, IllegalStateException("Entre na sua conta novamente."))
            return
        }
        usuario.reload()
            .addOnSuccessListener { callback(auth.currentUser?.isEmailVerified == true, null) }
            .addOnFailureListener { callback(false, erroParaUsuario(it)) }
    }

    fun buscarChavePix(chave: String, callback: (JogadorDestino?, Exception?) -> Unit) {
        chamarFunction("lookupPixKey", mapOf("key" to chave.trim())) { data, erro ->
            val destino = data?.let {
                JogadorDestino(
                    uid = it["uid"] as? String ?: return@let null,
                    apelido = it["displayName"] as? String ?: "Jogador",
                    nivel = (it["level"] as? Number)?.toInt() ?: 1,
                    avatarUrl = it["avatarUrl"] as? String ?: "",
                    avatarItensEquipados = (it["equippedAvatarItems"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    avatarComoFotoPerfil = it["avatarAsProfilePhoto"] as? Boolean ?: false,
                )
            }
            callback(destino, erro)
        }
    }

    fun buscarPerfilPublico(uid: String, callback: (PerfilPublico?, Exception?) -> Unit) {
        chamarFunction("getPlayerProfile", mapOf("uid" to uid)) { data, erro ->
            val perfil = data?.let {
                PerfilPublico(
                    uid = it["uid"] as? String ?: uid,
                    apelido = it["displayName"] as? String ?: "Jogador",
                    username = it["username"] as? String ?: "",
                    nivel = (it["level"] as? Number)?.toInt() ?: 1,
                    avatarUrl = it["avatarUrl"] as? String ?: "",
                    saldoCentavos = (it["balanceCents"] as? Number)?.toLong() ?: 0L,
                    partidas = (it["gamesPlayed"] as? Number)?.toInt() ?: 0,
                    vitorias = (it["wins"] as? Number)?.toInt() ?: 0,
                    inventario = (it["inventory"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    chavePix = it["pixKey"] as? String ?: "",
                    tipoChavePix = it["pixKeyType"] as? String ?: "",
                    molduraEquipada = it["equippedFrame"] as? String ?: "",
                    avatarItensEquipados = (it["equippedAvatarItems"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    avatarComoFotoPerfil = it["avatarAsProfilePhoto"] as? Boolean ?: false,
                )
            }
            callback(perfil, erro)
        }
    }

    fun transferirPorPix(chave: String, valorCentavos: Long, requestId: String, callback: (ResultadoTransferencia?, Exception?) -> Unit) {
        chamarFunction(
            "transferByPixKey",
            mapOf("key" to chave.trim(), "amountCents" to valorCentavos, "requestId" to requestId),
        ) { data, erro ->
            val resultado = data?.let {
                ResultadoTransferencia(
                    id = it["transferId"] as? String ?: requestId,
                    nomeDestino = it["recipientName"] as? String ?: "Jogador",
                    saldoCentavos = (it["balanceCents"] as? Number)?.toLong() ?: 0L,
                )
            }
            callback(resultado, erro)
        }
    }

    fun jogar(
        jogo: String,
        apostaCentavos: Long,
        tipoAposta: String,
        selecao: String,
        requestId: String,
        callback: (ResultadoJogo?, Exception?) -> Unit,
    ) {
        chamarFunction(
            "playGame",
            mapOf(
                "game" to jogo,
                "amountCents" to apostaCentavos,
                "betType" to tipoAposta,
                "selection" to selecao,
                "requestId" to requestId,
            ),
        ) { data, erro ->
            val rawResult = data?.get("result")
            val resultText = when (rawResult) {
                is List<*> -> rawResult.joinToString("     ")
                is Map<*, *> -> "${rawResult["number"]} · ${rawResult["color"]}"
                else -> ""
            }
            val resultado = data?.let {
                ResultadoJogo(
                    resultado = resultText,
                    variacaoCentavos = (it["deltaCents"] as? Number)?.toLong() ?: 0L,
                    saldoCentavos = (it["balanceCents"] as? Number)?.toLong() ?: 0L,
                    partidas = (it["gamesPlayed"] as? Number)?.toInt() ?: 0,
                    vitorias = (it["wins"] as? Number)?.toInt() ?: 0,
                    multiplicador = (it["payoutMultiplier"] as? Number)?.toInt() ?: 0,
                )
            }
            callback(resultado, erro)
        }
    }

    fun iniciarCrash(apostaCentavos: Long, requestId: String, callback: (SessaoCrash?, Exception?) -> Unit) {
        chamarFunction("startCrash", mapOf("amountCents" to apostaCentavos, "requestId" to requestId)) { data, erro ->
            val sessao = data?.let {
                SessaoCrash(
                    gameId = it["gameId"] as? String ?: return@let null,
                    apostaCentavos = (it["amountCents"] as? Number)?.toLong() ?: apostaCentavos,
                    iniciadoEmMs = (it["startedAtMs"] as? Number)?.toLong() ?: 0L,
                    retomada = it["resumed"] as? Boolean ?: false,
                )
            }
            callback(sessao, erro)
        }
    }

    fun sacarCrash(gameId: String, requestId: String, callback: (ResultadoCrash?, Exception?) -> Unit) {
        chamarFunction("cashOutCrash", mapOf("gameId" to gameId, "requestId" to requestId)) { data, erro ->
            val resultado = data?.let {
                ResultadoCrash(
                    caiu = it["crashed"] as? Boolean ?: false,
                    multiplicadorBps = (it["multiplierBps"] as? Number)?.toInt() ?: 100,
                    premioCentavos = (it["payoutCents"] as? Number)?.toLong() ?: 0L,
                    lucroCentavos = (it["profitCents"] as? Number)?.toLong() ?: 0L,
                    saldoCentavos = (it["balanceCents"] as? Number)?.toLong() ?: 0L,
                    partidas = (it["gamesPlayed"] as? Number)?.toInt() ?: 0,
                    vitorias = (it["wins"] as? Number)?.toInt() ?: 0,
                )
            }
            callback(resultado, erro)
        }
    }

    fun iniciarBlackjack(apostaCentavos: Long, requestId: String, callback: (EstadoBlackjack?, Exception?) -> Unit) {
        chamarFunction("startBlackjack", mapOf("amountCents" to apostaCentavos, "requestId" to requestId)) { data, erro ->
            callback(data?.toEstadoBlackjack(), erro)
        }
    }

    fun acaoBlackjack(
        gameId: String,
        acao: String,
        requestId: String,
        callback: (EstadoBlackjack?, Exception?) -> Unit,
    ) {
        chamarFunction(
            "blackjackAction",
            mapOf("gameId" to gameId, "action" to acao, "requestId" to requestId),
        ) { data, erro -> callback(data?.toEstadoBlackjack(), erro) }
    }

    fun comprarCosmetico(itemId: String, callback: (String?) -> Unit) {
        if (itemId.isBlank()) {
            callback("Este item não está disponível.")
            return
        }
        chamarFunction("buyCosmetic", mapOf("itemId" to itemId)) { _, error ->
            callback(error?.localizedMessage)
        }
    }

    fun equiparMoldura(itemId: String, callback: (Exception?) -> Unit) {
        chamarFunction("equipFrame", mapOf("itemId" to itemId)) { _, erro -> callback(erro) }
    }

    fun sair() = auth.signOut()

    private fun chamarFunction(
        nome: String,
        dados: Map<String, Any>,
        callback: (Map<String, Any>?, Exception?) -> Unit,
    ) {
        val callbackConcluido = java.util.concurrent.atomic.AtomicBoolean(false)
        val timeout = Runnable {
            if (callbackConcluido.compareAndSet(false, true)) {
                callback(
                    null,
                    IllegalStateException(
                        "A solicitação demorou para confirmar. Confira seu saldo e coleção antes de tentar novamente.",
                    ),
                )
            }
        }
        principal.postDelayed(timeout, REQUEST_TIMEOUT_MS)
        fun concluir(data: Map<String, Any>?, error: Exception?) {
            if (callbackConcluido.compareAndSet(false, true)) {
                principal.removeCallbacks(timeout)
                callback(data, error)
            }
        }

        val usuario = auth.currentUser
        if (usuario == null) {
            concluir(null, IllegalStateException("Entre na sua conta para continuar."))
            return
        }
        usuario.getIdToken(false)
            .addOnSuccessListener { tokenResult ->
                if (callbackConcluido.get()) return@addOnSuccessListener
                Thread {
                    var dadosRetorno: Map<String, Any>? = null
                    var erroRetorno: Exception? = null
                    try {
                        dadosRetorno = postarNoServidor(nome, dados, tokenResult.token.orEmpty())
                    } catch (e: java.net.SocketTimeoutException) {
                        erroRetorno = IllegalStateException(
                            "O servidor demorou para responder. Tente de novo.", e,
                        )
                    } catch (e: java.io.IOException) {
                        erroRetorno = IllegalStateException(
                            "Não foi possível conectar ao servidor. Tente de novo em instantes.", e,
                        )
                    } catch (e: Exception) {
                        erroRetorno = e
                    }
                    principal.post { concluir(dadosRetorno, erroRetorno) }
                }.start()
            }
            .addOnFailureListener { erro -> concluir(null, erroParaUsuario(erro)) }
    }

    private fun postarNoServidor(nome: String, dados: Map<String, Any>, token: String): Map<String, Any>? {
        val conexao = java.net.URL("$SERVER_URL/call/$nome").openConnection() as java.net.HttpURLConnection
        try {
            conexao.requestMethod = "POST"
            conexao.connectTimeout = 10_000
            conexao.readTimeout = 15_000
            conexao.doOutput = true
            conexao.setRequestProperty("Content-Type", "application/json")
            conexao.setRequestProperty("Authorization", "Bearer $token")
            val corpo = org.json.JSONObject().put("data", org.json.JSONObject(dados)).toString()
            conexao.outputStream.use { it.write(corpo.toByteArray()) }

            val status = conexao.responseCode
            val fluxo = if (status in 200..299) conexao.inputStream else conexao.errorStream
            val texto = fluxo?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { org.json.JSONObject(texto) }.getOrNull()

            if (status !in 200..299) {
                val mensagem = json?.optJSONObject("error")?.optString("message").orEmpty()
                if (status == 404 && (mensagem.contains("função não encontrada", ignoreCase = true)
                        || mensagem.contains("function not found", ignoreCase = true))) {
                    throw IllegalStateException(
                        "O backend hospedado em zeca-jvic.onrender.com está desatualizado. Publique a versão atual da pasta functions/ no serviço.",
                    )
                }
                throw IllegalStateException(mensagem.ifBlank { "Erro no servidor ($status)." })
            }
            @Suppress("UNCHECKED_CAST")
            return paraValor(json?.opt("result")) as? Map<String, Any>
        } finally {
            conexao.disconnect()
        }
    }

    private fun paraValor(valor: Any?): Any? = when (valor) {
        null, org.json.JSONObject.NULL -> null
        is org.json.JSONObject -> {
            val mapa = HashMap<String, Any>()
            valor.keys().forEach { chave -> paraValor(valor.get(chave))?.let { mapa[chave] = it } }
            mapa
        }
        is org.json.JSONArray -> (0 until valor.length()).map { paraValor(valor.get(it)) }
        else -> valor
    }

    private fun erroParaUsuario(erro: Exception): Exception {
        if (erro is FirebaseFirestoreException && erro.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return IllegalStateException(
                "O Firestore bloqueou a gravação. Publique as regras atualizadas do arquivo firestore.rules.",
                erro,
            )
        }
        if (erro !is FirebaseFunctionsException || erro.code != FirebaseFunctionsException.Code.NOT_FOUND) {
            return erro
        }
        val detalhe = erro.message.orEmpty()
        if (detalhe.equals("NOT_FOUND", ignoreCase = true)
            || detalhe.contains("no function", ignoreCase = true)
            || detalhe.contains("function not found", ignoreCase = true)) {
            return IllegalStateException(
                "O backend hospedado em zeca-jvic.onrender.com não encontrou este serviço. Publique a versão atual da pasta functions/ no serviço.",
                erro,
            )
        }
        return erro
    }

    private fun toPerfil(snapshot: DocumentSnapshot): PerfilJogador = PerfilJogador(
        uid = snapshot.id,
        apelido = snapshot.getString("displayName") ?: "Jogador",
        email = snapshot.getString("email") ?: "",
        username = snapshot.getString("username") ?: "",
        profileSetupComplete = snapshot.getBoolean("profileSetupComplete") == true,
        saldoCentavos = snapshot.getLong("balanceCents") ?: 0L,
        nivel = snapshot.getLong("level")?.toInt() ?: 1,
        avatarUrl = snapshot.getString("avatarUrl") ?: "",
        chavePix = snapshot.getString("pixKey") ?: "",
        tipoChavePix = snapshot.getString("pixKeyType") ?: "",
        partidas = snapshot.getLong("gamesPlayed")?.toInt() ?: 0,
        vitorias = snapshot.getLong("wins")?.toInt() ?: 0,
        inventario = (snapshot.get("inventory") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        ganhoTotalCentavos = snapshot.getLong("totalWonCents") ?: 0L,
        perdaTotalCentavos = snapshot.getLong("totalLostCents") ?: 0L,
        molduraEquipada = snapshot.getString("equippedFrame") ?: "",
        avatarItensEquipados = (snapshot.get("equippedAvatarItems") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        avatarComoFotoPerfil = snapshot.getBoolean("avatarAsProfilePhoto") == true,
    )

    private fun toJogadorRanking(snapshot: DocumentSnapshot): JogadorRanking? {
        val data = snapshot.data ?: return null
        return JogadorRanking(
            uid = snapshot.id,
            apelido = data["displayName"] as? String ?: "Jogador",
            saldoCentavos = (data["balanceCents"] as? Number)?.toLong() ?: 0L,
            nivel = (data["level"] as? Number)?.toInt() ?: 1,
            avatarUrl = data["avatarUrl"] as? String ?: "",
            username = data["username"] as? String ?: "",
            avatarItensEquipados = (data["equippedAvatarItems"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            avatarComoFotoPerfil = data["avatarAsProfilePhoto"] as? Boolean ?: false,
        )
    }

    private fun Map<String, Any>.toEstadoBlackjack(): EstadoBlackjack? {
        val gameId = this["gameId"] as? String ?: return null
        fun cards(key: String): List<CartaBlackjack> = (this[key] as? List<*>)
            .orEmpty()
            .mapNotNull { card ->
                val values = card as? Map<*, *> ?: return@mapNotNull null
                val rank = values["rank"] as? String ?: return@mapNotNull null
                val suit = values["suit"] as? String ?: return@mapNotNull null
                CartaBlackjack(rank, suit)
            }
        return EstadoBlackjack(
            gameId = gameId,
            status = this["status"] as? String ?: "active",
            cartasJogador = cards("playerCards"),
            cartasDealer = cards("dealerCards"),
            cartaOculta = this["dealerHoleHidden"] as? Boolean ?: false,
            apostaCentavos = (this["wagerCents"] as? Number)?.toLong() ?: 0L,
            resultado = this["outcome"] as? String ?: "",
            premioCentavos = (this["payoutCents"] as? Number)?.toLong() ?: 0L,
            lucroCentavos = (this["profitCents"] as? Number)?.toLong() ?: 0L,
            saldoCentavos = (this["balanceCents"] as? Number)?.toLong() ?: 0L,
        )
    }
}