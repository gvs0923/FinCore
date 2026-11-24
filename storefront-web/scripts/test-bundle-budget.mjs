import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { randomBytes } from "node:crypto";
import { gzipSync } from "node:zlib";

const script = fileURLToPath(new URL("./check-bundle-budget.mjs", import.meta.url));

function run(manifest, files) {
  const cwd = mkdtempSync(join(tmpdir(), "fincore-budget-"));
  try {
    mkdirSync(join(cwd, "dist/.vite"), { recursive: true });
    writeFileSync(join(cwd, "dist/.vite/manifest.json"), JSON.stringify(manifest));
    for (const [file, content] of Object.entries(files)) writeFileSync(join(cwd, "dist", file), content);
    return spawnSync(process.execPath, [script], { cwd, encoding: "utf8" });
  } finally {
    rmSync(cwd, { recursive: true, force: true });
  }
}

const entries = {
  "index.html": { isEntry: true, file: "main.js", imports: ["shared", "shared"], dynamicImports: ["lazy"], css: ["style.css"] },
  "admin.html": { isEntry: true, file: "admin.js", imports: ["shared"] },
  shared: { file: "shared.js", imports: ["shared"] },
  lazy: { file: "lazy.js" },
};

test("counts each static chunk once per entry and excludes lazy chunks and CSS", () => {
  const main = "export const main = true;";
  const shared = randomBytes(80_000);
  const result = run(entries, { "main.js": main, "shared.js": shared, "admin.js": "", "lazy.js": randomBytes(400_000), "style.css": randomBytes(400_000) });
  assert.equal(result.status, 0, result.stderr);
  const kb = ((gzipSync(main).length + gzipSync(shared).length) / 1024).toFixed(1);
  assert.ok(result.stdout.includes(`index.html: ${kb} KB gzip`), result.stdout);
});

test("fails the shopper budget", () => {
  const result = run(entries, { "main.js": randomBytes(160_000), "shared.js": "", "admin.js": "" });
  assert.equal(result.status, 1);
  assert.ok(result.stdout.includes("budget 145 KB"));
});

test("fails the admin budget independently", () => {
  const result = run(entries, { "main.js": "", "shared.js": "", "admin.js": randomBytes(280_000) });
  assert.equal(result.status, 1);
  assert.ok(result.stdout.includes("budget 241 KB"));
});

test("fails closed on missing entries or chunks", () => {
  assert.equal(run({}, {}).status, 1);
  assert.equal(run(entries, { "main.js": "" }).status, 1);
  const broken = { ...entries, "index.html": { ...entries["index.html"], imports: ["missing"] } };
  assert.equal(run(broken, {}).status, 1);
});
