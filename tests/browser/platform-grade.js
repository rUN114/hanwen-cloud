async (page) => {
  const f = __FIXTURE__,
    checks = [];
  function check(name, ok) {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  }
  await page.goto("http://127.0.0.1:5173/login");
  await page.getByRole("textbox", { name: "账号", exact: true }).fill(f.teacher.username);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(f.teacher.password);
  await page.getByRole("button", { name: "登录书院", exact: true }).click();
  await page.waitForURL("**/home");
  await page.goto("http://127.0.0.1:5173/teacher/review");
  await page.getByRole("button", { name: "批阅", exact: true }).first().waitFor();
  await page.getByRole("combobox", { name: "筛选批阅状态", exact: true }).press("Enter");
  await page.getByRole("option", { name: "全部", exact: true }).click();
  for (
    let n = 0;
    n < 100 && (await page.getByRole("combobox", { name: "筛选课程", exact: true }).isDisabled());
    n++
  )
    await page.waitForTimeout(100);
  await page.getByRole("combobox", { name: "筛选课程", exact: true }).press("Enter");
  await page.getByRole("option", { name: f.courseName, exact: true }).click();
  await page.getByText(f.uiTaskTitle, { exact: true }).first().waitFor();
  for (
    let n = 0;
    n < 50 &&
    (await page
      .locator("tr")
      .filter({ hasText: f.uiTaskTitle })
      .getByRole("button", { name: "批阅", exact: true })
      .count()) !== 2;
    n++
  )
    await page.waitForTimeout(100);
  check(
    "集中书案实际加载两位同窗答卷",
    (await page
      .locator("tr")
      .filter({ hasText: f.uiTaskTitle })
      .getByRole("button", { name: "批阅", exact: true })
      .count()) === 2,
  );
  await page.screenshot({ path: f.output + "/platform-review-desk.png", fullPage: true });
  await page
    .locator("tr")
    .filter({ hasText: f.uiTaskTitle })
    .getByRole("button", { name: "批阅", exact: true })
    .first()
    .click();
  await page.getByLabel("原文理解得分", { exact: true }).fill("50");
  await page.getByLabel("论证表达得分", { exact: true }).fill("30");
  await page.getByLabel("评语", { exact: true }).fill("");
  await page.getByRole("button", { name: "原文证据充分，分析紧扣语境。", exact: true }).click();
  await page
    .getByLabel("评语", { exact: true })
    .fill("原文证据充分，分析紧扣语境。核对 " + Date.now());
  check(
    "分项合计自动显示80分",
    await page.getByText("合计 80.00 / 100.00", { exact: true }).isVisible(),
  );
  await page.getByRole("button", { name: "下一份", exact: true }).click();
  await page.getByRole("dialog", { name: "离开答卷" }).waitFor();
  await page.getByRole("button", { name: "继续批阅", exact: true }).click();
  check(
    "连续批阅取消离开保留未保存朱批",
    (await page.getByLabel("原文理解得分").inputValue()) === "50",
  );
  await page.getByRole("button", { name: /^保存(评分|更正)$/ }).click();
  await page
    .getByRole("dialog", { name: "确认评分" })
    .getByRole("button", { name: "保存评分", exact: true })
    .click();
  await page.getByText("评分已保存", { exact: true }).waitFor();
  await page.getByText("分项得分：50 / 30（按题目细则顺序）", { exact: true }).first().waitFor();
  check("保存评分与分项留痕通过真实页面", true);
  await page.screenshot({ path: f.output + "/platform-grading.png", fullPage: true });
  await page.getByRole("button", { name: "下一份", exact: true }).click();
  await page.getByRole("heading", { name: "验收同窗乙", exact: true }).waitFor();
  await page.getByLabel("原文理解得分").fill("45");
  await page.getByLabel("论证表达得分").fill("25");
  await page
    .getByLabel("评语", { exact: true })
    .fill("观点明确，请进一步引用诗句并解释动作与情感的联系。");
  await page.getByRole("button", { name: /^保存(评分|更正)$/ }).click();
  await page
    .getByRole("dialog", { name: "确认评分" })
    .getByRole("button", { name: "保存评分", exact: true })
    .click();
  await page.getByText("评分已保存", { exact: true }).waitFor();
  check("队列切换第二份答卷并独立评分", true);
  await page.goto("http://127.0.0.1:5173/courses/" + f.courseId + "/gradebook");
  await page.getByRole("combobox", { name: "缺交处理", exact: true }).press("Enter");
  await page.getByRole("option", { name: "排除缺交项", exact: true }).click();
  await page.getByRole("combobox", { name: "重试成绩", exact: true }).press("Enter");
  await page.getByRole("option", { name: "最高得分率", exact: true }).click();
  await page.getByRole("button", { name: "保存规则", exact: true }).click();
  await page.getByText("86.70", { exact: true }).waitFor();
  check("教师通过页面修改总评口径并得到86.70", true);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出成绩册", exact: true }).click();
  await download;
  check("成绩册页面实际产生CSV下载", true);
  await page.screenshot({ path: f.output + "/platform-gradebook.png", fullPage: true });
  return { checks };
};
