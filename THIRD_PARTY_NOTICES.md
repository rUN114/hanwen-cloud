# 素材与第三方依赖

本仓库原创应用源码采用 MIT 许可。第三方依赖、古籍和馆藏图像保留各自的权利及许可，不因本仓库的 MIT 文件改变归属。

## 馆藏数字图像

`frontend/public/assets/collection/` 为克利夫兰艺术博物馆开放的 CC0 藏品数字图像，仅转换格式和制作等比例缩略图，未使用 AI 重绘或修复。

| 藏品编号 | 作品与作者 | 官方来源 |
| --- | --- | --- |
| 1933.220 | 米友仁《云山图》 | https://www.clevelandart.org/art/1933.220 |
| 1954.582 | 杜堇《陪月闲行图》 | https://www.clevelandart.org/art/1954.582 |
| 1981.34 | 传牧谿（法常）《荷燕图》（译名） | https://www.clevelandart.org/art/1981.34 |
| 1979.27.1.6 | 陈洪绶《仿古画册：荷花与山石》（译名） | https://www.clevelandart.org/art/1979.27.1.6 |
| 1959.46 | 董其昌《江山秋霁图》 | https://www.clevelandart.org/art/1959.46 |
| 1959.138 | 张渥绘、褚奂书《九歌图》及三张馆藏局部 | https://www.clevelandart.org/art/1959.138 |

馆方政策：https://www.clevelandart.org/open-access 。图像源链接和原图 SHA256 见 `frontend/src/data/museum-paintings.json`；题名、年代和归属说明见 `docs/书院画卷来源与使用说明.md`。示例界面截图内的馆藏照片同样适用以上说明。

## 项目插画、纹样和字体

`frontend/public/assets/paintings/` 和 `courtyard.webp` 为本项目用 AI 辅助生成的界面背景，不作为历史作品鉴赏。几何纹样、文房 SVG 为项目制作。弃用的竹溪插画未纳入仓库。系统使用设备提供的字体回退，没有分发字体文件。

## 古籍文本

数据库最初的四篇诗文及阅读内容目录中的24篇诗文来自古代作品，原文、版本及链接见 `database/content/works.json` 与 `docs/诗文来源.json`，页面和下载导引保留来源字段。仅整理所列古代正文，不收入来源页面的现代译文、注释、赏析或编辑叙述。本项目另行编写参考译意、词义提示、导读、练习与评分参考，并与古文原文分开标明。维基文库页面上的其他内容不纳入本项目源码许可。

## 依赖

开发格式工具包括 Prettier 3.9.10（MIT）和 google-java-format 1.37.0（Apache-2.0）。Java 格式工具从 Google 官方 Release 下载并核对 SHA256，不随源码分发二进制；Prettier 仅作为开发依赖。

Java 与前端依赖的版本分别由 Maven POM 和 `frontend/package-lock.json` 锁定。Spring、MyBatis-Plus、Vue、Vue Router、Element Plus、Vite 等依赖保留其上游许可；依赖二进制及 node_modules 不作为原创源码提交。部署时应保留依赖自带的许可及通知。
