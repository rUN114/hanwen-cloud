export interface ImportRow {
  username: string;
  displayName: string;
}
export function parseRoster(text: string): ImportRow[] {
  if (new TextEncoder().encode(text).length > 100000) throw Error("名册请控制在100KB以内");
  text = text.replace(/^\uFEFF/, "");
  const lines: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;
  let closed = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          field += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else field += c;
      continue;
    }
    if (c === '"') {
      if (field || closed) throw Error("引号位置不正确，请使用两列CSV名册");
      quoted = true;
      continue;
    }
    if (c === "," || c === "\n" || c === "\r") {
      row.push(field.trim());
      field = "";
      closed = false;
      if (c !== ",") {
        if (row.some(Boolean)) lines.push(row);
        row = [];
        if (c === "\r" && text[i + 1] === "\n") i++;
      }
    } else {
      if (closed && !/\s/.test(c)) throw Error("引号后的内容不正确");
      if (!closed) field += c;
    }
  }
  if (quoted) throw Error("名册有未闭合的引号");
  row.push(field.trim());
  if (row.some(Boolean)) lines.push(row);
  if (
    lines[0]?.length === 2 &&
    ["账号", "username"].includes(lines[0][0].toLowerCase()) &&
    ["姓名", "显示名", "displayname"].includes(lines[0][1].toLowerCase())
  )
    lines.shift();
  if (!lines.length || lines.length > 50) throw Error("每批需要1至50人，请分批开户");
  const seen = new Set<string>();
  return lines.map((cells, i) => {
    if (cells.length !== 2) throw Error("第" + (i + 1) + "行必须只有账号和姓名两列");
    const username = cells[0].toLowerCase();
    const displayName = cells[1];
    if (!/^[a-z0-9_.-]{3,50}$/.test(username)) throw Error("第" + (i + 1) + "行账号格式不正确");
    if (!displayName || [...displayName].length > 50)
      throw Error("第" + (i + 1) + "行姓名须为1至50字");
    if (seen.has(username)) throw Error("重复账号：" + username);
    seen.add(username);
    return { username, displayName };
  });
}
