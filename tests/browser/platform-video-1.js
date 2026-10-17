async (page) => {
  const f = __FIXTURE__,
    started = Date.now();
  await page.waitForTimeout(6000);
  await page.getByRole("link", { name: "我的课程", exact: true }).click();
  await page.locator("main h1").waitFor();
  await page.waitForTimeout(4000);
  await page.goto("http://127.0.0.1:5173/student/courses/" + f.demoCourse);
  await page.locator("main h1").waitFor();
  await page.waitForTimeout(7000);
  await page.mouse.wheel(0, 650);
  await page.waitForTimeout(4000);
  await page.goto(
    "http://127.0.0.1:5173/student/enrollments/" + f.demoEnrollment + "/chapters/" + f.demoChapter,
  );
  await page.locator(".reading-body.annotation-text").waitFor();
  await page.waitForTimeout(6000);
  await page.mouse.wheel(0, 550);
  await page.waitForTimeout(Math.max(0, 40000 - (Date.now() - started)));
  return {
    part: "Student workbench and actual literary reading",
    seconds: (Date.now() - started) / 1000,
  };
};
