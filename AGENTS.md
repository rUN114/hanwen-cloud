# 项目协作约定

本项目为汉文学微服务课程项目，保持 Vue 前端与 Spring Cloud 后端分离，保留 Eureka、Gateway 和课程双实例的演示能力。

## Git 与 GitHub

- 仓库为 https://github.com/rUN114/hanwen-cloud ，默认分支 main。
- 用户已要求大更新保留 Git 记录并上传 GitHub。每次大更新完成后，更新 CHANGELOG.md，执行本次改动相关验证，审查将提交的差异，提交并推送当前工作分支。记录实际结果，不将本地成功写成远端成功。
- 版本发布使用有说明的版本标签。首次 Git 快照为 v7.1.0，不补造过去的提交。
- 推送前运行 `python tools/check_publication.py`。凭据、本机配置、密钥、教学数据快照、原始验收材料与课程个人信息不得进入公开仓库。
- 不覆盖用户现有改动，不强推，不修改安全软件配置，不恢复或变相重建被隔离的启动脚本。

## 验证与素材

- 后端验证：`mvn -B -ntp -f backend/pom.xml package`。
- 前端验证：在 frontend 执行 `npm ci` 和 `npm run build`；按改动运行 tests 中相应检查。
- 数据库与浏览器集成检查需要独立本机环境，不能把编译或 mock 通过描述为完整业务验收。
- “书院画卷”只展示可核对来源的真实作品；AI 插画只用于界面装饰并明确标注。保留馆方“传”“约”等归属限定与长卷原比例。
