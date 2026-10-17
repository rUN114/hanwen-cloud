import assert from "node:assert/strict";
import { revisionDiff } from "../frontend/src/utils/revision-diff.ts";
let d = revisionDiff("起\n旧段\n尾", "起\n新段\n尾");
assert.equal(d.changed, true);
assert.deepEqual(
  d.after.map((x) => [x.text, x.changed]),
  [
    ["起", false],
    ["新段", true],
    ["尾", false],
  ],
);
d = revisionDiff("相同", "相同");
assert.equal(d.changed, false);
assert.equal(
  d.after.some((x) => x.changed),
  false,
);
d = revisionDiff("起\n尾", "起\n新增\n尾");
assert.equal(d.after.find((x) => x.changed).text, "新增");
assert.equal(
  d.before.some((x) => x.changed),
  false,
);
d = revisionDiff("旧段", "");
assert.equal(d.before[0].changed, true);
assert.equal(d.after.length, 0);
d = revisionDiff("", "新段");
assert.equal(d.after[0].text, "新段");
console.log("Revision comparison: five meaningful scenarios PASS.");
