async (page) => {
  const f = __FIXTURE__;
  await page.goto("http://127.0.0.1:5173/login");
  await page.getByRole("textbox", { name: "账号", exact: true }).fill(f.students[2].username);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(f.students[2].password);
  await page.getByRole("button", { name: "登录书院", exact: true }).click();
  await page.waitForURL("**/home");
  await page.goto("http://127.0.0.1:5173/practice/" + f.quizId);
  await page.locator(".result-circle").waitFor();
  await page.waitForTimeout(8000);
  await page.locator(".written-feedback").scrollIntoViewIfNeeded();
  await page.waitForTimeout(7000);
  return { shown: "student real graded score and teacher comment" };
};
