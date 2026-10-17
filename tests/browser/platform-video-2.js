async (page) => {
  const f = __FIXTURE__,
    started = Date.now();
  await page.goto("http://127.0.0.1:5173/login");
  await page.getByRole("textbox", { name: "账号", exact: true }).fill(f.teacher.username);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(f.teacher.password);
  await page.getByRole("button", { name: "登录书院", exact: true }).click();
  await page.waitForURL("**/home");
  await page.goto("http://127.0.0.1:5173/teacher/submissions/" + f.uiSubmissionIds[0] + "/grade");
  await page
    .getByLabel("评语", { exact: true })
    .fill("原文证据充分，分析紧扣语境。能结合“举头”与“低头”说明情感转移。");
  await page.waitForTimeout(6000);
  await page.getByRole("button", { name: "保存更正", exact: true }).click();
  await page
    .getByRole("dialog", { name: "确认评分" })
    .getByRole("button", { name: "保存评分", exact: true })
    .click();
  await page.getByText("评分已保存", { exact: true }).waitFor();
  await page.mouse.wheel(0, 650);
  await page.waitForTimeout(6000);
  await page.goto("http://127.0.0.1:5173/courses/" + f.courseId + "/gradebook");
  await page.getByText("86.70", { exact: true }).waitFor();
  await page.waitForTimeout(7000);
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "导出成绩册", exact: true }).click();
  await download;
  await page.waitForTimeout(Math.max(0, 40000 - (Date.now() - started)));
  return {
    part: "Teacher rubric feedback, history and actual gradebook export",
    seconds: (Date.now() - started) / 1000,
  };
};
