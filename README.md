# TrialSpawnerFinder 统一双版本发行版

一个发行包同时支持两代试炼密室布局。程序不会把两个 Minecraft 版本装进同一个服务端，而是根据配置选择独立引擎和独立运行环境。

## 首次使用

1. 编辑 `finder.properties`。
2. 必须填写 `generation-version` 和 `seed`。
3. 双击 `setup.bat`，安装当前选择版本的运行环境。
4. 双击 `run.bat` 开始搜索。

发行包已经包含 ASM、Mixin、Fabric Loader、Intermediary 和 Fabric API，不再联网获取 Fabric 依赖。`setup.bat` 会先寻找 GraalVM 25；找不到时直接下载 GraalVM 25，只有下载或解压失败才回退到普通兼容 Java。随后下载约 50-60 MB 的 Mojang 原版服务端并按官方 SHA-1 校验。原版服务端优先使用 BMCLAPI 国内镜像；镜像连续 15 秒低于 128 KB/s 或等待超过 90 秒时，会保留已下载部分并切换 Mojang 官方源断点续传。下载完成后会在本地完成一次服务端解包，`run.bat` 首次启动时不再执行任何安装下载。

安装时控制台会显示完整的环境准备和服务端解包输出，下载过程使用包含百分比、已下载大小和速度的单行进度。运行搜索时，控制台只保留任务流程、即时错误和原地刷新的单行搜索进度。Minecraft 和 Fabric 的普通日志写入 `.runtime\minecraft-版本\logs\latest.log`，调试日志写入同目录的 `debug.log`；每次启动都会将上一份日志按完整时间归档到 `logs\archive`。发行版根目录的 `logs\launcher-版本-时间.log` 另外保存每次 Java 启动的完整输出，便于定位 Fabric 初始化前的失败。

如果使用过早期统一版，重新运行新版 `setup.bat` 即可。脚本会把旧目录中已经下载并校验通过的服务端迁移到 Fabric Loader 使用的正确位置，不会重复下载。

配置示例：

```properties
generation-version=2
seed=8050192242802360875
```

版本含义：

| 配置 | 对应布局 | 实际引擎 |
|:---:|:---|:---|
| `1` | Minecraft 1.21-1.21.1 初代布局 | Minecraft 1.21.1 |
| `2` | Minecraft 1.21.2-26.2 现代布局 | Minecraft 26.2 |

“现代布局”当前只验证至26.2，不代表未来版本不会再次修改试炼密室。

`generation-version` 只能填写 `1` 或 `2`。`seed` 必须是 `-9223372036854775808` 至 `9223372036854775807` 范围内的十进制整数。配置缺失或错误时，安装与运行脚本都会显示中文错误并保留窗口。

## 切换版本

修改 `generation-version` 后，先运行一次 `setup.bat`，再运行 `run.bat`。两个服务端分别缓存在：

```text
.runtime\minecraft-1.21.1
.runtime\minecraft-26.2
```

已经安装过的版本可以离线复用，切换版本不会覆盖另一套运行环境。安装器优先复用现有 GraalVM 25，找不到便下载 GraalVM 25。只有下载或解压失败时才启用兜底：Minecraft 1.21.1 可回退到普通 Java 21或25，Minecraft 26.2 只能回退到普通 Java 25。

## 断点恢复与停止

每完成一个完整分片，程序都会原子保存当前排名和已完成分片。断点分别位于：

```text
.runtime\minecraft-1.21.1\checkpoints\trial-spawner
.runtime\minecraft-26.2\checkpoints\trial-spawner
```

下次使用相同 Minecraft 版本和相同配置运行时会自动继续，并在控制台显示已恢复的分片数量。修改种子、范围、统计形状、最低密室数、线程数或搜索模式后会开始一项独立任务，不会误用旧断点。搜索正常完成后，对应断点会自动删除。

运行中第一次按 `Ctrl+C` 会请求 Java 正常关闭并保存当前结果；关闭可能需要数秒。再次按 `Ctrl+C` 会立即终止。直接关闭窗口、任务管理器结束或断电时，CSV不一定来得及更新，但已经完整提交的分片仍可从二进制断点继续，最多重跑当时正在处理的一片。

升级发行版时应直接覆盖到原目录，并保留 `.runtime`。将新版解压到另一个目录不会自动迁移正在运行的任务。`trial-finder-runtime-种子` 小世界只是服务端注册表和种子上下文缓存，不保存搜索进度，也不会包含搜索范围内的区块。

## 结果

结果文件名由查找器类型和启动时间组成，例如：

```text
results-trial-spawner-20260719-003912.csv
results-trial-spawner-20260719-003912-2.csv
```

CSV使用中文表头，可直接用Excel打开；同名TXT按列对齐，适合记事本查看。结果文件名和防重名后缀由查找引擎统一生成，每次搜索都会新建结果，不覆盖旧文件。查找过程只使用 Minecraft 服务端提供的注册表、生成器和种子上下文，不生成搜索范围内的区块；对应种子的轻量运行上下文缓存在 `.runtime` 中，不会修改正常游戏存档。
