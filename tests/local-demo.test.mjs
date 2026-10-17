import test from "node:test";
import assert from "node:assert/strict";
import { Readable } from "node:stream";
import { localDemo } from "../frontend/local-demo.mjs";

async function request({
  gateway = "http://127.0.0.1:8088",
  host = "127.0.0.1:5173",
  origin = "http://127.0.0.1:5173",
  remote = "127.0.0.1",
  method = "POST",
  body = '{"role":"ADMIN"}',
  site = "same-origin",
} = {}) {
  let handler;
  localDemo(gateway).configureServer({
    middlewares: {
      use(fn) {
        handler = fn;
      },
    },
  });
  const req = Readable.from([body]);
  Object.assign(req, {
    url: "/__local-demo/login",
    method,
    headers: { host, origin, "content-type": "application/json", "sec-fetch-site": site },
    socket: { remoteAddress: remote },
  });
  if (origin === null) delete req.headers.origin;
  const res = {
    statusCode: 200,
    setHeader() {},
    end(value) {
      this.body = JSON.parse(value);
    },
  };
  await handler(req, res, () => {
    throw new Error("unexpected next");
  });
  return res.statusCode;
}
test("development only; production build and preview excluded", () => {
  const plugin = localDemo("http://127.0.0.1:8088");
  assert.equal(plugin.apply({}, { command: "build" }), false);
  assert.equal(plugin.apply({}, { command: "serve", isPreview: true }), false);
  assert.equal(plugin.apply({}, { command: "serve", isPreview: false }), true);
});
test("reject external Host and non-loopback connections", async () => {
  assert.equal(await request({ host: "attacker.example:5173" }), 403);
  assert.equal(await request({ remote: "192.168.1.5" }), 403);
});
test("reject cross-origin and missing-origin POST", async () => {
  assert.equal(await request({ origin: "https://attacker.example" }), 403);
  assert.equal(await request({ origin: null }), 403);
  assert.equal(await request({ origin: "" }), 403);
  assert.equal(await request({ site: "cross-site" }), 403);
});
test("never forward private credentials to a remote gateway", async () => {
  assert.equal(await request({ gateway: "https://attacker.example" }), 403);
});
test("no administrator shortcut or caller supplied credentials", async () => {
  assert.equal(await request(), 400);
  assert.equal(await request({ body: '{"username":"admin","password":"ignored"}' }), 400);
  assert.equal(await request({ body: '{"role":"__proto__"}' }), 400);
});
test("reject invalid JSON, oversized requests and wrong methods", async () => {
  assert.equal(await request({ body: "invalid" }), 400);
  assert.equal(await request({ body: "x".repeat(1025) }), 413);
  assert.equal(await request({ method: "GET" }), 405);
});
