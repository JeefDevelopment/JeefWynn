import { createHmac, timingSafeEqual } from "node:crypto";

export interface BridgeIdentity {
  v: 1;
  discordId: string;
  discordName: string;
  minecraftUuid: string;
  minecraftName: string;
  issuedAt: number;
}

function encode(value: string | Buffer): string {
  return Buffer.from(value).toString("base64url");
}

export function issueToken(identity: Omit<BridgeIdentity, "v" | "issuedAt">, secret: string): string {
  const payload: BridgeIdentity = { v: 1, issuedAt: Date.now(), ...identity };
  const body = encode(JSON.stringify(payload));
  const signature = encode(createHmac("sha256", secret).update(body).digest());
  return `${body}.${signature}`;
}

export function verifyToken(token: string, secret: string): BridgeIdentity | null {
  const [body, suppliedSignature, extra] = token.split(".");
  if (!body || !suppliedSignature || extra) return null;

  const expected = createHmac("sha256", secret).update(body).digest();
  let supplied: Buffer;
  try {
    supplied = Buffer.from(suppliedSignature, "base64url");
  } catch {
    return null;
  }
  if (supplied.length !== expected.length || !timingSafeEqual(supplied, expected)) return null;

  try {
    const payload = JSON.parse(Buffer.from(body, "base64url").toString("utf8")) as BridgeIdentity;
    if (
      payload.v !== 1 ||
      typeof payload.discordId !== "string" ||
      typeof payload.discordName !== "string" ||
      typeof payload.minecraftUuid !== "string" ||
      typeof payload.minecraftName !== "string" ||
      typeof payload.issuedAt !== "number"
    ) return null;
    return payload;
  } catch {
    return null;
  }
}
