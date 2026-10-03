"use strict";

const WORK_TIERS = Object.freeze([
  { tier: 1, name: "Empregos Iniciais", level: 1, salaryMinCents: 400, salaryMaxCents: 800, shiftsToPromote: 12 },
  { tier: 2, name: "Empregos Intermediários", level: 10, salaryMinCents: 1_500, salaryMaxCents: 3_000, shiftsToPromote: 20 },
  { tier: 3, name: "Empregos Especializados", level: 25, salaryMinCents: 4_500, salaryMaxCents: 8_500, shiftsToPromote: 30 },
  { tier: 4, name: "Liderança e Gerência", level: 45, salaryMinCents: 10_000, salaryMaxCents: 18_000, shiftsToPromote: 40 },
  { tier: 5, name: "Alta Executiva e Magnatas", level: 70, salaryMinCents: 22_000, salaryMaxCents: 45_000, shiftsToPromote: 50 },
]);

const WORK_JOBS = Object.freeze([
  ["entregador_pizza", "Entregador de Pizza", 1], ["ajudante_limpeza", "Ajudante de Limpeza", 1],
  ["repositor_estoque", "Repositor de Estoque", 1], ["passeador_caes", "Passeador de Cães", 1],
  ["atendente_cafe", "Atendente de Cafeteria", 1], ["lavador_carros", "Lavador de Carros", 1],
  ["panfleteiro", "Panfleteiro de Rua", 1],
  ["vendedor_loja", "Vendedor de Loja", 2], ["mecanico_assist", "Assistente de Mecânico", 2],
  ["auxiliar_cozinha", "Auxiliar de Cozinha", 2], ["telemarketing", "Atendente de Telemarketing", 2],
  ["motorista_app", "Motorista de Aplicativo", 2], ["seguranca_eventos", "Segurança de Eventos", 2],
  ["barbeiro", "Barbeiro e Cabeleireiro", 2],
  ["desenvolvedor_ti", "Desenvolvedor de Software", 3], ["chef_cozinha", "Chef de Cozinha", 3],
  ["mecanico_chefe", "Mecânico Chefe", 3], ["fotografo_prof", "Fotógrafo Profissional", 3],
  ["designer_grafico", "Designer Gráfico", 3], ["personal_trainer", "Personal Trainer", 3],
  ["analista_financeiro", "Analista Financeiro", 3],
  ["gerente_loja", "Gerente Geral de Loja", 4], ["engenheiro_civil", "Engenheiro Civil", 4],
  ["medico_especialista", "Médico Especialista", 4], ["advogado_senior", "Advogado Sênior", 4],
  ["diretor_producao", "Diretor de Produção", 4], ["gerente_projetos", "Gerente de Projetos", 4],
  ["piloto_comercial", "Piloto Comercial", 4],
  ["ceo_executivo", "Diretor Executivo", 5], ["investidor_anjo", "Investidor Anjo", 5],
  ["cirurgiao_chefe", "Cirurgião Chefe", 5], ["juiz_federal", "Juiz Federal", 5],
  ["socio_majoritario", "Sócio Majoritário", 5], ["engenheiro_aeroespacial", "Engenheiro Aeroespacial", 5],
  ["magnata_bilionario", "Magnata Bilionário", 5],
].map(([slug, name, tier]) => {
  const tierInfo = WORK_TIERS[tier - 1];
  return Object.freeze({
    slug,
    name,
    tier,
    tierName: tierInfo.name,
    minLevel: tierInfo.level,
    salaryMinCents: tierInfo.salaryMinCents,
    salaryMaxCents: tierInfo.salaryMaxCents,
    shiftsToPromote: tierInfo.shiftsToPromote,
  });
}));

const WORK_COOLDOWN_MS = 40 * 60 * 1_000;
const WORK_SESSION_TTL_MS = 5 * 60 * 1_000;
const WORK_RESIGN_COOLDOWN_MS = 20 * 60 * 1_000;

function emptyCareer() {
  return {
    jobSlug: "",
    unlockedTier: 1,
    shiftsInTier: 0,
    lastShiftAtMs: 0,
    hiredAtMs: 0,
    resignUntilMs: 0,
    activeSessionId: "",
  };
}

function getWorkJob(slug) {
  return WORK_JOBS.find((job) => job.slug === slug) || null;
}

function canPromote(career, job, level) {
  const nextTier = WORK_TIERS[job.tier];
  return Boolean(nextTier)
    && level >= nextTier.level
    && career.shiftsInTier >= job.shiftsToPromote
    && career.unlockedTier < nextTier.tier;
}

module.exports = {
  WORK_COOLDOWN_MS,
  WORK_JOBS,
  WORK_RESIGN_COOLDOWN_MS,
  WORK_SESSION_TTL_MS,
  WORK_TIERS,
  canPromote,
  emptyCareer,
  getWorkJob,
};
