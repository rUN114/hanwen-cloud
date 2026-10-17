# 项目协作约定

本项目为汉文学微服务课程项目，保持 Vue 前端与 Spring Cloud 后端分离，保留 Eureka、Gateway 和课程双实例的演示能力。

## Git 与 GitHub

- 仓库为 https://github.com/rUN114/hanwen-cloud ，默认分支 main。
- 用户已要求大更新保留 Git 记录并上传 GitHub。每次大更新完成后，更新 CHANGELOG.md，执行本次改动相关验证，审查将提交的差异，提交并推送当前工作分支。记录实际结果，不将本地成功写成远端成功。
- 版本发布使用有说明的版本标签。首次 Git 快照为 v7.1.0，不补造过去的提交。
- 推送前运行 `python tools/check_publication.py`。凭据、本机配置、密钥、教学数据快照、原始验收材料与课程个人信息不得进入公开仓库。
- 不覆盖用户现有改动，不强推，不修改安全软件配置，不恢复或变相重建被隔离的启动脚本。

## 验证与素材

- Java 业务代码按 controller、service、client、config、mapper、model 分包；入口 Application 留在服务根包。前端页面和组件按业务分组，HTTP、会话、类型、组合函数、工具、数据与全局样式使用各自目录。
- 编码和重构遵循 karpathy-guidelines；非简单改动结束后使用 code-simplifier 检查维护性。正确性、可读性和合理分层优先，不默认启用 ponytail，不为了短代码压缩多条语句。
- Java 使用 google-java-format 1.37.0 的 AOSP 风格（4空格）；Vue/TypeScript/CSS 与前端配置使用锁定的 Prettier（2空格）。前端运行 `npm run format` / `npm run format:check`，Java 使用 CI 中固定版本和 SHA256 的格式工具。细节见 docs/代码结构与格式.md。
- 移动文件时同步导入、测试与文档引用；保持接口路径、页面路由和样式加载顺序。不能通过删测试或放宽规则让重构通过。

- 后端验证：`mvn -B -ntp -f backend/pom.xml package`。
- 前端验证：在 frontend 执行 `npm ci` 和 `npm run build`；按改动运行 tests 中相应检查。
- 数据库与浏览器集成检查需要独立本机环境，不能把编译或 mock 通过描述为完整业务验收。
- “书院画卷”只展示可核对来源的真实作品；AI 插画只用于界面装饰并明确标注。保留馆方“传”“约”等归属限定与长卷原比例。
