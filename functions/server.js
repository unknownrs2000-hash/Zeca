"use strict";

const express = require("express");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");
const handlers = require("./index");
const { HttpsError, STATUS } = require("./callable");

const app = express();
app.use(express.json({ limit: "20kb" }));

app.get("/", (_req, res) => res.send("zeca-server ok"));

app.post("/call/:name", async (req, res) => {
  const { name } = req.params;
  if (!Object.hasOwn(handlers, name)) {
    return res.status(404).json({ error: { code: "not-found", message: "Função não encontrada." } });
  }
  try {
    const header = req.headers.authorization || "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : null;
    let auth = null;
    if (token) {
      try {
        const decoded = await getAuth().verifyIdToken(token);
        auth = { uid: decoded.uid, token: decoded };
      } catch {
        throw new HttpsError("unauthenticated", "Sessão inválida. Entre novamente.");
      }
      const profile = await getFirestore().collection("users").doc(auth.uid).get();
      if (profile.exists && profile.get("isBlocked") === true) {
        throw new HttpsError("permission-denied", "Esta conta está desativada.");
      }
    }
    const result = await handlers[name]({ auth, data: req.body?.data ?? {} });
    res.json({ result });
  } catch (error) {
    const known = error instanceof HttpsError;
    if (!known) console.error(error);
    const code = known ? error.code : "internal";
    res.status(STATUS[code] || 500).json({
      error: { code, message: known ? error.message : "Erro interno." },
    });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log(`zeca-server na porta ${port}`));