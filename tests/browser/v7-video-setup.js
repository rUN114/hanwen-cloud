async (page) => {
  const f = __FIXTURE__;
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("http://127.0.0.1:5173/login");
  await page.getByRole("textbox", { name: "账号", exact: true }).fill(f.owner.username);
  await page.getByRole("textbox", { name: "密码", exact: true }).fill(f.owner.password);
  await page.getByRole("button", { name: "登录书院", exact: true }).click();
  await page.waitForURL("**/home");
  return { ready: true };
};
