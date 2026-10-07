<p align="center">
  <img src="../assets/logo.svg" alt="MetroRail logo" width="96" height="96">
</p>

<h1 align="center">MetroRail 文档</h1>

<p align="center">
  MetroRail 是 <a href="https://github.com/CubeX-MC/Metro">Metro</a> 的铁路运行附属插件，命令前缀 <code>/mr</code>。
</p>

## 从这里开始

| 文档 | 适合谁 | 内容 |
| :-- | :-- | :-- |
| [安装与部署](安装与部署.md) | 服主 | 环境要求、安装、升级、卸载、排障 |
| [快速上手](快速上手.md) | 玩家 | 十分钟学会接管矿车并开动 |
| [命令参考](命令参考.md) | 玩家 / OP | `/mr` 全部子命令与权限 |
| [配置参考](配置参考.md) | 服主 / OP | `config.yml`、`vehicles/*.yml`、语言与数据文件 |

## 进阶

| 文档 | 内容 |
| :-- | :-- |
| [列控与信号](列控与信号.md) | 轨道图、道岔、应答器、占用、行车许可、列控模式、完整性监测 |
| [自动牌子](自动牌子.md) | `[station]` `[property]` `[spawn]` `[limit]` `[stop]` 牌子 |
| [调度网页](调度网页.md) | SkyPCC 风格调度台与 HTTP API |

## 开发者

| 文档 | 内容 |
| :-- | :-- |
| [开发与构建](开发与构建.md) | 编译、目录结构、架构、扩展点 |
| [功能规划表](功能规划表.md) | Metro vs SkyRail vs MetroRail 功能对照（开工前评审） |

---

## 一条最短路径

```
安装插件 → /mr help
站到矿车旁 → /mr drive
/mr reverser forward → /mr p4 → 开动
/mr b4 → /mr eb → 停车
/mr release → 结束
```

之后按需翻阅：[列控与信号](列控与信号.md)（把线路变成「有信号」的）、
[自动牌子](自动牌子.md)（做车站自动停）、[调度网页](调度网页.md)（浏览器看运行图）。
