"use strict";

const fs = require("node:fs");
const path = require("node:path");
const { cert, initializeApp } = require("firebase-admin/app");
const { FieldPath, FieldValue, Timestamp, getFirestore } = require("firebase-admin/firestore");

const INITIAL_BONUS_CENTS = 50_000;
const MIGRATION_ID = "retroactive-initial-bonus-20260930";
const BATCH_SIZE = 200;
const EXPECTED_PROJECT_ID = JSON.parse(
  fs.readFileSync(path.resolve(__dirname, "../../app/google-services.json"), "utf8"),
).project_info.project_id;

function initializeDatabase() {
  const serializedServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!serializedServiceAccount) {
    throw new Error("Configure FIREBASE_SERVICE_ACCOUNT no ambiente antes de executar a migração.");
  }

  const serviceAccount = JSON.parse(serializedServiceAccount);
  if (serviceAccount.project_id !== EXPECTED_PROJECT_ID) {
    throw new Error(
      `Projeto da credencial (${serviceAccount.project_id}) diferente do app (${EXPECTED_PROJECT_ID}).`,
    );
  }

  initializeApp({ credential: cert(serviceAccount), projectId: EXPECTED_PROJECT_ID });
  return getFirestore();
}

async function getMigration(database, apply) {
  const migrationRef = database.collection("systemMigrations").doc(MIGRATION_ID);
  if (!apply) {
    const snapshot = await migrationRef.get();
    if (snapshot.exists) return { ref: migrationRef, ...snapshot.data() };
    return { ref: migrationRef, state: "preview", cohortCutoff: Timestamp.now() };
  }

  const migration = await database.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(migrationRef);
    if (snapshot.exists) return snapshot.data();
    const data = {
      state: "running",
      cohortCutoff: Timestamp.now(),
      createdAt: FieldValue.serverTimestamp(),
    };
    transaction.create(migrationRef, data);
    return data;
  });
  return { ref: migrationRef, ...migration };
}

function createdAtMillis(profile) {
  const value = profile.createdAt;
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return null;
}

function startingBalance(profile) {
  const balance = profile.balanceCents === undefined ? 0 : profile.balanceCents;
  if (!Number.isSafeInteger(balance) || balance < 0) return null;
  const updatedBalance = balance + INITIAL_BONUS_CENTS;
  return Number.isSafeInteger(updatedBalance) ? updatedBalance : null;
}

async function creditAccount(database, userId, cutoffMillis) {
  const userRef = database.collection("users").doc(userId);
  const rankRef = database.collection("leaderboard").doc(userId);
  const grantRef = userRef.collection("transactions").doc(MIGRATION_ID);

  return database.runTransaction(async (transaction) => {
    const [userSnapshot, rankSnapshot, grantSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(rankRef),
      transaction.get(grantRef),
    ]);
    if (!userSnapshot.exists) return "missing";
    if (grantSnapshot.exists) return "already-credited";

    const profile = userSnapshot.data();
    const createdAt = createdAtMillis(profile);
    if (createdAt !== null && createdAt > cutoffMillis) return "created-after-cutoff";

    const balanceAfter = startingBalance(profile);
    if (balanceAfter === null) return "invalid-balance";

    transaction.update(userRef, {
      balanceCents: balanceAfter,
      balanceInitialized: true,
    });
    if (rankSnapshot.exists) {
      transaction.update(rankRef, { balanceCents: balanceAfter });
    } else {
      transaction.create(rankRef, {
        displayName: profile.displayName || "Jogador",
        username: profile.username || "",
        balanceCents: balanceAfter,
        level: Number.isSafeInteger(profile.level) && profile.level > 0 ? profile.level : 1,
        avatarUrl: profile.avatarUrl || "",
        avatarAsProfilePhoto: profile.avatarAsProfilePhoto === true,
        equippedAvatarItems: Array.isArray(profile.equippedAvatarItems)
          ? profile.equippedAvatarItems
          : [],
      });
    }
    transaction.create(grantRef, {
      description: "Bônus retroativo de saldo inicial",
      deltaCents: INITIAL_BONUS_CENTS,
      type: "initial_balance_grant",
      migrationId: MIGRATION_ID,
      createdAt: FieldValue.serverTimestamp(),
    });
    return "credited";
  });
}

async function main() {
  const apply = process.argv.includes("--apply");
  const database = initializeDatabase();
  const migration = await getMigration(database, apply);
  if (migration.state === "completed") {
    console.log(`Migração já concluída: ${migration.creditedCount} contas receberam R$ 500.`);
    return;
  }

  const cutoffMillis = migration.cohortCutoff.toMillis();
  const counts = {
    eligible: 0,
    credited: 0,
    alreadyCredited: 0,
    skippedAfterCutoff: 0,
    skippedInvalidBalance: 0,
    skippedMissing: 0,
  };
  let cursor = null;

  while (true) {
    let query = database.collection("users")
      .orderBy(FieldPath.documentId())
      .limit(BATCH_SIZE);
    if (cursor) query = query.startAfter(cursor);

    const page = await query.get();
    if (page.empty) break;
    cursor = page.docs[page.docs.length - 1];

    for (const userDocument of page.docs) {
      const profile = userDocument.data();
      const createdAt = createdAtMillis(profile);
      if (createdAt !== null && createdAt > cutoffMillis) {
        counts.skippedAfterCutoff += 1;
        continue;
      }
      counts.eligible += 1;

      if (!apply) {
        const grant = await userDocument.ref.collection("transactions").doc(MIGRATION_ID).get();
        if (grant.exists) counts.alreadyCredited += 1;
        else if (startingBalance(profile) === null) counts.skippedInvalidBalance += 1;
        else counts.credited += 1;
        continue;
      }

      const result = await creditAccount(database, userDocument.id, cutoffMillis);
      if (result === "credited") counts.credited += 1;
      else if (result === "already-credited") counts.alreadyCredited += 1;
      else if (result === "created-after-cutoff") counts.skippedAfterCutoff += 1;
      else if (result === "invalid-balance") counts.skippedInvalidBalance += 1;
      else counts.skippedMissing += 1;
    }
  }

  console.log(`Projeto: ${EXPECTED_PROJECT_ID}`);
  console.log(`Coorte até: ${new Date(cutoffMillis).toISOString()}`);
  console.log(JSON.stringify(counts, null, 2));

  if (apply) {
    await migration.ref.update({
      state: "completed",
      creditedCount: counts.credited + counts.alreadyCredited,
      skippedAfterCutoff: counts.skippedAfterCutoff,
      skippedInvalidBalance: counts.skippedInvalidBalance,
      completedAt: FieldValue.serverTimestamp(),
    });
    console.log("Crédito concluído; cada conta recebeu no máximo um lançamento de R$ 500.");
  } else {
    console.log("Simulação: nenhum saldo foi alterado. Use --apply para executar.");
  }
}

main().catch((error) => {
  console.error(error.message || error);
  process.exitCode = 1;
});