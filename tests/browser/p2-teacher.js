async page => {
 const checks=[];const check=(name,ok)=>{if(!ok)throw Error(name);checks.push({name,result:'PASS'})};
 await page.setViewportSize({width:1440,height:1000});
 let d=page.getByRole('dialog',{name:'新建题笺',exact:true});
 await d.getByLabel('题型',{exact:true}).selectOption('MULTIPLE');
 await d.getByRole('spinbutton',{name:'分值',exact:true}).fill('4');
 const prompt='P2浏览器验证：理解诗歌中的意象，应结合哪些依据？';
 await d.getByRole('textbox',{name:'题目',exact:true}).fill(prompt);
 for(const [key,text] of Object.entries({A:'原文字句',B:'按钮数量',C:'作品语境',D:'账号编号'}))
  await d.getByRole('textbox',{name:'选项 '+key,exact:true}).fill(text);
 await d.getByRole('checkbox',{name:'B',exact:true}).uncheck();
 await d.getByRole('checkbox',{name:'C',exact:true}).check();
 await d.getByRole('textbox',{name:'解析',exact:true}).fill('这是自编浏览器测试题。文学理解应回到字句与语境。');
 await d.getByRole('textbox',{name:'主题标签',exact:true}).fill('P2浏览器组卷');
 await d.getByRole('button',{name:'保存题笺',exact:true}).click();
 await d.waitFor({state:'hidden'});
 let card=page.locator('.bank-slip').filter({has:page.getByRole('heading',{name:prompt,exact:true})});
 await card.waitFor();
 check('教师界面创建多选4分题笺且正确项为AC',(await card.locator('.correct-option').allTextContents()).map(s=>s.trim()[0]).join('')==='AC'&&(await card.innerText()).includes('4 分'));
 await card.getByRole('button',{name:'修订题笺',exact:true}).click();
 d=page.getByRole('dialog',{name:'修订题笺',exact:true});
 await d.getByRole('textbox',{name:'题目',exact:true}).fill(prompt+'（修订）');
 await d.getByRole('button',{name:'保存题笺',exact:true}).click();await d.waitFor({state:'hidden'});
 card=page.locator('.bank-slip').filter({has:page.getByRole('heading',{name:prompt+'（修订）',exact:true})});await card.waitFor();
 check('教师修订表单能正常保存并更新版本',(await card.innerText()).includes('修订 1'));
 await page.locator('.bank-filter').getByLabel('题型',{exact:true}).selectOption('MULTIPLE');
 await page.getByRole('button',{name:'筛选题笺',exact:true}).click();
 await page.waitForFunction(()=>document.querySelectorAll('.bank-slip').length===2);
 check('题库界面筛选多选且不显示其他题型',(await page.locator('.bank-slip-meta').allTextContents()).every(s=>s.includes('多选')));
 await page.screenshot({path:'output/playwright/p2-bank-desktop.png',fullPage:true,animations:'disabled'});
 for(const width of [390,320]){
  await page.setViewportSize({width,height:844});
  check(width+'px题笺库没有横向溢出',await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  await page.screenshot({path:'output/playwright/p2-bank-'+width+'.png',fullPage:true,animations:'disabled'});
 }
 await page.setViewportSize({width:1440,height:1000});
 return {at:new Date().toISOString(),checks,scope:'Actual teacher UI: create, revise, filter and responsive screenshots'};
}
