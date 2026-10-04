"use strict";

const LEGACY_FINANCIAL_CALLABLES = new Set();

function assertFinancialCallableAvailable(name, mode = process.env.ZECA_FINANCIAL_MODE) {
  if (mode !== "mongo" || !LEGACY_FINANCIAL_CALLABLES.has(name)) return;
  const error = new Error("Esta operação está temporariamente indisponível durante a migração da carteira.");
  error.name = "UnmigratedFinancialCallableError";
  error.code = "unavailable";
  error.reason = "financial-handler-not-migrated";
  throw error;
}

function assertMinimumAppVersion({ mode = process.env.ZECA_FINANCIAL_MODE, minimum, client }) {
  if (mode !== "mongo") return;
  if (!Number.isSafeInteger(minimum) || minimum < 1) {
    const error = new Error("A versão mínima do aplicativo não foi configurada no backend.");
    error.code = "unavailable";
    error.reason = "app-version-policy-unconfigured";
    throw error;
  }
  if (!Number.isSafeInteger(client) || client < minimum) {
    const error = new Error("Atualize o Zeca para continuar usando a carteira.");
    error.code = "failed-precondition";
    error.reason = "app-version-unsupported";
    throw error;
  }
}

module.exports = {
  LEGACY_FINANCIAL_CALLABLES,
  assertFinancialCallableAvailable,
  assertMinimumAppVersion,
};