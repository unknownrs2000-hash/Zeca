"use strict";

const { getAuth } = require("firebase-admin/auth");
const { connectFinancialMongo } = require("../financial-mongo");
const { optionValue, runCli } = require("./cli");
const {
  createFinancialBackup,
  initializeFirebaseAdmin,
  migrateBackupToMongo,
  readBackup,
} = require("./financial-migration-lib");

const OPEN_ROOM_STATES = ["waiting", "ready", "active"];

async function main(argv = process.argv, env = process.env) {
  const backupPath = optionValue(argv, "--backup");
  if (!backupPath) throw new Error("Informe --backup com o arquivo gerado por backup:financials.");
  const commit = argv.includes("--commit");
  if (commit && (!argv.includes("--confirm-cutover")
      || env.FINANCIAL_MAINTENANCE_WINDOW !== "true"
      || env.FINANCIAL_CUTOVER_READY !== "true")) {
    throw new Error("O commit exige --confirm-cutover, FINANCIAL_MAINTENANCE_WINDOW=true e FINANCIAL_CUTOVER_READY=true.");
  }
  const backup = await readBackup(backupPath);
  const firestore = initializeFirebaseAdmin();
  const openRooms = await firestore.collection("tugRooms").where("status", "in", OPEN_ROOM_STATES).limit(1).get();
  if (commit && !openRooms.empty) {
    throw new Error("Ainda existem salas abertas; reembolse ou encerre antes do cutover.");
  }
  const connection = await connectFinancialMongo({ env, ensureIndexes: false });
  try {
    const marker = await connection.database.collection("financialMigrations").findOne({ _id: backup.migrationId });
    let liveHashMatches = null;
    if (!marker) {
      const live = await createFinancialBackup(firestore, getAuth());
      liveHashMatches = live.sourceHash === backup.sourceHash;
      if (!liveHashMatches) throw new Error("O Firestore mudou desde o backup; gere outro backup e refaça o dry-run.");
    }
    const result = await migrateBackupToMongo({ database: connection.database, firestore, backup, commit });
    console.log(JSON.stringify({ mode: commit ? "commit" : "dry-run", openTugRooms: !openRooms.empty, liveHashMatches, ...result }));
  } finally {
    await connection.close();
  }
}

if (require.main === module) runCli(main);
module.exports = { main };