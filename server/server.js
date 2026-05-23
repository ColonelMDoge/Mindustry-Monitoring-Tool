const express = require("express");
const crypto = require("crypto");
const app = express();
app.use(express.json());

const SECRET = process.env.API_KEY;
const PORT = process.env.PORT;

let users = {};
let host = null;
let serverIP = null;

const log = (msg) => {
    const ts = new Date().toLocaleString('sv', { timeZone: 'America/Toronto' });
    console.log(`[${ts}] ${msg}`);
};

const auth = (req, res, next) => {
    if (!SECRET || req.headers["api-key"] !== SECRET) {
        return res.status(401).send({ error: "Unauthorized" });
    }
    next();
};

app.post("/heartbeat", auth, (req, res) => {
    const { user, token } = req.body || {};
    if (!user) return res.status(400).send({ error: "Missing user" });
    const existing = users[user];
    if (!existing || !existing.active) {
        return res.status(401).send({ error: "User not active. Register via /status first." });
    }
    if (existing.token && existing.token !== token) {
        log(`[HEARTBEAT REJECTED] ${user} — invalid token`);
        return res.status(403).send({ error: "Invalid session token" });
    }
    users[user].lastSeen = Date.now();
    res.send({
        status: "alive",
        host,
        isHost: host === user
    });
});

app.post("/status", auth, (req, res) => {
    const { user, active, token, ip } = req.body || {};
    if (!user) return res.status(400).send({ error: "Missing user" });
    const existing = users[user];
    const now = Date.now();
    const wasActive = existing?.active || false;
    if (active) {
        if (wasActive && existing.token && existing.token !== token) {
            log(`[REJECTED] ${user} — session collision`);
            return res.status(409).send({ error: "Username already active in another session" });
        }
    }
    const userToken = token || existing?.token || crypto.randomUUID();
    users[user] = {
        active: !!active,
        lastSeen: now,
        token: userToken
    };
    if (!wasActive && active)  log(`[ONLINE]  ${user}`);
    if (wasActive && !active)  log(`[OFFLINE] ${user}`);
    if (user === host && !active) {
        log(`[HOST LEFT] ${host}. Host position is now empty.`);
        host = null;
        serverIP = null;
    }
    if (active && (!host || !users[host] || !users[host].active)) {
        host = user;
        serverIP = ip;
        log(`[HOST ASSIGNED] ${host}`);
    }
    const onlineUsers = Object.keys(users).filter(u => users[u].active);
    res.send({
        someoneOnline: onlineUsers.some(u => u !== user),
        onlineUsers,
        host,
        token: userToken,
        serverIP
    });
});

app.get("/status", auth, (req, res) => {
    const safe = Object.fromEntries(
        Object.entries(users).map(([u, v]) => [u, { active: v.active, lastSeen: v.lastSeen }])
    );
    res.json({ users: safe, host });
});

setInterval(() => {
    const now = Date.now();
    for (const u of Object.keys(users)) {
        if (users[u].active && now - users[u].lastSeen > 15_000) {
            log(`[TIMEOUT] ${u} missed heartbeats.`);
            users[u].active = false;
            if (host === u) {
                log(`[HOST TIMEOUT] ${host} disconnected. Host position is now empty.`);
                host = null;
                serverIP = null;
            }
        }
        if (!users[u].active && now - users[u].lastSeen > 60_000) {
            log(`[PURGED] ${u} removed from memory.`);
            delete users[u];
        }
    }
}, 15_000);

app.listen(PORT, () => log(`Presence server running on port ${PORT}`));