import { randomBytes, randomInt, randomUUID } from "node:crypto";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import {
  Client,
  GatewayIntentBits,
  SlashCommandBuilder,
} from "discord.js";
import { WebSocket, WebSocketServer } from "ws";
import { issueToken, verifyToken, type BridgeIdentity } from "./auth.js";

const PORT = Number(process.env.PORT ?? "8080");
const DISCORD_TOKEN = required("DISCORD_TOKEN");
const DISCORD_GUILD_ID = required("DISCORD_GUILD_ID");
const DISCORD_CHANNEL_ID = required("DISCORD_CHANNEL_ID");
const TOKEN_SECRET = required("TOKEN_SECRET", 32);

interface PendingLink {
  pollId: string;
  code: string;
  minecraftUuid: string;
  minecraftName: string;
  expiresAt: number;
  token?: string;
}

interface ClientSocket extends WebSocket {
  identity?: BridgeIdentity;
  recentMessages?: number[];
}

const pendingByCode = new Map<string, PendingLink>();
const pendingByPollId = new Map<string, PendingLink>();
const sockets = new Set<ClientSocket>();
const webSockets = new WebSocketServer({ noServer: true, maxPayload: 8_192 });
const discord = new Client({ intents: [
  GatewayIntentBits.Guilds,
  GatewayIntentBits.GuildMessages,
  GatewayIntentBits.MessageContent,
] });

const server = createServer(async (request, response) => {
  try {
    await route(request, response);
  } catch (error) {
    console.error(error);
    json(response, 500, { error: "internal_error" });
  }
});

server.on("upgrade", (request, socket, head) => {
  if (new URL(request.url ?? "/", "http://localhost").pathname !== "/ws") {
    socket.destroy();
    return;
  }
  const token = bearerToken(request);
  const identity = token ? verifyToken(token, TOKEN_SECRET) : null;
  if (!identity) {
    socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n");
    socket.destroy();
    return;
  }
  webSockets.handleUpgrade(request, socket, head, (rawSocket) => {
    const clientSocket = rawSocket as ClientSocket;
    clientSocket.identity = identity;
    webSockets.emit("connection", clientSocket, request);
  });
});

webSockets.on("connection", (socket: ClientSocket) => {
  sockets.add(socket);
  socket.recentMessages = [];
  socket.send(JSON.stringify({ type: "ready", minecraftName: socket.identity?.minecraftName }));
  socket.on("close", () => sockets.delete(socket));
  socket.on("message", async (data) => {
    const identity = socket.identity;
    if (!identity || !withinRateLimit(socket)) return;
    let parsed: unknown;
    try { parsed = JSON.parse(data.toString()); } catch { return; }
    if (!isChatPayload(parsed)) return;

    const channel = await discord.channels.fetch(DISCORD_CHANNEL_ID);
    if (!channel?.isSendable()) return;
    await channel.send({
      content: `**${escapeMarkdown(identity.minecraftName)}**: ${escapeMentions(parsed.content)}`,
      allowedMentions: { parse: [] },
    });
  });
});

discord.once("ready", async () => {
  const guild = await discord.guilds.fetch(DISCORD_GUILD_ID);
  const commands = [new SlashCommandBuilder()
    .setName("link")
    .setDescription("Link your Discord account to the JeefWynn modpack")
    .addStringOption((option) => option.setName("code").setDescription("Code shown in Minecraft").setRequired(true))
    .toJSON()];
  await guild.commands.set(commands);
  console.log(`Discord bridge signed in as ${discord.user?.tag}`);
});

discord.on("interactionCreate", async (interaction) => {
  if (!interaction.isChatInputCommand() || interaction.commandName !== "link") return;
  if (interaction.guildId !== DISCORD_GUILD_ID) {
    await interaction.reply({ content: "Use this command in the configured guild.", ephemeral: true });
    return;
  }
  const code = interaction.options.getString("code", true).trim().toUpperCase();
  const pending = pendingByCode.get(code);
  if (!pending || pending.expiresAt < Date.now()) {
    await interaction.reply({ content: "That code is invalid or expired. Run `/d link` in Minecraft again.", ephemeral: true });
    return;
  }
  pending.token = issueToken({
    discordId: interaction.user.id,
    discordName: interaction.user.globalName ?? interaction.user.username,
    minecraftUuid: pending.minecraftUuid,
    minecraftName: pending.minecraftName,
  }, TOKEN_SECRET);
  pendingByCode.delete(code);
  await interaction.reply({ content: `Linked to Minecraft player **${escapeMarkdown(pending.minecraftName)}**. You can return to the game.`, ephemeral: true });
});

discord.on("messageCreate", (message) => {
  if (message.channelId !== DISCORD_CHANNEL_ID || message.author.bot || !message.content.trim()) return;
  broadcast({
    type: "discord_message",
    author: message.member?.displayName ?? message.author.globalName ?? message.author.username,
    content: message.content.slice(0, 500),
  });
});

setInterval(cleanExpiredLinks, 60_000).unref();

await discord.login(DISCORD_TOKEN);
server.listen(PORT, "0.0.0.0", () => console.log(`Bridge listening on :${PORT}`));

async function route(request: IncomingMessage, response: ServerResponse): Promise<void> {
  const url = new URL(request.url ?? "/", "http://localhost");
  if (request.method === "GET" && url.pathname === "/health") {
    json(response, 200, { ok: true, discordReady: discord.isReady(), clients: sockets.size });
    return;
  }
  if (request.method === "POST" && url.pathname === "/api/link/start") {
    const body = await readJson(request);
    if (!isLinkStart(body)) {
      json(response, 400, { error: "invalid_player" });
      return;
    }
    const code = uniqueCode();
    const link: PendingLink = {
      pollId: randomUUID(),
      code,
      minecraftUuid: body.minecraftUuid.replaceAll("-", "").toLowerCase(),
      minecraftName: body.minecraftName,
      expiresAt: Date.now() + 10 * 60_000,
    };
    pendingByCode.set(code, link);
    pendingByPollId.set(link.pollId, link);
    json(response, 201, { code, pollId: link.pollId, expiresInSeconds: 600 });
    return;
  }
  if (request.method === "GET" && url.pathname === "/api/link/status") {
    const pollId = url.searchParams.get("id");
    const link = pollId ? pendingByPollId.get(pollId) : undefined;
    if (!link || link.expiresAt < Date.now()) {
      json(response, 404, { status: "expired" });
      return;
    }
    if (!link.token) {
      json(response, 200, { status: "pending" });
      return;
    }
    pendingByPollId.delete(link.pollId);
    json(response, 200, { status: "linked", token: link.token });
    return;
  }
  json(response, 404, { error: "not_found" });
}

function required(name: string, minimumLength = 1): string {
  const value = process.env[name];
  if (!value || value.length < minimumLength) throw new Error(`${name} must be set${minimumLength > 1 ? ` and at least ${minimumLength} characters` : ""}`);
  return value;
}

function isLinkStart(value: unknown): value is { minecraftUuid: string; minecraftName: string } {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.minecraftUuid === "string"
    && /^(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i.test(candidate.minecraftUuid)
    && typeof candidate.minecraftName === "string"
    && /^[A-Za-z0-9_]{1,16}$/.test(candidate.minecraftName);
}

function isChatPayload(value: unknown): value is { type: "chat"; content: string } {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return candidate.type === "chat" && typeof candidate.content === "string"
    && candidate.content.trim().length > 0 && candidate.content.length <= 500;
}

async function readJson(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let length = 0;
  for await (const chunk of request) {
    const buffer = Buffer.from(chunk);
    length += buffer.length;
    if (length > 8_192) throw new Error("request_too_large");
    chunks.push(buffer);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8")); } catch { return null; }
}

function json(response: ServerResponse, status: number, body: unknown): void {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(body));
}

function bearerToken(request: IncomingMessage): string | null {
  const value = request.headers.authorization;
  return value?.startsWith("Bearer ") ? value.slice(7) : null;
}

function uniqueCode(): string {
  let code: string;
  do { code = `${randomInt(100, 1000)}-${randomBytes(2).toString("hex").toUpperCase()}`; }
  while (pendingByCode.has(code));
  return code;
}

function cleanExpiredLinks(): void {
  const now = Date.now();
  for (const [code, link] of pendingByCode) if (link.expiresAt < now) pendingByCode.delete(code);
  for (const [id, link] of pendingByPollId) if (link.expiresAt < now) pendingByPollId.delete(id);
}

function withinRateLimit(socket: ClientSocket): boolean {
  const now = Date.now();
  const recent = (socket.recentMessages ?? []).filter((time) => now - time < 10_000);
  socket.recentMessages = recent;
  if (recent.length >= 5) return false;
  recent.push(now);
  return true;
}

function broadcast(payload: unknown): void {
  const encoded = JSON.stringify(payload);
  for (const socket of sockets) if (socket.readyState === WebSocket.OPEN) socket.send(encoded);
}

function escapeMentions(value: string): string {
  return value.replaceAll("@", "@\u200b");
}

function escapeMarkdown(value: string): string {
  return value.replace(/[\\`*_{}[\]()#+\-.!|>~]/g, "\\$&");
}
