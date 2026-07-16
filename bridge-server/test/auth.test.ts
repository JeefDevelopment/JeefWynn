import assert from "node:assert/strict";
import test from "node:test";
import { issueToken, verifyToken } from "../src/auth.js";

const secret = "a-very-long-test-secret-that-is-not-production";
const identity = {
  discordId: "123",
  discordName: "Guild Member",
  minecraftUuid: "00112233445566778899aabbccddeeff",
  minecraftName: "Jeef",
};

test("round trips a signed identity", () => {
  const verified = verifyToken(issueToken(identity, secret), secret);
  assert.ok(verified);
  assert.equal(typeof verified.issuedAt, "number");
  assert.deepEqual({ ...verified, issuedAt: 0 }, { v: 1, issuedAt: 0, ...identity });
});

test("rejects tampering and the wrong secret", () => {
  const token = issueToken(identity, secret);
  assert.equal(verifyToken(`${token}x`, secret), null);
  assert.equal(verifyToken(token, "another-secret"), null);
});
