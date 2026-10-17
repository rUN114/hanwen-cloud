import fs from "node:fs";
import assert from "node:assert/strict";
import ts from "../frontend/node_modules/typescript/lib/typescript.js";
const root = new URL("../", import.meta.url);
const source = fs
  .readFileSync(new URL("frontend/src/utils/export.ts", root), "utf8")
  .replace(/import \{ api \} from "\.\.\/api\/client";/, "");
const code = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
const { csv, csvCell } = await import(
  "data:text/javascript;base64," + Buffer.from(code).toString("base64")
);
assert.equal(csvCell('汉文,"古典"'), '"汉文,""古典"""');
assert.equal(csvCell("=SUM(A1:A2)"), '"\'=SUM(A1:A2)"');
assert.equal(csvCell("  @命令"), '"\'  @命令"');
assert.equal(csvCell("\t=命令"), '"\'\t=命令"');
assert.equal(csvCell("-2"), '"\'-2"');
assert.equal(csvCell("16.00"), '"16.00"');
const text = csv([
  ["姓名", "札记"],
  ["李禾", "第一行\n第二行"],
]);
assert.ok(text.startsWith("\ufeff"));
assert.ok(text.includes('"李禾","第一行\n第二行"'));
assert.equal(text.split("\r\n").length, 2);
fs.mkdirSync(new URL("evidence/", root), { recursive: true });
fs.writeFileSync(
  new URL("evidence/v2-export-tests.json", root),
  JSON.stringify(
    {
      result: "PASS",
      assertions: 9,
      scope: "实际 CSV 编码函数：中文 BOM、引号、换行、公式注入防护；非表格软件兼容性全测",
      at: new Date().toISOString(),
    },
    null,
    2,
  ),
);
console.log("CSV actual helper: 9 assertions PASS");
