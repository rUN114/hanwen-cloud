async (page) => {
  const f = __FIXTURE__,
    checks = [];
  const check = (name, ok) => {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  };
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("http://127.0.0.1:5173/login");
  await page.getByRole("textbox", { name: "账号", exact: true }).fill(f.teacher.username);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(f.teacher.password);
  await page.getByRole("button", { name: "登录书院", exact: true }).click();
  await page.waitForURL("**/home");
  await page.goto("http://127.0.0.1:5173/teacher/courses/" + f.courseId + "/assignments/new");
  await page
    .getByLabel("作业标题", { exact: true })
    .fill("浏览器习作 · 月光与乡思 " + Date.now().toString().slice(-6));
  await page
    .getByLabel("题目与要求", { exact: true })
    .fill("以“举头”与“低头”为线索，说明诗歌情感的转移，至少引用一句原文。");
  await page.getByLabel("截止时间（北京时间）", { exact: true }).fill(f.deadlineLocal);
  await page.getByRole("button", { name: "添加评分项", exact: true }).click();
  await page.getByLabel("评分项1", { exact: true }).fill("原文理解");
  await page.getByLabel("分项满分1", { exact: true }).fill("60");
  await page.getByRole("button", { name: "添加评分项", exact: true }).click();
  await page.getByLabel("评分项2", { exact: true }).fill("论证表达");
  await page.getByLabel("分项满分2", { exact: true }).fill("40");
  check(
    "教师页面可填写两项评分细则",
    (await page.getByLabel("评分项2", { exact: true }).inputValue()) === "论证表达",
  );
  await page.screenshot({ path: f.output + "/platform-task-editor.png", fullPage: true });
  const created = page.waitForResponse(
    (r) =>
      r.url().endsWith("/api/teacher/assignments") &&
      r.request().method() === "POST" &&
      r.status() === 200,
  );
  await page.getByRole("button", { name: "保存草稿", exact: true }).click();
  const t = (await (await created).json()).data;
  await page.waitForURL("**/assignments");
  const article = page
    .locator("article")
    .filter({ has: page.getByRole("heading", { name: t.title, exact: true }) });
  await article.getByRole("button", { name: "发布作业", exact: true }).click();
  await page.getByRole("button", { name: "确认发布", exact: true }).click();
  await article.getByText("已发布", { exact: true }).waitFor();
  check("界面新建作业并独立发布成功", true);
  await article.getByRole("button", { name: "定向催交", exact: true }).click();
  await page.getByRole("dialog", { name: "先生叮嘱 · 定向催交" }).waitFor();
  await page.locator("label.el-checkbox").filter({ hasText: "验收同窗甲" }).click();
  await page.getByRole("button", { name: "发送站内提醒", exact: true }).click();
  await page.getByRole("button", { name: "确定", exact: true }).click();
  await page.getByText("已发送 1 条，今日已发送 0 条", { exact: true }).waitFor();
  check("界面定向催交真实发出一条站内信", true);
  await page.goto(
    "http://127.0.0.1:5173/teacher/courses/" + f.courseId + "/chapters/" + f.chapterId + "/edit",
  );
  await page.getByRole("textbox", { name: "排版讲义", exact: true }).waitFor();
  const old = await page.getByRole("textbox", { name: "排版讲义", exact: true }).inputValue();
  await page
    .getByRole("textbox", { name: "排版讲义", exact: true })
    .fill(old + "\n## 课后思考\n**原文证据**应如何支持观点？");
  await page.getByRole("button", { name: "阅读预览", exact: true }).click();
  await page.getByRole("heading", { name: "课后思考", exact: true }).waitFor();
  check(
    "讲义标题与强调按排版预览呈现",
    (await page.locator(".lesson-document strong").filter({ hasText: "原文证据" }).count()) === 1,
  );
  await page.locator(".lesson-figure img").waitFor();
  check("教师预览通过鉴权读取图片", true);
  await page.screenshot({ path: f.output + "/platform-lesson-editor.png", fullPage: true });
  await page.getByRole("button", { name: "保存章节", exact: true }).click();
  await page.waitForURL("**/chapters");
  check("排版讲义通过页面保存", true);
  return { checks, taskId: t.id, taskTitle: t.title };
};
