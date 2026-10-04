"use strict";

const crypto = require("node:crypto");
const express = require("express");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const handlers = require("./index");
const { HttpsError, STATUS } = require("./callable");
const { getFinancialServices, initializeFinancialRuntime } = require("./financial-runtime");
const { assertFinancialCallableAvailable, assertMinimumAppVersion } = require("./financial-mode");

const app = express();
app.use(express.json({ limit: "20kb" }));

app.get("/", (_req, res) => res.send("zeca-server ok"));

const rankingCache = new Map();
const RANKING_CACHE_TTL_MS = 15_000;
const SOCIAL_RANKING_FIELDS = [
  "displayName",
  "username",
  "level",
  "avatarUrl",
  "avatarAsProfilePhoto",
  "equippedAvatarItems",
  "equippedFrame",
  "countryCode",
  "currencyCode",
  "currencyRate",
  "currencyRateDate",
];

async function authenticateFinancialRequest(req) {
  const header = req.headers.authorization || "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : null;
  if (!token) throw new HttpsError("unauthenticated", "Entre na sua conta para consultar os dados financeiros.");
  let decoded;
  try {
    decoded = await getAuth().verifyIdToken(token);
  } catch {
    throw new HttpsError("unauthenticated", "Sessão inválida. Entre novamente.");
  }
  assertMinimumAppVersion({
    minimum: Number(process.env.ZECA_MIN_APP_VERSION_CODE),
    client: Number(req.get("x-zeca-app-version")),
  });
  const profile = await getFirestore().collection("users").doc(decoded.uid).get();
  if (profile.exists && profile.get("isBlocked") === true) {
    throw new HttpsError("permission-denied", "Esta conta está desativada.");
  }
  return decoded.uid;
}

function sendFinancialError(res, error) {
  const known = error instanceof HttpsError;
  if (!known) console.error("Falha ao consultar carteira Mongo:", error?.message || error);
  const code = known ? error.code : error.code || "unavailable";
  const message = known ? error.message : "Dados financeiros temporariamente indisponíveis. Tente novamente.";
  res.status(STATUS[code] || 503).json({
    error: { code, message, reason: error.reason || undefined },
  });
}

app.get("/api/financial/balance", async (req, res) => {
  try {
    const uid = await authenticateFinancialRequest(req);
    const { store } = await getFinancialServices();
    const balanceCents = await store.getBalance(uid);
    if (balanceCents === null) {
      return res.status(503).json({
        error: { code: "unavailable", message: "A carteira está sendo inicializada. Tente novamente em instantes." },
      });
    }
    return res.json({ balanceCents, loadedAtMs: Date.now() });
  } catch (error) {
    return sendFinancialError(res, error);
  }
});

app.get("/api/financial/transactions", async (req, res) => {
  try {
    const uid = await authenticateFinancialRequest(req);
    const requestedLimit = Number(req.query.limit || 50);
    const limit = Number.isInteger(requestedLimit) ? Math.max(1, Math.min(requestedLimit, 100)) : 50;
    const { store } = await getFinancialServices();
    const transactions = (await store.listTransactions(uid, limit))
      .map(({ _id, legacyData, operationId, migrationId, createdAt, ...entry }) => entry);
    return res.json({ transactions });
  } catch (error) {
    return sendFinancialError(res, error);
  }
});

app.get("/api/financial/leaderboard", async (req, res) => {
  try {
    await authenticateFinancialRequest(req);
    const requestedLimit = Number(req.query.limit || 50);
    const limit = Number.isInteger(requestedLimit) ? Math.max(1, Math.min(requestedLimit, 100)) : 50;
    const cacheKey = String(limit);
    const cached = rankingCache.get(cacheKey);
    if (cached && cached.expiresAtMs > Date.now()) return res.json({ players: cached.players });
    const { connection } = await getFinancialServices();
    const wallets = await connection.repository.listTopWallets(limit);
    if (wallets.length === 0) return res.json({ players: [] });
    const profileSnapshots = await getFirestore().getAll(
      ...wallets.map(({ _id }) => getFirestore().collection("users").doc(_id)),
      { fieldMask: SOCIAL_RANKING_FIELDS },
    );
    const players = wallets.map((wallet, index) => {
      const profile = profileSnapshots[index].data() || {};
      return {
        uid: wallet._id,
        displayName: profile.displayName || "Jogador",
        username: profile.username || "",
        balanceCents: wallet.balanceCents,
        level: Number.isSafeInteger(profile.level) && profile.level > 0 ? profile.level : 1,
        avatarUrl: profile.avatarUrl || "",
        avatarAsProfilePhoto: profile.avatarAsProfilePhoto === true,
        equippedAvatarItems: Array.isArray(profile.equippedAvatarItems) ? profile.equippedAvatarItems : [],
        equippedFrame: profile.equippedFrame || "",
        countryCode: profile.countryCode || "",
        currencyCode: profile.currencyCode || "",
        currencyRate: Number.isFinite(profile.currencyRate) && profile.currencyRate > 0 ? profile.currencyRate : 1,
        currencyRateDate: profile.currencyRateDate || "",
      };
    });
    rankingCache.set(cacheKey, { expiresAtMs: Date.now() + RANKING_CACHE_TTL_MS, players });
    return res.json({ players });
  } catch (error) {
    return sendFinancialError(res, error);
  }
});

app.post("/whatsapp/wallet", async (req, res) => {
  const secret = process.env.WHATSAPP_LINK_SECRET;
  if (!secret || secret.length < 32) {
    console.error("WHATSAPP_LINK_SECRET está ausente ou curto demais.");
    return res.status(503).json({ error: { code: "unavailable", message: "Carteira compartilhada indisponível." } });
  }

  const {
    action,
    jid,
    recipientJid = "",
    requestId = "",
    deltaCents = "",
    description = "",
  } = req.body || {};
  const timestamp = req.get("x-wallet-timestamp") || "";
  const signature = req.get("x-wallet-signature") || "";
  if (!["balance", "adjust", "transfer"].includes(action)
      || typeof jid !== "string"
      || !/^\d+@s\.whatsapp\.net$/.test(jid)
      || typeof recipientJid !== "string"
      || (action === "transfer" && (!/^\d+@s\.whatsapp\.net$/.test(recipientJid) || recipientJid === jid))
      || !/^\d{13}$/.test(timestamp)
      || !/^[a-f\d]{64}$/i.test(signature)) {
    return res.status(400).json({ error: { code: "invalid-argument", message: "Solicitação inválida." } });
  }
  if (Math.abs(Date.now() - Number(timestamp)) > 60_000) {
    return res.status(401).json({ error: { code: "unauthenticated", message: "Solicitação expirada." } });
  }

  const canonical = `${timestamp}\nPOST\n/whatsapp/wallet\n${jid}\n${action}\n${recipientJid}\n${requestId}\n${deltaCents}\n${description}`;
  const expected = crypto.createHmac("sha256", secret).update(canonical).digest();
  const received = Buffer.from(signature, "hex");
  if (received.length !== expected.length || !crypto.timingSafeEqual(expected, received)) {
    return res.status(401).json({ error: { code: "unauthenticated", message: "Assinatura inválida." } });
  }

  try {
    const result = await handlers._operateWhatsAppWallet({
      data: { action, jid, recipientJid, requestId, deltaCents, description },
    });
    return res.json({ result });
  } catch (error) {
    const known = error instanceof HttpsError;
    const knownFinancial = !known && [
      "InsufficientBalanceError",
      "WalletNotFoundError",
      "IdempotencyConflictError",
      "ActionPendingError",
      "ProjectionPendingError",
      "CommitUncertainError",
    ].includes(error.name);
    if (!known && !knownFinancial) console.error("Falha na carteira compartilhada do WhatsApp:", error);
    const code = known || knownFinancial ? error.code : "internal";
    const message = known || knownFinancial ? error.message : "Não foi possível atualizar a carteira.";
    return res.status(STATUS[code] || 500).json({ error: { code, message } });
  }
});

app.post("/whatsapp/work", async (req, res) => {
  const secret = process.env.WHATSAPP_LINK_SECRET;
  if (!secret || secret.length < 32) {
    console.error("WHATSAPP_LINK_SECRET está ausente ou curto demais.");
    return res.status(503).json({ error: { code: "unavailable", message: "Empregos compartilhados indisponíveis." } });
  }

  const {
    action,
    jid,
    requestId = "",
    jobSlug = "",
    sessionId = "",
    answerIndex = "",
  } = req.body || {};
  const timestamp = req.get("x-work-timestamp") || "";
  const signature = req.get("x-work-signature") || "";
  if (!["status", "apply", "resign", "promote", "start", "complete"].includes(action)
      || typeof jid !== "string"
      || !/^\d+@s\.whatsapp\.net$/.test(jid)
      || typeof requestId !== "string"
      || typeof jobSlug !== "string"
      || typeof sessionId !== "string"
      || !/^\d{13}$/.test(timestamp)
      || !/^[a-f\d]{64}$/i.test(signature)) {
    return res.status(400).json({ error: { code: "invalid-argument", message: "Solicitação inválida." } });
  }
  if (Math.abs(Date.now() - Number(timestamp)) > 60_000) {
    return res.status(401).json({ error: { code: "unauthenticated", message: "Solicitação expirada." } });
  }

  const canonical = [
    timestamp,
    "POST",
    "/whatsapp/work",
    jid,
    action,
    requestId,
    jobSlug,
    sessionId,
    answerIndex,
  ].join("\n");
  const expected = crypto.createHmac("sha256", secret).update(canonical).digest();
  const received = Buffer.from(signature, "hex");
  if (received.length !== expected.length || !crypto.timingSafeEqual(expected, received)) {
    return res.status(401).json({ error: { code: "unauthenticated", message: "Assinatura inválida." } });
  }

  try {
    const result = await handlers._operateWhatsAppCareer({
      data: { action, jid, requestId, jobSlug, sessionId, answerIndex: answerIndex === "" ? undefined : answerIndex },
    });
    return res.json({ result });
  } catch (error) {
    const known = error instanceof HttpsError;
    if (!known) console.error("Falha na carreira compartilhada do WhatsApp:", error);
    const code = known ? error.code : "internal";
    const message = known ? error.message : "Não foi possível atualizar o emprego.";
    return res.status(STATUS[code] || 500).json({ error: { code, message } });
  }
});

app.post("/whatsapp/link/complete", async (req, res) => {
  const secret = process.env.WHATSAPP_LINK_SECRET;
  if (!secret || secret.length < 32) {
    console.error("WHATSAPP_LINK_SECRET está ausente ou curto demais.");
    return res.status(503).json({ error: { code: "unavailable", message: "Vínculo do WhatsApp indisponível." } });
  }

  const { code, jid } = req.body || {};
  const timestamp = req.get("x-link-timestamp") || "";
  const signature = req.get("x-link-signature") || "";
  if (typeof code !== "string" || typeof jid !== "string" || !/^\d{13}$/.test(timestamp)
      || !/^[a-f\d]{64}$/i.test(signature)) {
    return res.status(400).json({ error: { code: "invalid-argument", message: "Solicitação inválida." } });
  }
  if (Math.abs(Date.now() - Number(timestamp)) > 60_000) {
    return res.status(401).json({ error: { code: "unauthenticated", message: "Solicitação expirada." } });
  }

  const payload = `${timestamp}\n${jid}\n${code}`;
  const expected = crypto.createHmac("sha256", secret).update(payload).digest();
  const received = Buffer.from(signature, "hex");
  if (received.length !== expected.length || !crypto.timingSafeEqual(received, expected)) {
    return res.status(401).json({ error: { code: "unauthenticated", message: "Assinatura inválida." } });
  }

  try {
    const result = await handlers._completeWhatsAppLink({ data: { code, jid } });
    return res.json({ result });
  } catch (error) {
    const known = error instanceof HttpsError;
    if (!known) console.error("Falha ao concluir vínculo do WhatsApp:", error);
    const code = known ? error.code : "internal";
    const message = known ? error.message : "Não foi possível concluir o vínculo.";
    return res.status(STATUS[code] || 500).json({ error: { code, message } });
  }
});

app.post("/call/:name", async (req, res) => {
  const { name } = req.params;
  if (name.startsWith("_") || !Object.hasOwn(handlers, name)) {
    return res.status(404).json({ error: { code: "not-found", message: "Função não encontrada." } });
  }
  try {
    const header = req.headers.authorization || "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : null;
    let auth = null;
    if (token) {
      try {
        const decoded = await getAuth().verifyIdToken(token);
        auth = { uid: decoded.uid, token: decoded };
      } catch {
        throw new HttpsError("unauthenticated", "Sessão inválida. Entre novamente.");
      }
      const profile = await getFirestore().collection("users").doc(auth.uid).get();
      if (profile.exists && profile.get("isBlocked") === true) {
        throw new HttpsError("permission-denied", "Esta conta está desativada.");
      }
      assertMinimumAppVersion({
        minimum: Number(process.env.ZECA_MIN_APP_VERSION_CODE),
        client: Number(req.get("x-zeca-app-version")),
      });
    }
    assertFinancialCallableAvailable(name);
    const result = await handlers[name]({ auth, data: req.body?.data ?? {} });
    res.json({ result });
  } catch (error) {
    const known = error instanceof HttpsError;
    if (!known) console.error(error);
    const quotaExceeded = error.code === 8
      || error.code === "RESOURCE_EXHAUSTED"
      || error.code === "resource-exhausted";
    const knownFinancial = [
      "projection-pending",
      "commit-uncertain",
      "action-in-progress",
      "financial-handler-not-migrated",
      "app-version-unsupported",
      "app-version-policy-unconfigured",
    ].includes(error.reason)
      || ["InsufficientBalanceError", "WalletNotFoundError", "IdempotencyConflictError", "ActionPendingError"]
        .includes(error.name);
    const code = known ? error.code : knownFinancial ? error.code : quotaExceeded ? "resource-exhausted" : "internal";
    const message = known
      ? error.message
      : knownFinancial
        ? error.message
      : quotaExceeded
        ? "A cota do Firestore foi excedida. Aguarde a renovação da cota ou habilite faturamento no Firebase."
        : "Erro interno.";
    res.status(STATUS[code] || 500).json({
      error: { code, message, reason: knownFinancial ? error.reason : undefined },
    });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => {
  console.log(`zeca-server na porta ${port}`);
  const startFinancialRuntime = () => {
    initializeFinancialRuntime().catch(() => {
      console.error("Mongo financeiro indisponível; nova tentativa em 30 s. Rotas financeiras permanecem fechadas.");
      setTimeout(startFinancialRuntime, 30_000);
    });
  };
  startFinancialRuntime();
  const sweepExpiredRooms = () => {
    handlers._cleanupExpiredTugRooms().catch((error) => {
      console.error("Falha ao remover salas concluídas expiradas:", error);
    });
  };
  sweepExpiredRooms();
  setInterval(sweepExpiredRooms, 30_000);
});

app.get("/api/financial/players", async (req, res) => {
  try {
    await authenticateFinancialRequest(req);
    const prefix = typeof req.query.username === "string"
      ? req.query.username.trim().replace(/^@/, "").toLowerCase()
      : "";
    if (!prefix || prefix.length > 30) {
      return res.status(400).json({ error: { code: "invalid-argument", message: "Informe um nome de usuário válido." } });
    }
    const profileSnapshot = await getFirestore().collection("users")
      .select(...SOCIAL_RANKING_FIELDS)
      .orderBy("username")
      .startAt(prefix)
      .endAt(`${prefix}\uf8ff`)
      .limit(5)
      .get();
    const { store } = await getFinancialServices();
    const players = await Promise.all(profileSnapshot.docs.map(async (document) => {
      const profile = document.data();
      const balanceCents = await store.getBalance(document.id);
      if (balanceCents === null) return null;
      return {
        uid: document.id,
        displayName: profile.displayName || "Jogador",
        username: profile.username || "",
        balanceCents,
        level: Number.isSafeInteger(profile.level) && profile.level > 0 ? profile.level : 1,
        avatarUrl: profile.avatarUrl || "",
        avatarAsProfilePhoto: profile.avatarAsProfilePhoto === true,
        equippedAvatarItems: Array.isArray(profile.equippedAvatarItems) ? profile.equippedAvatarItems : [],
        equippedFrame: profile.equippedFrame || "",
        countryCode: profile.countryCode || "",
        currencyCode: profile.currencyCode || "",
        currencyRate: Number.isFinite(profile.currencyRate) && profile.currencyRate > 0 ? profile.currencyRate : 1,
        currencyRateDate: profile.currencyRateDate || "",
      };
    }));
    return res.json({ players: players.filter(Boolean) });
  } catch (error) {
    return sendFinancialError(res, error);
  }
});