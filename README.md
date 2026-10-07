<p align="center">
  <img src="assets/logo.svg" alt="MetroRail logo" width="132" height="132">
</p>

<h1 align="center">MetroRail</h1>

<p align="center">
  <a href="https://github.com/ALingqing/MetroRail/actions/workflows/build.yml"><img
    src="https://img.shields.io/github/actions/workflow/status/ALingqing/MetroRail/build.yml?label=build"
    alt="Build"></a>
  <a href="https://github.com/ALingqing/MetroRail/releases"><img
    src="https://img.shields.io/github/v/release/ALingqing/MetroRail?label=release" alt="Release"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.18%2B-3c8527" alt="Minecraft 1.18+">
  <img src="https://img.shields.io/badge/Java-17%2B-e76f00" alt="Java 17+">
  <a href="https://github.com/CubeX-MC/Metro"><img
    src="https://img.shields.io/badge/depend-Metro-blueviolet" alt="depend: Metro"></a>
</p>

<p align="center">
  <b>Metro 的铁路运行附属插件</b> —— 给 <a href="https://github.com/CubeX-MC/Metro">CubeX-MC/Metro</a>
  补上它没有的 <b>手动驾驶</b>、<b>列控信号</b>、<b>调度台</b> 三层能力。
</p>

<p align="center">
  
  <code>MetroRail</code> 管「驾驶 / 列控 / 调度」
</p>

---

MetroRail 的设计参照 **Skyworld-Railway（SkyRail Suite：STF / STCS / STA / SkyPCC）** 的全部功能域，
但把命令前缀从 `/st`、`/stcs`、`/skypcc` 收敛到统一的 **`/mr`**，并与 Metro 的线路数据并存运行。

---

## 目录

| 文档 | 内容 |
| :-- | :-- |
| [docs/安装与部署.md](docs/安装与部署.md) | 环境要求、安装、升级、卸载、排障 |
| [docs/快速上手.md](docs/快速上手.md) | 十分钟学会接管矿车并开动 |
| [docs/命令参考.md](docs/命令参考.md) | `/mr` 全部子命令与参数 |
| [docs/列控与信号.md](docs/列控与信号.md) | 轨道图、道岔、应答器、占用、行车许可、列控模式、完整性监测 |
| [docs/自动牌子.md](docs/自动牌子.md) | `[station] / [property] / [spawn] / [limit] / [stop]` 牌子 |
| [docs/调度网页.md](docs/调度网页.md) | SkyPCC 风格的浏览器调度台与 HTTP API |
| [docs/配置参考.md](docs/配置参考.md) | `config.yml`、`vehicles/*.yml` 与数据文件 |
| [docs/开发与构建.md](docs/开发与构建.md) | 编译、目录结构、架构与扩展点 |
| [docs/功能规划表.md](docs/功能规划表.md) | 开工前的功能对照表（Metro vs SkyRail vs MetroRail） |

---

## 功能一览

### A. 列车运动与手动驾驶
- **级位**：`/mr p1`~`p4`（牵引）、`/mr n`（惰行）、`/mr b1`~`b7`（常用制动）、`/mr eb`（紧急制动）
- **换向器**：`/mr forward / neutral / backward`（需停稳或低速才可换向）
- **驾驶权**：`/mr drive` 接管最近的矿车，`/mr release` 释放；下车 / 掉线自动撤权
- **车型物理**：`vehicles/*.yml` 定义最高速度、加速度、制动、紧急制动、阻力
- **编组**：多节矿车登记为「一列车」（`scan / create / append / unlink / remove`）
- **列车属性与模板**：`/mr property`、`/mr savedtrain`
- **完整性监测 TIMS**：编组固定、采样时效、相邻车厢间距校验
- **里程**：`/mr mileage`

### B. 驾驶 HMI
- `/mr cab` —— 图形驾驶室（级位 / 换向 / 鸣笛 / 列控模式按钮）
- `/mr hotbar` —— 快捷栏驾驶（第 N 格启用，1~9 格映射到 B7 → P4）
- 计分板 + BossBar —— 速度、级位、限速、MA、编组
- `/mr horn`、`/mr bell`、`/mr speedunit`、`/mr lang`

### C. 基础设施与定位
- **RailGraph** —— 有向轨道图（四向 + 坡道洪泛建图，`graph.yml` 持久化）
- **道岔** —— 登记与转换（拉杆 + 红石火把「道岔棒」交互，或 `/mr switch set`）
- **应答器 balise** —— 牌子第 1 行写 `[station]` / `[property]` / `[spawn]` / `[limit]` / `[stop]` 自动登记
- **列车定位与里程标定** —— 沿图的边 / 偏移
- **图导出 / 重建** —— `/mr graph status|inspect|rebuild|export`

### D. 占用 · 预约 · 行车许可
- **占用账本 OccupancyLedger** —— 证据保留，卸载 ≠ 出清，持久化到 `occupancy.yml`
- **行车许可 MA / EoA** —— 占用 / 断头 / 探测上限三段取最小
- **预约** —— `/mr ma demand [段数]` / `/mr ma release` / `/mr ma ack`
- **列控模式** —— `shadow`（只读影子） / `bypass` / `isolate` / `enforce`（强制保护制动，实验）
- **列车状态机** —— `SB / FS / SH / SR / TR / PT`

### E. 自动牌子
- `[station]` 距离制动站停 ｜ `[stop]` 强制停车 ｜ `[limit]` 限速 ｜ `[property V_target]` 目标速度 ｜ `[spawn]` 受控生成

### F. 调度网页
- `/mr web` —— 独立轻量 HTTP 服务（`com.sun.net.httpserver`）
- 实时轨道图（占用着色）、列车 / 道岔 / 应答器详情、事件日志、网页道岔控制
- 默认仅回环访问，可配 token；页面文件落盘到 `plugins/MetroRail/web/`，改完刷新即生效

---

## 环境要求

| 项目 | 要求 |
| :-- | :-- |
| 服务端 | Spigot / Paper **1.18+**（编译基线 1.18.2，Paper 现代版本直接可用） |
| Java | **17+** |
| 必需依赖 | **Metro** 插件（`depend: [Metro]`） |

---

## 快速安装

1. 把 `MetroRail-1.0.0.jar` 放进服务器的 `plugins/`。
2. 确认 `plugins/Metro/` 已就位（Metro 为强制依赖）。
3. 启动服务器，看到日志：`MetroRail 已启用（…）`。
4. 进游戏执行 `/mr help` 查看指令。

详细步骤（含首次配置、升级、卸载）见 [docs/安装与部署.md](docs/安装与部署.md)。

---

## 命令速查

完整参数说明见 [docs/命令参考.md](docs/命令参考.md)。

```
/mr help                                    帮助
/mr drive [车型]                             接管最近的矿车并获得驾驶权
/mr release                                 释放驾驶权
/mr <p1..p4|n|b1..b7|eb>                    设置级位（简写）
/mr notch <p1..p4|n|b1..b7|eb>              设置级位
/mr reverser <forward|neutral|backward>     换向
/mr vehicle <list|info|select>              车型
/mr train <list|scan|create|append|unlink|remove|info>   编组
/mr status                                  运行状态
/mr mileage [玩家] / /mr clearkm            里程
/mr lang <语言>                             切换语言
/mr reload                                  重载配置

/mr cab                                     打开驾驶室 GUI
/mr hotbar [格位]                           快捷栏驾驶
/mr horn | /mr bell                         鸣笛 / 铃声
/mr speedunit <kph|mph|block>               速度单位

/mr switch <list|scan|info|set|remove>      道岔
/mr balise|origin|end [list]                应答器
/mr graph <status|inspect|rebuild|export>   轨道图

/mr ma <status|demand|release|ack>          行车许可
/mr mode <shadow|bypass|isolate|enforce>    列控模式
/mr property <列车> <键> [值]                列车属性
/mr savedtrain <list|save|spawn|remove>     列车模板

/mr web                                     调度网页信息
```

---

## 权限

| 权限 | 说明 | 默认 |
| :-- | :-- | :-: |
| `metrorail.use` | 基础功能（`status` / `vehicle` / `mileage` / `graph` / `balise`…） | 所有人 |
| `metrorail.drive` | 取得驾驶权、操作级位 / 换向 / MA / 模式 | 所有人 |
| `metrorail.train` | 编组、列车属性、模板 | OP |
| `metrorail.switch` | 登记与转换道岔 | OP |
| `metrorail.admin` | `reload`、`graph rebuild/export`、`web`、`enforce` 模式 | OP |

---

## 数据文件

运行时生成于 `plugins/MetroRail/`：

| 文件 | 内容 |
| :-- | :-- |
| `config.yml` | 主配置 |
| `lang/zh_CN.yml`、`lang/en_US.yml` | 语言文件 |
| `vehicles/*.yml` | 车型物理参数 |
| `graph.yml` | 轨道图 |
| `switches.yml` | 道岔登记 |
| `balises.yml` | 应答器登记 |
| `occupancy.yml` | 占用账本 |
| `rail-trains.yml` | 列车属性 / 模板 |
| `trains.yml` | 编组 |
| `mileage.yml` | 里程记录 |
| `web/` | 调度网页静态资源 |

---

## 从源码构建

```bash
mvn -o clean package
# 产物：target/MetroRail-1.0.0.jar
```

详见 [docs/开发与构建.md](docs/开发与构建.md)。

---

## 许可

未指定许可证。使用前请与作者确认。
