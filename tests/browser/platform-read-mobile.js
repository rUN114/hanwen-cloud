async (page) => {
  const f = __FIXTURE__,
    checks = [];
  function check(name, ok) {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  }
  await page.goto("http://127.0.0.1:5173/login");
  await page.getByRole("textbox", { name: "账号", exact: true }).fill(f.students[0].username);
  await page
    .getByRole("textbox", { name: "密码", exact: true })
    .fill(f.students[0].initialPassword);
  await page.getByRole("button", { name: "登录书院", exact: true }).click();
  await page.waitForURL("**/home");
  await page.goto("http://127.0.0.1:5173/student/submissions/" + f.uiSubmissionIds[0]);
  await page.getByRole("heading", { name: "先生批语", exact: true }).waitFor();
  check(
    "学生实际读到分项反馈",
    (await page.locator(".score-large").innerText()).includes("80.00") &&
      (await page.locator(".rubric-feedback li").count()) === 2,
  );
  check("评分后附件停止上传", (await page.getByLabel("选择习作附件").count()) === 0);
  const dl = page.waitForEvent("download");
  await page
    .locator(".attachment-panel")
    .getByRole("button", { name: "下载", exact: true })
    .first()
    .click();
  await dl;
  check("学生实际下载本人随卷附件", true);
  await page.screenshot({ path: f.output + "/platform-feedback.png", fullPage: true });
  await page.goto("http://127.0.0.1:5173/courses/" + f.courseId + "/gradebook");
  await page.getByText("86.70", { exact: true }).waitFor();
  check("学生成绩册只显示本人一行", (await page.locator(".el-table__body tbody tr").count()) === 1);
  await page.goto(
    "http://127.0.0.1:5173/student/enrollments/" + f.enrollmentId + "/chapters/" + f.chapterId,
  );
  await page.getByRole("heading", { name: "课后思考", exact: true }).waitFor();
  await page.locator(".lesson-figure img").waitFor();
  check("学生阅读讲义显示排版及鉴权图片", true);
  check(
    "原文阅读锚点保持独立",
    (await page.locator(".reading-body.annotation-text").count()) === 1,
  );
  await page.screenshot({ path: f.output + "/platform-reading.png", fullPage: true });
  await page.goto("http://127.0.0.1:5173/library/" + f.workId);
  await page.getByRole("button", { name: "只读原文", exact: true }).click();
  check(
    "原译对照可切换为只读原文",
    (await page.getByRole("heading", { name: "译文", exact: true }).count()) === 0,
  );
  await page.getByRole("button", { name: "原文译文对照", exact: true }).click();
  await page.getByRole("heading", { name: "译文", exact: true }).waitFor();
  check(
    "原文译注与典故关联实际显示",
    (await page.getByRole("heading", { name: "字词笺注", exact: true }).isVisible()) &&
      (await page.locator(".allusion-shelf").count()) === 1,
  );
  await page.screenshot({ path: f.output + "/platform-literature-compare.png", fullPage: true });
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    for (const [name, path, ready] of [
      ["literature", "/library/" + f.workId, "字词笺注"],
      ["gradebook", "/courses/" + f.courseId + "/gradebook", "总评口径"],
      ["feedback", "/student/submissions/" + f.uiSubmissionIds[0], "先生批语"],
      ["inbox", "/inbox", "书院来信"],
    ]) {
      await page.goto("http://127.0.0.1:5173" + path);
      await page.getByRole("heading", { name: ready, exact: true }).waitFor();
      const bounds = await page.evaluate(() => ({
        scroll: document.documentElement.scrollWidth,
        width: innerWidth,
      }));
      check(width + "px " + name + "无页面横向溢出", bounds.scroll <= bounds.width + 1);
      await page.screenshot({
        path: f.output + "/platform-" + name + "-" + width + ".png",
        fullPage: true,
      });
    }
    await page.keyboard.press("Tab");
    check(
      width + "px键盘可以进入可操作元素",
      await page.evaluate(() => document.activeElement !== document.body),
    );
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  return { checks };
};
