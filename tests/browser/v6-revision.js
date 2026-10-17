async (page) => {
  const f = __FIXTURE__,
    checks = [],
    errors = [];
  function check(name, ok) {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  }
  page.on("pageerror", (e) => errors.push(e.message));
  async function login(x) {
    await page.goto("http://127.0.0.1:5173/login");
    await page.getByRole("textbox", { name: "账号", exact: true }).fill(x.username);
    await page.getByRole("textbox", { name: "密码", exact: true }).fill(x.password);
    await page.getByRole("button", { name: "登录书院", exact: true }).click();
    await page.waitForURL("**/home");
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await login(f.students[0]);
  await page.goto("http://127.0.0.1:5173/student/submissions/" + f.submissionId);
  await page.getByRole("textbox", { name: "修订正文", exact: true }).waitFor();
  check(
    "学生实际看到先生修订邀请",
    await page
      .getByText("请比较“月光”和“故乡”两处意象，再补充一段分析。", { exact: true })
      .isVisible(),
  );
  const body =
    "月光令诗人想到故乡。\n“举头望明月”与“低头思故乡”通过视线变化连接月色与乡思。\n月光是眼前可见的景物，故乡是内心思念的归处，二者借动作联系起来。";
  await page.getByRole("textbox", { name: "修订正文", exact: true }).fill(body);
  await page
    .getByRole("textbox", { name: "修改说明", exact: true })
    .fill("比较眼前景物与内心思念，并补充原文依据。");
  await page.reload();
  await page.getByRole("textbox", { name: "修订正文", exact: true }).waitFor();
  check(
    "刷新后修订草稿按本人账号恢复",
    (await page.getByRole("textbox", { name: "修订正文", exact: true }).inputValue()) === body,
  );
  check(
    "草稿修改说明也恢复",
    (await page.getByRole("textbox", { name: "修改说明", exact: true }).inputValue()).includes(
      "比较眼前景物",
    ),
  );
  await page.getByRole("button", { name: "提交修订稿", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "提交修订稿", exact: true }).click();
  await page.getByRole("heading", { name: "本轮：待先生评阅", exact: true }).waitFor();
  check(
    "学生真实提交修订后停止编辑",
    (await page.getByRole("textbox", { name: "修订正文", exact: true }).count()) === 0,
  );
  check(
    "修订提交不改变正式80分",
    (await page.locator(".score-large").innerText()).includes("80.00"),
  );
  await login(f.teacher);
  await page.goto("http://127.0.0.1:5173/teacher/submissions/" + f.submissionId + "/grade");
  await page.getByRole("textbox", { name: "修订反馈", exact: true }).waitFor();
  check(
    "教师能并排核对正式原稿与修订稿",
    (await page.locator(".revision-comparison article").count()) === 2,
  );
  check(
    "修改区间同时有文字标记",
    (await page.locator(".revision-comparison .diff-changed").count()) >= 1,
  );
  await page
    .getByRole("textbox", { name: "修订反馈", exact: true })
    .fill("两处意象比较充分，认可本轮修订，原正式成绩保持。");
  await page.getByRole("button", { name: "保存修订反馈", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "保存反馈", exact: true }).click();
  await page.waitForFunction(
    () => !document.querySelector(".revision-panel form input[type=radio]"),
  );
  check(
    "教师真实评阅完成并保留历史",
    (await page.locator(".revision-timeline").innerText()).includes("两处意象比较充分"),
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: f.output + "/v6-revision-teacher.png", fullPage: true });
  await login(f.students[0]);
  await page.goto("http://127.0.0.1:5173/student/submissions/" + f.submissionId);
  await page
    .getByText("先生回笺：两处意象比较充分，认可本轮修订，原正式成绩保持。", { exact: true })
    .waitFor();
  check(
    "学生实际回看认可与完整修订正文",
    (await page.locator(".revision-comparison").innerText()).includes("月光是眼前可见的景物"),
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: f.output + "/v6-revision-student.png", fullPage: true });
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    const b = await page.evaluate(() => ({
      width: innerWidth,
      scroll: document.documentElement.scrollWidth,
    }));
    check(width + "px修订页无外层横向溢出", b.scroll <= b.width + 1);
    await page.screenshot({ path: f.output + "/v6-revision-" + width + ".png", fullPage: true });
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("http://127.0.0.1:5173/student/review-book");
  await page.getByRole("combobox", { name: "课程", exact: true }).selectOption(f.courseId);
  await page
    .locator(".review-card")
    .first()
    .getByRole("button", { name: "展开题签", exact: true })
    .click();
  await page.getByRole("heading", { name: "再读这一题", exact: true }).waitFor();
  check(
    "温故册可按课程名称筛选",
    (await page.getByRole("combobox", { name: "课程", exact: true }).inputValue()) === f.courseId,
  );
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: f.output + "/v6-review-desktop.png", fullPage: true });
  check("本轮修订页面没有运行异常", errors.length === 0);
  return { checks };
};
