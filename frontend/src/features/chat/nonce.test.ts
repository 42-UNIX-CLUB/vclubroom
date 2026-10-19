import { afterEach, describe, expect, it, vi } from "vitest";
import { newNonce } from "./nonce";

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

afterEach(() => vi.unstubAllGlobals());

describe("newNonce", () => {
  it("is a version-4 UUID, also where crypto.randomUUID is missing (plain HTTP)", () => {
    expect(newNonce()).toMatch(UUID_V4);
    const real = globalThis.crypto;
    vi.stubGlobal("crypto", { getRandomValues: (bytes: Uint8Array<ArrayBuffer>) => real.getRandomValues(bytes) });   // no randomUUID
    const fallback = newNonce();
    expect(fallback).toMatch(UUID_V4);
    expect(newNonce()).not.toBe(fallback);
  });
});
