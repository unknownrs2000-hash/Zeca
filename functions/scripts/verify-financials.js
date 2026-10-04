"use strict";

const { connectFinancialMongo } = require("../financial-mongo");
const { optionValue, runCli } = require("./cli");
const { readBackup, verifyMongoAgainstBackup } = require("./financial-migration-lib");

async function main(argv = process.argv, env = process.env) {
  const backupPath = optionValue(argv, "--backup");
  if (!backupPath) throw new Error("Informe --backup com o arquivo usado na migração.");
  const backup = await readBackup(backupPath);
  const connection = await connectFinancialMongo({ env, ensureIndexes: false });
  try {
    const report = await verifyMongoAgainstBackup({ database: connection.database, backup });
    console.log(JSON.stringify(report));
  } finally {
    await connection.close();
  }
}

if (require.main === module) runCli(main);
module.exports = { main };