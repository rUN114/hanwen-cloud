export function revisionDiff(before: string, after: string) {
  const a = before.split("\n");
  const b = after.split("\n");
  // 仅标出首尾相同行之间的改动范围，不做逐行匹配；中间未变的行也可能被高亮。
  let prefix = 0;
  let suffix = 0;
  while (prefix < Math.min(a.length, b.length) && a[prefix] === b[prefix]) prefix++;
  while (
    suffix < Math.min(a.length, b.length) - prefix &&
    a[a.length - 1 - suffix] === b[b.length - 1 - suffix]
  )
    suffix++;
  return {
    before: [
      { text: a.slice(0, prefix).join("\n"), changed: false },
      { text: a.slice(prefix, a.length - suffix).join("\n"), changed: true },
      { text: a.slice(a.length - suffix).join("\n"), changed: false },
    ].filter((x) => x.text),
    after: [
      { text: b.slice(0, prefix).join("\n"), changed: false },
      { text: b.slice(prefix, b.length - suffix).join("\n"), changed: true },
      { text: b.slice(b.length - suffix).join("\n"), changed: false },
    ].filter((x) => x.text),
    changed: before !== after,
  };
}
