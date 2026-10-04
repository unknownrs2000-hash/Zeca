"use strict";

const { getAuth } = require("firebase-admin/auth");
const { optionValue, runCli } = require("./cli");
const { createFinancialBackup, initializeFirebaseAdmin, writeBackupExclusive } = require("./financial-migration-lib");

async function main(argv = process.argv) {
  const output = optionValue(argv, "--output");
  if (!output) throw new Error("Informe --output com um caminho novo para o backup.");
  const firestore = initializeFirebaseAdmin();
  const backup = await createFinancialBackup(firestore, getAuth());
  const file = await writeBackupExclusive(output, backup);
  console.log(JSON.stringify({ file, sourceHash: backup.sourceHash, counts: backup.counts }));
}

if (require.main === module) runCli(main);
module.exports = { main };