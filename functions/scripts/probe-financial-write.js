"use strict";

const { randomUUID } = require("node:crypto");
const { connectFinancialMongo } = require("../financial-mongo");
const { runCli } = require("./cli");

async function main(argv = process.argv, env = process.env) {
  if (!argv.includes("--confirm-write")) {
    throw new Error("Use --confirm-write. Não rode com app e bot em atividade.");
  }
  const connection = await connectFinancialMongo({ env, ensureIndexes: false });
  const probeId = `probe:${randomUUID()}`;
  const probes = connection.database.collection("financialProbes");
  let readBack = false;
  let cleanedUp = false;
  try {
    await probes.insertOne({ _id: probeId, createdAtMs: Date.now() }, { writeConcern: { w: "majority" } });
    readBack = (await probes.findOne({ _id: probeId })) !== null;
  } finally {
    try {
      cleanedUp = (await probes.deleteOne({ _id: probeId })).deletedCount === 1;
    } catch {
      cleanedUp = false;
    }
    await connection.close();
  }
  console.log(JSON.stringify({ probeId, readBack, cleanedUp }));
  if (!readBack || !cleanedUp) {
    throw new Error(`Probe incompleto; inspecione manualmente o documento ${probeId}.`);
  }
}

if (require.main === module) runCli(main);
module.exports = { main };