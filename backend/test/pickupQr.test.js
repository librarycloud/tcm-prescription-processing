import assert from "node:assert/strict";
import test from "node:test";
import {
  parsePickupQrContent,
  pickupQrContent,
} from "../src/utils/pickupQr.js";

test("package pickup QR content signs and validates the package identity", () => {
  const pkg = { id: 123, pickupCode: "123456" };
  const content = pickupQrContent(pkg);

  assert.match(content, /^TCM:PICKUP:1:123:123456:[A-Za-z0-9_-]+$/);
  assert.deepEqual(parsePickupQrContent(content), {
    packageId: 123,
    pickupCode: "123456",
  });
});

test("tampered or legacy pickup QR content is rejected by the signed parser", () => {
  const content = pickupQrContent({ id: 123, pickupCode: "123456" });

  assert.equal(parsePickupQrContent(content.replace(":123456:", ":123457:")), null);
  assert.equal(parsePickupQrContent("123456"), null);
});
