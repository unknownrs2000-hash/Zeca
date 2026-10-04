"use strict";

function runCli(main) {
  main().catch((error) => {
    console.error(error instanceof SyntaxError ? "Configuração inválida (JSON malformado)." : error.message);
    process.exitCode = 1;
  });
}

function optionValue(argv, name) {
  const index = argv.indexOf(name);
  const value = index >= 0 ? argv[index + 1] : "";
  return value && !value.startsWith("--") ? value : "";
}

module.exports = { optionValue, runCli };