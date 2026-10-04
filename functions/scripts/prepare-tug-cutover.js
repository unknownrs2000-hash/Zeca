"use strict";

const { createHash } = require("node:crypto");
const { cert, getApps, initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { closeFinancialRuntime, initializeFinancialRuntime } = require("../financial-runtime");
const { SERVER_TIMESTAMP } = require("../financial-projection");

const OPEN_ROOM_STATES = ["waiting", "ready", "active"];
const PAGE_SIZE = 100;

function preconditionError(message) {
  const error = new Error(message);
  error.code = "failed-precondition";
  return error;
}

function closeRoomProjection(room, roomId) {
  return {
    status: "cancelled",
    cutoverClosed: true,
    cutoverReason: "financial_mongo",
    players: [],
    participantUids: [],
    invitedUids: [],
    inviteOnly: false,
    passwordHash: "",
    opponentUid: "",
    opponentName: "",
    lastUpdatedAtMs: Number.isSafeInteger(room.lastUpdatedAtMs) ? room.lastUpdatedAtMs : 0,
    cutoverClosedAt: SERVER_TIMESTAMP,
    cutoverRoomId: roomId,
  };
}

function getRefundPlayers(room) {
  const players = Array.isArray(room.players) ? room.players : [];
  const seen = new Set();
  return players.filter((player) => {
    if (typeof player?.uid !== "string" || !player.uid || seen.has(player.uid)) return false;
    seen.add(player.uid);
    return true;
  });
}

function planRoom(room, documentId) {
  const roomId = room.roomId || documentId;
  const players = getRefundPlayers(room);
  const stakeCents = room.stakeCents;
  if (players.length > 0 && (!Number.isSafeInteger(stakeCents) || stakeCents < 0)) {
    throw preconditionError(`Sala ${roomId} tem aposta inválida; o cutover foi interrompido.`);
  }
  const perPlayerCents = players.length > 0 ? stakeCents : 0;
  return { roomId, players, stakeCents: perPlayerCents, refundTotalCents: perPlayerCents * players.length };
}

function initializeFirebase() {
  if (getApps().length > 0) return;
  const serializedServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!serializedServiceAccount) throw preconditionError("Configure a credencial Firebase no ambiente do backend.");
  initializeApp({ credential: cert(JSON.parse(serializedServiceAccount)) });
}

// Somente leitura. Pressupõe poucas salas abertas (sem paginação).
async function planOpenRooms({ database }) {
  const snapshot = await database.collection("tugRooms").where("status", "in", OPEN_ROOM_STATES).get();
  const plans = snapshot.docs.map((document) => planRoom(document.data(), document.id));
  return {
    rooms: plans.length,
    roomsWithRefund: plans.filter((plan) => plan.refundTotalCents > 0).length,
    playersToRefund: plans.reduce((total, plan) => total + (plan.stakeCents > 0 ? plan.players.length : 0), 0),
    refundTotalCents: plans.reduce((total, plan) => total + plan.refundTotalCents, 0),
    roomIds: plans.map((plan) => plan.roomId),
  };
}

async function closeOpenRooms({ database, store }) {
  const summary = { roomsClosed: 0, playersRefunded: 0 };
  while (true) {
    const snapshot = await database.collection("tugRooms")
      .where("status", "in", OPEN_ROOM_STATES)
      .limit(PAGE_SIZE)
      .get();
    if (snapshot.empty) break;

    for (const document of snapshot.docs) {
      const room = document.data();
      const { roomId, players, stakeCents } = planRoom(room, document.id);
      const roomWrite = {
        path: document.ref.path,
        data: closeRoomProjection(room, roomId),
      };
      if (players.length === 0 || stakeCents === 0) {
        await document.ref.set(roomWrite.data, { merge: true });
        summary.roomsClosed += 1;
        continue;
      }

      const operationId = `tug-cutover-refund:${roomId}`;
      const lockKey = `tug-cutover:${roomId}`;
      const entries = players.map((player) => ({ uid: player.uid, deltaCents: stakeCents }));
      const ledger = players.map((player) => {
        const uidKey = createHash("sha256").update(player.uid).digest("hex").slice(0, 16);
        return {
          uid: player.uid,
          id: `tug_cutover_refund_${roomId}_${uidKey}`,
          description: "Cabo de Guerra · reembolso de encerramento para migração financeira",
          deltaCents: stakeCents,
          type: "tug_cutover_refund",
          roomId,
          createdAt: SERVER_TIMESTAMP,
        };
      });
      await store.withActionLock(lockKey, (lockLease) => store.applyOperation({
        operationId,
        operationType: "tug_cutover_refund",
        entries,
        ledger,
        lockLease,
        projection: { writes: [roomWrite] },
        result: { roomId, refundedCount: players.length },
      }));
      summary.roomsClosed += 1;
      summary.playersRefunded += players.length;
    }
  }

  const remaining = await database.collection("tugRooms")
    .where("status", "in", OPEN_ROOM_STATES)
    .limit(1)
    .get();
  if (!remaining.empty) throw preconditionError("Ainda existem salas abertas; não ative o modo financeiro Mongo.");
  return summary;
}

async function main(argv = process.argv, env = process.env) {
  initializeFirebase();
  const database = getFirestore();
  if (!argv.includes("--execute")) {
    const plan = await planOpenRooms({ database });
    console.log(JSON.stringify({ mode: "dry-run", ...plan }));
    return;
  }
  if (env.FINANCIAL_MAINTENANCE_WINDOW !== "true") {
    throw preconditionError("Execute somente com FINANCIAL_MAINTENANCE_WINDOW=true (app e bot parados).");
  }
  const runtime = await initializeFinancialRuntime({ database });
  try {
    const summary = await closeOpenRooms({ database, store: runtime.store });
    console.log(JSON.stringify({ mode: "execute", ...summary }));
  } finally {
    await closeFinancialRuntime();
  }
}

if (require.main === module) {
  main().catch((error) => {
    const knownSafe = ["failed-precondition", "already-exists", "unavailable"].includes(error.code);
    console.error(knownSafe ? error.message : "Falha ao encerrar salas. Verifique os logs protegidos antes de prosseguir.");
    process.exitCode = 1;
  });
}

module.exports = { closeOpenRooms, planOpenRooms };