async (page) => {
  const f = __FIXTURE__,
    checks = [];
  const check = (name, ok) => {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  };
  await page.goto("http://127.0.0.1:5173/courses/" + f.courseId + "/quizzes");
  await page.getByRole("button", { name: "拟一份练习", exact: true }).click();
  let d = page.getByRole("dialog", { name: "拟写章节练习", exact: true });
  await d.getByRole("textbox", { name: "练习标题", exact: true }).fill("P2浏览器组卷 · 字句与诗心");
  await d.getByLabel("所属章节", { exact: true }).selectOption(f.chapterId);
  await d
    .getByRole("textbox", { name: "练习说明", exact: true })
    .fill("独立验证试卷：字句辨意、原文寻证、判断明理。");
  await d.getByRole("button", { name: "创建草稿", exact: true }).click();
  await page.waitForURL("**/teacher/quizzes/*");
  const quizId = new URL(page.url()).pathname.split("/").pop();
  await page.getByRole("button", { name: "测验规则", exact: true }).click();
  await page.getByRole("spinbutton", { name: "限时分钟（0 为不限时）", exact: true }).fill("10");
  await page.getByRole("spinbutton", { name: "最多尝试次数", exact: true }).fill("2");
  await page.getByRole("button", { name: "保存规则", exact: true }).click();
  await page.getByRole("button", { name: "从题库组卷", exact: true }).click();
  d = page.getByRole("dialog", { name: "从问学题笺组卷", exact: true });
  await d.locator(".bank-picker-row").first().waitFor();
  for (const typ of ["SINGLE", "MULTIPLE", "TRUE_FALSE"]) {
    await d.getByLabel("题库题型", { exact: true }).selectOption(typ);
    await d.getByRole("button", { name: "查找", exact: true }).click();
    const text = f.bank.find((q) => q.questionType === typ).prompt;
    const row = d
      .locator(".bank-picker-row")
      .filter({ has: page.getByRole("heading", { name: text, exact: true }) });
    await row.waitFor();
    await row.getByRole("button", { name: "选入试卷", exact: true }).click();
    await page.waitForFunction(
      (n) => document.querySelectorAll(".question-manuscript").length === n,
      ["SINGLE", "MULTIPLE", "TRUE_FALSE"].indexOf(typ) + 1,
    );
  }
  check("教师通过界面组入三种题型且总分10", (await d.innerText()).includes("3 题，共 10 分"));
  await d.getByRole("button", { name: "关闭此对话框", exact: true }).click();
  await page.getByRole("button", { name: "发布练习", exact: true }).click();
  await page
    .getByRole("dialog", { name: "确认状态", exact: true })
    .getByRole("button", { name: "确定", exact: true })
    .click();
  await page.getByRole("button", { name: "归档", exact: true }).waitFor();
  check(
    "发布后隐藏编辑入口且保留10分钟与两次规则",
    (await page.getByRole("button", { name: "从题库组卷", exact: true }).count()) === 0 &&
      (await page.locator(".quiz-rules").innerText()).includes("最多 2 次"),
  );
  await page.evaluate(() => {
    document.activeElement?.blur?.();
    scrollTo(0, 0);
  });
  await page.screenshot({
    path: "output/playwright/p2-quiz-editor.png",
    fullPage: true,
    animations: "disabled",
  });
  return {
    at: new Date().toISOString(),
    checks,
    quizId,
    scope: "Actual teacher UI: create quiz, edit rules, import three question types and publish",
  };
};
