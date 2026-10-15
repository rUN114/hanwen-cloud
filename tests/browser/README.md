# P2浏览器流程参考

脚本使用Playwright CLI的run-code --filename执行，不是Playwright Test框架。仅在专用验证环境操作。

__FIXTURE__为本机私有输入占位，不能将替换后的文件提交源码。输入来自tests/p2_practice.py生成的.local/p2-fixtures.json，加上teacher（本机演示教师账号）和uiQuizId（界面组卷返回编号）。所有注入后的文件只能放.local。

教师脚本从已登录题库且打开“新建题笺”弹窗开始，组卷脚本使用本次验证课程与题库。学生脚本通过实际登录完成两次作答，final脚本在同一验证学生登录后补查状态及草稿版本冲突。测试会消耗测验次数，重复验收应使用新验证课程与账号；历史验证库已有相同题干时，先按本次topic限定题库，或在专用空演示库运行。

这些是本次验收参考流程，图片和结构化结果在output/playwright与evidence。密码、JWT和替换后的脚本排除于源码包。
