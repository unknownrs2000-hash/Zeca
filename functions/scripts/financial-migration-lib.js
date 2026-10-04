"use strict";

const fs = require("node:fs/promises");
const path = require("node:path");
const { createHash } = require("node:crypto");
const { cert, getApps, initializeApp } = require("firebase-admin/app");
const { FieldPath, FieldValue, getFirestore } = require("firebase-admin/firestore");

const DEFAULT_MIGRATION_ID = "zeca-financial-cutover-v1";
const PAGE_SIZE = 200;

function canonicalize(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (Array.isArray(value)) return value.map(canonicalize);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.keys(value).sort().map((key) => [key, canonicalize(value[key])]),
  );
}

function sha256(value) {
  return createHash("sha256").update(JSON.stringify(canonicalize(value))).digest("hex");
}

function timestampMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  if (Number.isSafeInteger(value)) return value;
  return 0;
}

function normalizedEntry(uid, id, value) {
  const data = canonicalize(value || {});
  return {
    uid,
    id,
    description: typeof data.description === "string" ? data.description : "Movimentação",
    deltaCents: data.deltaCents,
    type: typeof data.type === "string" ? data.type : "legacy",
    createdAtMs: timestampMillis(value?.createdAtMs ?? value?.createdAt),
    data,
  };
}

function buildBackup({ users, pixKeys = [], sportsBets = [], inventoryProfiles = [], authAccountCount = null }) {
  const invalidUsers = users.filter((user) => typeof user.uid !== "string" || !user.uid
    || !Number.isSafeInteger(user.balanceCents) || user.balanceCents < 0);
  if (invalidUsers.length > 0) {
    throw new Error(
      `${invalidUsers.length} conta(s) sem saldo inteiro não negativo: `
      + invalidUsers.map((user) => user.uid || "(sem uid)").join(", "),
    );
  }
  const normalizedUsers = users.map((user) => {
    if (typeof user.uid !== "string" || !user.uid
        || !Number.isSafeInteger(user.balanceCents) || user.balanceCents < 0) {
      throw new Error("A snapshot contém uma conta sem saldo inteiro não negativo.");
    }
    const transactions = user.transactions
      .map((transaction) => normalizedEntry(user.uid, transaction.id, transaction.data))
      .sort((left, right) => left.id.localeCompare(right.id));
    return {
      uid: user.uid,
      balanceCents: user.balanceCents,
      createdAtMs: timestampMillis(user.createdAt),
      inventory: Array.isArray(user.inventory) ? user.inventory : [],
      transactions,
    };
  }).sort((left, right) => left.uid.localeCompare(right.uid));
  const normalizedPixKeys = pixKeys
    .map(({ id, data }) => ({ id, data: canonicalize(data) }))
    .sort((left, right) => left.id.localeCompare(right.id));
  const normalizedSportsBets = sportsBets
    .map(({ id, data }) => ({ id, data: canonicalize(data) }))
    .sort((left, right) => left.id.localeCompare(right.id));
  const normalizedInventoryProfiles = inventoryProfiles
    .map(({ id, data }) => ({ id, data: canonicalize(data) }))
    .sort((left, right) => left.id.localeCompare(right.id));
  const source = {
    users: normalizedUsers,
    pixKeys: normalizedPixKeys,
    sportsBets: normalizedSportsBets,
    inventoryProfiles: normalizedInventoryProfiles,
  };
  const counts = {
    users: normalizedUsers.length,
    wallets: normalizedUsers.length,
    transactions: normalizedUsers.reduce((sum, user) => sum + user.transactions.length, 0),
    pixKeys: normalizedPixKeys.length,
    inventoryProfiles: normalizedInventoryProfiles.length,
    openSportsBets: normalizedSportsBets.filter((bet) => bet.data.status === "open").length,
    authAccounts: authAccountCount,
    authAccountsWithoutProfile: authAccountCount == null ? null : Math.max(0, authAccountCount - normalizedUsers.length),
  };
  return {
    schemaVersion: 1,
    migrationId: DEFAULT_MIGRATION_ID,
    createdAt: new Date().toISOString(),
    counts,
    sourceHash: sha256(source),
    source,
  };
}

async function readPages(query, fieldPath = FieldPath.documentId()) {
  const documents = [];
  let cursor = null;
  while (true) {
    let pageQuery = query.orderBy(fieldPath).limit(PAGE_SIZE);
    if (cursor) pageQuery = pageQuery.startAfter(cursor);
    const page = await pageQuery.get();
    if (page.empty) return documents;
    documents.push(...page.docs);
    cursor = page.docs[page.docs.length - 1];
  }
}

async function safeCollectionSnapshot(database, name) {
  return readPages(database.collection(name));
}

async function countAuthAccounts(auth) {
  let total = 0;
  let pageToken;
  do {
    const page = await auth.listUsers(1000, pageToken);
    total += page.users.length;
    pageToken = page.pageToken;
  } while (pageToken);
  return total;
}

async function createFinancialBackup(database, auth = null) {
  const userDocuments = await readPages(database.collection("users"));
  const users = await Promise.all(userDocuments.map(async (document) => {
    const profile = document.data();
    const transactionDocuments = await readPages(document.ref.collection("transactions"));
    return {
      uid: document.id,
      balanceCents: profile.balanceCents,
      createdAt: profile.createdAt,
      inventory: profile.inventory,
      transactions: transactionDocuments.map((transaction) => ({ id: transaction.id, data: transaction.data() })),
    };
  }));
  const [pixKeyDocuments, sportsBetDocuments, inventoryProfileDocuments, authUsers] = await Promise.all([
    safeCollectionSnapshot(database, "pixKeys"),
    safeCollectionSnapshot(database, "sportsBets"),
    safeCollectionSnapshot(database, "inventoryProfiles"),
    auth ? countAuthAccounts(auth) : Promise.resolve(null),
  ]);
  return buildBackup({
    users,
    pixKeys: pixKeyDocuments.map((document) => ({ id: document.id, data: document.data() })),
    sportsBets: sportsBetDocuments.map((document) => ({ id: document.id, data: document.data() })),
    inventoryProfiles: inventoryProfileDocuments.map((document) => ({ id: document.id, data: document.data() })),
    authAccountCount: authUsers,
  });
}

function validateBackup(backup) {
  if (backup?.schemaVersion !== 1 || backup?.migrationId !== DEFAULT_MIGRATION_ID
      || !Array.isArray(backup.source?.users) || typeof backup.sourceHash !== "string") {
    throw new Error("Backup financeiro inválido ou de outra versão.");
  }
  const rebuilt = buildBackup({
    users: backup.source.users.map((user) => ({
      ...user,
      createdAt: user.createdAtMs,
      transactions: user.transactions.map((transaction) => ({ id: transaction.id, data: transaction.data })),
    })),
    pixKeys: backup.source.pixKeys,
    sportsBets: backup.source.sportsBets,
    inventoryProfiles: backup.source.inventoryProfiles,
    authAccountCount: backup.counts?.authAccounts ?? null,
  });
  if (rebuilt.sourceHash !== backup.sourceHash) throw new Error("Hash do backup não confere; arquivo alterado ou incompleto.");
  return backup;
}

async function writeBackupExclusive(filePath, backup) {
  const resolvedPath = path.resolve(filePath);
  await fs.mkdir(path.dirname(resolvedPath), { recursive: true, mode: 0o700 });
  await fs.writeFile(resolvedPath, `${JSON.stringify(backup, null, 2)}\n`, { flag: "wx", mode: 0o600 });
  return resolvedPath;
}

async function readBackup(filePath) {
  const backup = JSON.parse(await fs.readFile(filePath, "utf8"));
  return validateBackup(backup);
}

function initializeFirebaseAdmin() {
  if (getApps().length > 0) return getFirestore();
  const serializedServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!serializedServiceAccount) throw new Error("Configure a credencial Firebase no ambiente; nenhum arquivo .env é carregado.");
  const serviceAccount = JSON.parse(serializedServiceAccount);
  initializeApp({ credential: cert(serviceAccount), projectId: serviceAccount.project_id });
  return getFirestore();
}

async function migrateBackupToMongo({ database, firestore, backup, commit }) {
  validateBackup(backup);
  if (backup.counts.openSportsBets > 0) {
    throw new Error("O backup ainda tem apostas abertas. Liquide-as ou encerre/refunde antes do cutover.");
  }
  const migrations = database.collection("financialMigrations");
  const migrationId = backup.migrationId;
  let marker = await migrations.findOne({ _id: migrationId });
  if (marker && marker.sourceHash !== backup.sourceHash) {
    throw new Error("Este ID de migração já foi associado a outro hash de origem.");
  }
  if (marker?.state === "completed") {
    return { state: "already-completed", sourceHash: marker.sourceHash, counts: marker.counts };
  }
  for (const user of backup.source.users) {
    for (const entry of user.transactions) {
      if (!Number.isSafeInteger(entry.deltaCents)) {
        throw new Error(`Lançamento inválido em uid ${user.uid}, id ${entry.id}.`);
      }
    }
  }
  if (!commit) return { state: marker?.state || "preview", sourceHash: backup.sourceHash, counts: backup.counts };
  if (!marker) {
    await migrations.insertOne({
      _id: migrationId,
      state: "running",
      sourceHash: backup.sourceHash,
      counts: backup.counts,
      startedAt: new Date(),
    });
    marker = await migrations.findOne({ _id: migrationId });
  }

  const wallets = database.collection("wallets");
  const ledger = database.collection("financialTransactions");
  for (const user of backup.source.users) {
    const existingWallet = await wallets.findOne({ _id: user.uid });
    if (existingWallet) {
      if (existingWallet.sourceMigrationId !== migrationId
          || existingWallet.balanceCents !== user.balanceCents) {
        throw new Error(`Carteira Mongo existente diverge da origem para uid ${user.uid}.`);
      }
    } else {
      await wallets.insertOne({
        _id: user.uid,
        balanceCents: user.balanceCents,
        createdAtMs: user.createdAtMs || Date.now(),
        updatedAtMs: Date.now(),
        sourceMigrationId: migrationId,
      });
    }
    for (const entry of user.transactions) {
      if (!Number.isSafeInteger(entry.deltaCents)) {
        throw new Error(`Lançamento inválido em uid ${user.uid}, id ${entry.id}.`);
      }
      const documentId = `${user.uid}:${entry.id}`;
      const imported = {
        _id: documentId,
        uid: user.uid,
        id: entry.id,
        operationId: `migration:${migrationId}`,
        migrationId,
        description: entry.description,
        deltaCents: entry.deltaCents,
        type: entry.type,
        createdAtMs: entry.createdAtMs,
        legacyData: entry.data,
      };
      const current = await ledger.findOne({ _id: documentId });
      if (current) {
        if (current.migrationId !== migrationId || current.deltaCents !== entry.deltaCents
            || current.description !== entry.description || current.createdAtMs !== entry.createdAtMs) {
          throw new Error(`Lançamento Mongo conflita com a origem: ${documentId}.`);
        }
      } else {
        await ledger.insertOne(imported);
      }
    }
  }

  const backupUids = new Set(backup.source.users.map((user) => user.uid));
  const userDocuments = (await readPages(firestore.collection("users")))
    .filter((document) => backupUids.has(document.id));
  for (let offset = 0; offset < userDocuments.length; offset += 200) {
    const batch = firestore.batch();
    const group = userDocuments.slice(offset, offset + 200);
    for (const document of group) {
      batch.update(document.ref, { balanceCents: FieldValue.delete(), balanceInitialized: FieldValue.delete() });
      const rank = await firestore.collection("leaderboard").doc(document.id).get();
      if (rank.exists) batch.update(rank.ref, { balanceCents: FieldValue.delete() });
    }
    await batch.commit();
  }
  await migrations.updateOne(
    { _id: migrationId, sourceHash: backup.sourceHash },
    { $set: { state: "completed", completedAt: new Date(), counts: backup.counts } },
  );
  return { state: "completed", sourceHash: backup.sourceHash, counts: backup.counts };
}

async function verifyMongoAgainstBackup({ database, backup }) {
  validateBackup(backup);
  const marker = await database.collection("financialMigrations").findOne({ _id: backup.migrationId });
  if (!marker || marker.state !== "completed" || marker.sourceHash !== backup.sourceHash) {
    throw new Error("Migração ausente, incompleta ou com hash diferente.");
  }
  const wallets = database.collection("wallets");
  const ledger = database.collection("financialTransactions");
  const balances = [];
  for (const user of backup.source.users) {
    const wallet = await wallets.findOne({ _id: user.uid });
    if (!wallet) throw new Error(`Carteira ausente para uid ${user.uid}.`);
    balances.push({ uid: user.uid, expectedCents: user.balanceCents, actualCents: wallet.balanceCents });
    if (wallet.balanceCents !== user.balanceCents) throw new Error(`Saldo diverge para uid ${user.uid}.`);
    for (const entry of user.transactions) {
      const imported = await ledger.findOne({ _id: `${user.uid}:${entry.id}` });
      if (!imported || imported.migrationId !== backup.migrationId
          || imported.deltaCents !== entry.deltaCents || imported.description !== entry.description) {
        throw new Error(`Hash/conteúdo do extrato diverge em uid ${user.uid}, id ${entry.id}.`);
      }
    }
  }
  const walletCount = await wallets.countDocuments({ sourceMigrationId: backup.migrationId });
  const transactionCount = await ledger.countDocuments({ migrationId: backup.migrationId });
  if (walletCount !== backup.counts.wallets || transactionCount !== backup.counts.transactions) {
    throw new Error("Contagens Mongo não correspondem ao backup.");
  }
  return {
    state: "verified",
    sourceHash: backup.sourceHash,
    counts: { wallets: walletCount, transactions: transactionCount },
    balances,
  };
}

module.exports = {
  DEFAULT_MIGRATION_ID,
  buildBackup,
  canonicalize,
  createFinancialBackup,
  initializeFirebaseAdmin,
  migrateBackupToMongo,
  readBackup,
  sha256,
  validateBackup,
  verifyMongoAgainstBackup,
  writeBackupExclusive,
};