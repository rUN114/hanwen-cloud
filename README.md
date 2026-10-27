# 锦卷书院 · 汉文学在线教学管理系统

[![Build verification](https://github.com/rUN114/hanwen-cloud/actions/workflows/verify.yml/badge.svg)](https://github.com/rUN114/hanwen-cloud/actions/workflows/verify.yml)

个人《微服务项目实验》课程项目。Vue 与 Spring Cloud 前后端分离，以汉文学学习为主题，提供课程、阅读、练习、习作批阅和学情管理。最近公开发布标签为 **v7.2.2**；main 分支已包含后续依赖维护、附件与资源回收、事务边界和批量查询等修订，交付时以注明的 Git 提交号为准。首次公开 Git 快照为 v7.1.0。

![书院画卷：米友仁《云山图》及馆藏来源](docs/images/museum-cloud-mountains.jpg)

截图内作品为克利夫兰艺术博物馆开放馆藏，详见 [素材与依赖说明](THIRD_PARTY_NOTICES.md)。鉴赏区使用真实作品，AI 插画只用于明确标注的界面装饰。

## 功能

- 管理员：账号、批量导入、启停与审计。
- 教师：课程章节、资料、班级、公告答疑、题库组卷、共教与交接、人工批阅、分项评分、课程总评和 CSV 导出。
- 学生：选课、阅读进度、札记批注、收藏、练习、习作草稿与提交、温故册、修订回笺与历史对照。
- 学情：知识点统计、班级对照、八周交卷记录、关注名册、截止提醒与定向催交。
- 书院画卷：六件真实馆藏的出处、作者与年代，原比例长卷、馆藏局部和滚动放大。
- 本机开发：学生与教师快捷登录，仅限回环地址和开发服务器，正常经过后端鉴权；不提供固定公开密码。

## 阅读内容

课程包括唐诗、宋词、古典散文、经典阅读、文学写作与汉文化专题，共24章、24篇诗文、24份章节练习（48题）、12项习作和6份可下载学习导引。每篇原文注明来源与版本；译意、词义提示、导读和练习由本项目编写。

内容目录不包含虚构选课、成绩、评语或学习进度。学生从课程广场选择加入，学习记录在实际使用后形成。导入方式与内容来源见[课程内容与数据初始化](docs/课程内容与数据初始化.md)。

## 结构

| 目录或服务 | 用途 | 本机端口 |
| --- | --- | --- |
| frontend | Vue 页面、交互与本地静态素材 | 5173 |
| backend/eureka | 服务注册与发现 | 8761 |
| backend/gateway | 统一 API 入口 | 默认8080；本项目本机使用8088 |
| backend/user-service | 用户、登录、会话与审计 | 8101 |
| backend/course-service | 课程、共教、诗文与题库 | 8102；第二实例8105 |
| backend/learning-service | 班级、学习记录、札记与练习 | 8103 |
| backend/assignment-service | 习作、评分、修订与总评 | 8104 |
| database | 39表空库结构及增量迁移 | MySQL本机3307 |
| scripts / tools / tests | 初始化、备份、构建和验证 | — |

后端通过 Eureka、Gateway 和 OpenFeign 协作；各业务库使用各自数据库账号。后端用 IDEA 打开 `backend/pom.xml`，前端用 VS Code 打开 `frontend`。

业务服务内部已分为 controller、service、client、config、mapper、model；前端按业务整理页面和组件，统一归档 HTTP、类型、会话和样式。具体目录及格式命令见[代码结构与格式](docs/代码结构与格式.md)。

依赖已在 POM 和 package-lock 中固定：JDK25、Spring Boot4.1.1、Spring Cloud2025.1.3、MyBatis-Plus3.5.17、MySQL9.7；Vue3.5.43、Vue Router5.4.0、Element Plus2.14.7、Vite8.3.4、TypeScript6.0.3。构建使用 Maven3.9 和 Node24。

## 本机准备与运行

1. 安装 JDK25、Maven3.9、Node24 和 MySQL9.7，确认工具在终端可用。
2. **新空环境**用 `database/hanwen.sql`。Windows 可运行 `scripts/Initialize-Local.ps1`，交互输入数据库管理凭据，创建四库、受限应用账号、RSA密钥和私有 `.local/runtime.json`。该脚本拒绝覆盖已有表或轮换现有环境凭据。先审阅脚本，再在个人开发环境运行。
3. 在 IDEA 各服务运行配置中，导入 `.local/runtime.json` 的环境变量。先启动 Eureka，再启动四个业务服务，最后 Gateway。需要演示负载均衡时，以 `--server.port=8105` 再运行一个课程实例。Gateway 本机可设置 `--server.port=8088`。
4. 启动前端，本机开发默认代理到8088，可用 `HW_GATEWAY_URL` 显式调整。代理地址必须与 Gateway 一致。例如 PowerShell：

```powershell
$env:HW_GATEWAY_URL = 'http://127.0.0.1:8088'
cd frontend
npm ci
npm run dev
```

5. 浏览器访问 `http://127.0.0.1:5173`。管理员初始密码由初始化随机生成，保存在私有运行配置中；使用管理员创建教学账号。仓库不包含本机已生成的密码、教学上传文件或数据快照。快捷登录按钮仅在本机存在对应的私有演示账号配置时提供。

已有教学库不要重新初始化。升级前完整备份数据库、上传文件和密钥；按版本增量执行 `database/migrations` 对应脚本。部署环境变量示例与 systemd/Nginx 候选配置在 `deploy/linux`，尚未在公网目标机验收。

## 构建与检查

```text
mvn -B -ntp -f backend/pom.xml package
npm ci --prefix frontend
npm run build --prefix frontend
npm run format:check --prefix frontend
node tests/export.test.mjs
node tests/roster.test.mjs
node tests/workspace.test.mjs
node tests/revision-diff.test.mjs
node --test tests/local-demo.test.mjs
python tests/monitor_test.py
python tools/install_academy_content.py --validate
python tests/content_catalog_test.py
python tools/check_publication.py
```

GitHub Actions 自动执行构建、JUnit、前端逻辑、课程内容及发布内容检查。`tests` 下的真实 API、数据库恢复及浏览器流程检查需要独立本机环境，部分脚本会创建明确命名的测试数据。安装阅读课程后，这些写入脚本会由本机内容标记阻止运行；测试应使用独立检出、独立私有配置和隔离数据库，不能通过删除标记绕过保护。构建或模拟检查成功不代表公网生产验收通过。

## Git、版本与公开范围

[版本记录](CHANGELOG.md) · [提交与发布约定](docs/版本管理.md) · [运行接口说明](docs/V7接口与运行.md)

每次大更新保留真实提交、更新日志和相关验证，再同步 GitHub；里程碑创建版本标签。首次提交包含此前开发的完整源码，不补造过往 Git 历史。

`.local`、数据库数据快照、教学上传文件、验收原始记录、录像和个人课程材料留在本机。公开仓库只包含可复用源码、空库结构、迁移脚本及经过整理的文档。GitHub 源码备份不能替代数据库和附件的私有备份。

## 当前边界

本项目以课程实验和本机教学流程为范围，未完成公网生产部署、跨主机存储和长时间运行验收；不提供邮件短信、直播、支付或 AI 自动批改。Linux 部署配置需要在目标服务器继续验证。

原创源码采用 [MIT](LICENSE)；馆藏照片、古籍及第三方依赖见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
