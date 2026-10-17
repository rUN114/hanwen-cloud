async (page) => {
  const f = __FIXTURE__,
    started = Date.now();
  await page.goto("http://127.0.0.1:5173/library/" + f.workId);
  await page.getByRole("button", { name: "只读原文", exact: true }).waitFor();
  await page.waitForTimeout(5000);
  await page.getByRole("button", { name: "只读原文", exact: true }).click();
  await page.waitForTimeout(3000);
  await page.getByRole("button", { name: "原文译文对照", exact: true }).click();
  await page.waitForTimeout(4000);
  await page.setViewportSize({ width: 390, height: 900 });
  await page.mouse.wheel(0, 800);
  await page.waitForTimeout(5000);
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("http://127.0.0.1:8761");
  await page.getByText("Instances currently registered with Eureka", { exact: true }).waitFor();
  await page.waitForTimeout(6000);
  await page.goto("http://127.0.0.1:5173/teacher/question-bank");
  await page.locator("main h1").waitFor();
  await page.waitForTimeout(Math.max(0, 40000 - (Date.now() - started)));
  return {
    part: "Literary comparison, mobile layout, Eureka registration and question bank",
    seconds: (Date.now() - started) / 1000,
  };
};
