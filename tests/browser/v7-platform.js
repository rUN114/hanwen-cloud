async (page) => {
  const f = __FIXTURE__,
    checks = [],
    errors = [];
  function check(name, ok = true) {
    if (!ok) throw Error(name);
    checks.push({ name, result: "PASS" });
  }
  page.on("pageerror", (e) => errors.push(e.message));
  const base = "http://127.0.0.1:5173";
  async function login(account) {
    await page.goto(base + "/login");
    await page.getByRole("textbox", { name: "账号", exact: true }).fill(account.username);
    await page.getByRole("textbox", { name: "密码", exact: true }).fill(account.password);
    await page.getByRole("button", { name: "登录书院", exact: true }).click();
    await page.waitForURL("**/home");
  }
  async function shot(name) {
    await page.screenshot({ path: f.output + "/" + name + ".png", fullPage: true });
  }
  async function widths(name) {
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 920 });
      await page.waitForTimeout(100);
      check(
        name + " " + width + "px无页面横向溢出",
        await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),
      );
      await shot(name + "-" + width);
    }
    await page.setViewportSize({ width: 1440, height: 1000 });
  }
  try {
    await page.setViewportSize({ width: 1440, height: 1000 });
    await login(f.owner);
    await page.goto(base + "/teacher/courses/" + f.courseId + "/team");
    await page.locator(".team-roster").waitFor();
    check(
      "主讲共教名录有主讲、协同与助教",
      (await page.locator(".team-roster").innerText()).includes("助教") &&
        (await page.locator(".team-member").count()) === 3,
    );
    check(
      "主讲有委任表单",
      (await page.getByRole("textbox", { name: "协作教师账号" }).count()) === 1,
    );
    await shot("v7-team-desktop");
    await widths("v7-team");
    await page.getByRole("button", { name: "课程交接 · 留痕" }).click();
    check("交接记录保留委任与交接", (await page.locator(".team-ledger li").count()) >= 5);
    await shot("v7-handover-desktop");
    await login(f.assistant);
    await page.goto(base + "/teacher/courses/" + f.courseId + "/team");
    await page.locator(".team-roster").waitFor();
    check(
      "助教共教名录没有委任和移除",
      (await page.getByRole("textbox", { name: "协作教师账号" }).count()) === 0 &&
        (await page.getByRole("button", { name: "移除", exact: true }).count()) === 0,
    );
    await page.goto(base + "/teacher/courses/" + f.courseId + "/chapters");
    await page.getByRole("heading", { name: /章节/ }).first().waitFor();
    check(
      "助教章节列表不显示新增",
      (await page.getByRole("button", { name: "新增章节", exact: true }).count()) === 0,
    );
    await page.goto(base + "/courses/" + f.courseId + "/quizzes");
    await page.locator(".quiz-cover").first().waitFor();
    check(
      "助教练习列表不显示新增",
      (await page.getByRole("button", { name: "新建练习", exact: true }).count()) === 0,
    );
    await login(f.students[2]);
    await page.goto(base + "/practice/" + f.quizId);
    await page.getByRole("button", { name: "开始测验", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "确定", exact: true }).click();
    await page.locator(".question-manuscript").first().waitFor();
    await page.locator("input[type=radio][value=A]").check();
    await page.getByRole("textbox", { name: "第2题文字作答", exact: true }).fill("思故乡");
    await page.locator(".draft-status").filter({ hasText: "答案已保存" }).waitFor();
    await page.route("**/api/practices/*/runs/*/draft", (r) => r.abort());
    const answer = "举头与低头的动作变化，表现诗人由看月转向思乡的内心变化。";
    await page.getByRole("textbox", { name: "第3题文字作答", exact: true }).fill(answer);
    await page.waitForFunction(() =>
      document.querySelector(".draft-status")?.textContent.includes("网络"),
    );
    check(
      "断网保存失败仍保留文字答案",
      (await page.getByRole("textbox", { name: "第3题文字作答", exact: true }).inputValue()) ===
        answer,
    );
    await page.unroute("**/api/practices/*/runs/*/draft");
    await page.getByRole("button", { name: "保存答案", exact: true }).click();
    await page.locator(".draft-status").filter({ hasText: "答案已保存" }).waitFor();
    await page.reload();
    await page.getByRole("textbox", { name: "第3题文字作答", exact: true }).waitFor();
    check(
      "刷新可恢复服务器保存的文字草稿",
      (await page.getByRole("textbox", { name: "第3题文字作答", exact: true }).inputValue()) ===
        answer,
    );
    await shot("v7-written-desktop");
    await widths("v7-written");
    const submitted = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/practices/" + f.quizId + "/attempts") &&
        r.request().method() === "POST",
    );
    await page.getByRole("button", { name: "确认交卷", exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "确定", exact: true }).click();
    const attempt = (await (await submitted).json()).data;
    await page.getByText("文字题待人工批阅，整卷成绩尚未确定。", { exact: true }).waitFor();
    check("页面待批不显示零分圆环", (await page.locator(".result-circle").count()) === 0);
    await shot("v7-pending-desktop");
    await login(f.assistant);
    await page.getByRole("link", { name: "文字题待阅", exact: true }).click();
    await page.waitForURL("**/teacher/practice-queue");
    const line = page.locator(".list-line").filter({ hasText: f.browserStudentName });
    await line.getByRole("link", { name: "展卷批阅", exact: true }).click();
    await page.waitForURL("**/teacher/practice-attempts/" + attempt.id);
    await page.getByRole("spinbutton", { name: "第2题得分", exact: true }).fill("3");
    await page.getByRole("spinbutton", { name: "第3题得分", exact: true }).fill("4");
    await page
      .getByRole("textbox", { name: "第3题批语", exact: true })
      .fill("动词分析准确，可补充月色意象。");
    await page.locator(".written-reference").last().locator("summary").click();
    check(
      "教师显示冻结的参考答案",
      (await page.locator(".written-reference").last().innerText()).includes(
        "从外界月色转入内心乡思",
      ),
    );
    await shot("v7-mark-desktop");
    await widths("v7-mark");
    await page.getByRole("button", { name: "核对并保存批阅", exact: true }).click();
    check("批阅确认显示整卷9分", (await page.getByRole("dialog").innerText()).includes("9 / 10"));
    await page.getByRole("dialog").getByRole("button", { name: "确定", exact: true }).click();
    await page.getByRole("heading", { name: "已完成批阅", exact: true }).waitFor();
    check(
      "保存正式成绩及评分历史",
      (await page.locator(".mark-frontispiece").innerText()).includes("9 分") &&
        (await page.locator(".mark-history details").count()) === 1,
    );
    await shot("v7-marked-desktop");
    await page.goto(base + "/teacher/courses/" + f.courseId + "/analytics");
    await page.locator(".insights-points").waitFor();
    check(
      "学情页实际挂载知识点回览",
      (await page.locator(".insights-points").innerText()).includes("意象与情感"),
    );
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: "导出知识点 CSV", exact: true }).click();
    const downloaded = await download;
    check("知识点CSV可下载", downloaded.suggestedFilename().endsWith(".csv"));
    await downloaded.saveAs(f.output + "/v7-knowledge.csv");
    await shot("v7-insights-desktop");
    await widths("v7-insights");
    await page.getByRole("button", { name: "学舍对照", exact: true }).click();
    check(
      "学舍统计区分零分与未知",
      (await page.locator(".insights-classes").innerText()).includes("45%"),
    );
    await shot("v7-classes-desktop");
    await page.getByRole("button", { name: "八周练习笺", exact: true }).click();
    check(
      "八周练习笺显示真实交卷记录",
      (await page.locator(".insights-weeks").innerText()).includes("份交卷"),
    );
    await page.getByRole("combobox", { name: "学情学舍", exact: true }).selectOption(f.classId);
    await page.waitForFunction(() =>
      document.querySelector(".insights-attention h3")?.textContent.includes("/ 2"),
    );
    check(
      "班级筛选明确2人在册",
      (await page.locator(".insights-attention h3").innerText()).includes("/ 2"),
    );
    await page.route("**/api/teacher/courses/*/learning-insights*", (r) => r.abort());
    await page.getByRole("button", { name: "重新统计", exact: true }).click();
    await page.getByText(/当前展示上次成功统计/).waitFor();
    check("学情网络失败保留并标识上次统计", (await page.locator(".insights-weeks").count()) === 1);
    await page.unroute("**/api/teacher/courses/*/learning-insights*");
    await page.getByRole("button", { name: "重新统计", exact: true }).click();
    await page.waitForFunction(() => !document.body.textContent.includes("当前展示上次成功统计"));
    await shot("v7-weeks-desktop");
    await login(f.students[2]);
    await page.goto(base + "/practice/" + f.quizId);
    await page.locator(".result-circle").waitFor();
    check(
      "学生页面回看9分与教师批语",
      (await page.locator(".result-scroll").innerText()).includes("90") &&
        (await page.locator(".written-feedback").innerText()).includes("动词分析准确"),
    );
    await shot("v7-feedback-desktop");
    check("本轮页面没有运行异常", errors.length === 0);
    return {
      checks,
      attemptId: attempt.id,
      scope:
        "Real browser login, draft, submission, assistant marking, analytics and CSV. Network abort is simulated; server operations are real.",
    };
  } catch (e) {
    return { checks, error: e.message, result: "FAIL" };
  }
};
