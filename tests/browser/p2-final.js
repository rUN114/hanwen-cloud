async (page) => {
  const f = __FIXTURE__,
    checks = [];
  const check = (name, ok) => {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  };
  for (const [title, text] of [
    ["尚未开放验证", "练习尚未开放，请按开放时间进入。"],
    ["已截止验证", "练习已截止，不能继续作答。"],
    ["真实截止时间验证", "第 1 次作答已超时，未生成已提交成绩。"],
  ]) {
    await page.goto("http://127.0.0.1:5173/practice/" + f.validationQuizzes[title]);
    await page.getByText(text, { exact: true }).waitFor();
    check(
      title + "在界面显示实际状态且没有开始按钮",
      (await page.getByRole("button", { name: "开始测验", exact: true }).count()) === 0,
    );
  }
  await page.goto("http://127.0.0.1:5173/practice/" + f.validationQuizzes["归档期间拒绝交卷验证"]);
  await page.getByText("测验已停止接收作答，本页选择只供查看。", { exact: true }).waitFor();
  check(
    "已开始后归档的测验界面禁止输入和交卷",
    (await page.locator("fieldset input:not(:disabled)").count()) === 0 &&
      (await page.getByRole("button", { name: "确认交卷", exact: true }).isDisabled()),
  );
  await page.goto("http://127.0.0.1:5173/practice/" + f.browserQuizId);
  await page.getByRole("button", { name: "开始测验", exact: true }).click();
  await page
    .getByRole("dialog", { name: "开始测验", exact: true })
    .getByRole("button", { name: "确定", exact: true })
    .click();
  await page.locator("fieldset.question-manuscript").nth(2).waitFor();
  const field = page.locator("fieldset.question-manuscript").nth(0);
  await field.locator('input[value="A"]').check();
  await page.waitForFunction(
    () => document.querySelector(".draft-status")?.textContent === "答案已保存",
  );
  // Use only the automation context's current authenticated student's session; never print the token.
  const token = await page.evaluate(
    () => JSON.parse(sessionStorage.getItem("hw:session") || "null")?.accessToken,
  );
  if (!token) throw Error("Authenticated test student session missing");
  const actual = await (
    await page.request.get("http://127.0.0.1:5173/api/practices/" + f.browserQuizId + "/state", {
      headers: { Authorization: "Bearer " + token },
    })
  ).json();
  const active = actual.data.active;
  const edit = await page.request.put(
    "http://127.0.0.1:5173/api/practices/" + f.browserQuizId + "/runs/" + active.id + "/draft",
    {
      headers: { Authorization: "Bearer " + token },
      data: {
        answers: [{ questionId: active.questions[0].id, choice: "B" }],
        expectedVersion: active.version,
      },
    },
  );
  check("另一个实际请求写入较新草稿成功", edit.ok());
  await field.locator('input[value="C"]').check();
  await page.getByText("内容已更新，请重新查看后再操作", { exact: true }).waitFor();
  check("旧页面遇到版本冲突保留未保存选择", await field.locator('input[value="C"]').isChecked());
  await page.getByRole("button", { name: "重新载入已保存答案", exact: true }).click();
  await page
    .getByRole("dialog", { name: "重新载入答案", exact: true })
    .getByRole("button", { name: "确定", exact: true })
    .click();
  await page.waitForFunction(() => document.querySelector('fieldset input[value="B"]')?.checked);
  check("明确确认后重新载入较新服务器答案", await field.locator('input[value="B"]').isChecked());
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    check(
      width + "px最终答题页及长姓名无横向溢出",
      await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    );
    check(
      width + "px姓名与头像保持在页头内",
      await page
        .locator(".user-chip")
        .evaluate((e) => e.getBoundingClientRect().right <= innerWidth),
    );
    await page.evaluate(() => {
      document.activeElement?.blur?.();
      scrollTo(0, 0);
    });
    await page.screenshot({
      path: "output/playwright/p2-practice-final-" + width + ".png",
      fullPage: true,
      animations: "disabled",
    });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  return {
    at: new Date().toISOString(),
    checks,
    scope:
      "Final archived/deadline UI and real optimistic draft version conflict using the same test student.",
  };
};
