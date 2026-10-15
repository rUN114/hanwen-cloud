async page => {
 const f=__FIXTURE__,checks=[],errors=[];function check(name,ok){if(!ok)throw Error(name);checks.push({name,result:'PASS'});}page.on('pageerror',e=>errors.push(e.message));
 await page.setViewportSize({width:1440,height:1000});
 await page.goto('http://127.0.0.1:5173/login');await page.getByRole('textbox',{name:'账号',exact:true}).fill(f.students[0].username);await page.getByRole('textbox',{name:'密码',exact:true}).fill(f.students[0].password);await page.getByRole('button',{name:'登录书院',exact:true}).click();await page.waitForURL('**/home');
 await page.getByRole('link',{name:'温故册',exact:true}).click();await page.getByRole('heading',{name:'温故而知新',exact:true}).waitFor();await page.getByRole('combobox',{name:'课程',exact:true}).selectOption(f.courseId);await page.waitForFunction(()=>document.querySelectorAll('.review-card').length===3);check('真实页面收录三枚错题题签',true);
 const card=page.locator('.review-card').filter({hasText:'诗中月光与乡思有关。'});await card.getByRole('button',{name:'展开题签',exact:true}).click();await page.locator('.review-options button').first().waitFor();check('作答前不显示正确答案或解析',await page.locator('.review-result').count()===0);
 await page.locator('.review-options button').first().focus();await page.keyboard.press('Space');check('键盘可选择复习答案',await page.locator('.review-options button').first().getAttribute('aria-pressed')==='true');await page.getByRole('button',{name:'核对本次温习',exact:true}).click();await page.getByRole('heading',{name:'本次答对，继续按计划温习',exact:true}).waitFor();check('真实作答返回解析与下一计划',(await page.locator('.review-result').innerText()).includes('计划连续答对 1 / 3'));
 await page.screenshot({path:f.output+'/v6-review-desktop.png',fullPage:true});
 for(const width of [390,320]){await page.setViewportSize({width,height:900});const b=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth}));check(width+'px温故册没有外层横向溢出',b.scroll<=b.width+1);await page.screenshot({path:f.output+'/v6-review-'+width+'.png',fullPage:true});}
 await page.setViewportSize({width:1440,height:1000});await card.getByRole('button',{name:'展开题签',exact:true}).click();await page.getByText('尚未到计划复习时间，可回看题面；',{exact:false}).waitFor();check('复习未到期按钮禁用并提示日期',await page.getByRole('button',{name:'核对本次温习',exact:true}).isDisabled());
 check('本轮页面没有运行异常',errors.length===0);return {checks};
}
