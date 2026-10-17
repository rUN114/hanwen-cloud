async (page) => {
  const f = __FIXTURE__;
  await page.goto("http://127.0.0.1:5173/teacher/practice-attempts/" + f.uiAttemptId);
  await page.locator(".mark-question").first().waitFor();
  await page.waitForTimeout(10000);
  await page.screenshot({ path: f.output + "/v7-marked-desktop.png", fullPage: true });
  await page.locator(".mark-question").last().scrollIntoViewIfNeeded();
  await page.locator(".written-reference").last().locator("summary").click();
  await page.screenshot({ path: f.output + "/v7-mark-viewport.png" });
  await page.waitForTimeout(15000);
  await page.locator(".mark-history").scrollIntoViewIfNeeded();
  await page.locator(".mark-history summary").first().click();
  await page.waitForTimeout(13000);
  return { shown: "actual written answer, reference, awarded score and grading history" };
};
