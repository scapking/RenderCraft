# 外部调研总结

> 创建 RenderCraft 过程中参考的第三方项目、输入法现状、音视频延迟方案、CLI 设计范式、以及高可用/高性能架构模式的调研结果。

## 1. 项目目标回顾

1. 创建 `scapking/RenderCraft` 项目，使用 MIT 协议。
2. 支持 Wayland、X11，以及将来扩展其他协议。
3. 音视频兼容问题：目前只有视频相对可用，音频尚未真正实现。主流思路——客户端与服务端各缓存 4~8 秒音视频，以解决卡顿带来的延迟问题。
4. 精简混乱的 CLI。
5. 改进模板窗口移动体验。
6. 统一现有杂乱的快捷键设计。
7. 把代码改造成高可用、高扩展、高并发、高性能。
8. 输入法目前不可用——希望不依赖任何模拟层，原生兼容 Minecraft，让窗口本身就是 Minecraft 的一部分。支持所有输入法与所有协议。
9. 联网查阅类似 GitHub 开源项目，看看别人如何解决这些问题。

---

## 2. 竞品与衍生项目 landscape

在 WaylandCraft 生态中，存在多个 fork 和衍生项目，说明社区对这类功能有持续需求。值得注意的是：**多个 fork 重复做了同一类事情（Xwayland 整合、输入法、鼠标捕获、窗口管理），后来有的被上游合并，有的弃用。**这也是 RenderCraft 要做协议抽象层的原因——防止重复劳动和短命 patch branch。

- **YourSandwich/waylandcraft**  
  - Xwayland 支持的独立 fork。X11 应用在 Minecraft 中展示为窗口，输入/焦点/剪贴板/拖放双向桥接。  
  - 默认按键：V = app launcher、G = 键盘捕获、B = 窗口管理器。

- **Skycrafter-dev / PedroLuisBrilhadori / PainDeMie64 的 waylandcraft-extended**  
  - 都已弃置。曾加了 Xwayland、Steam/Proton 工作流、可配置普通+硬键盘捕获、真实 Wayland 客户光标 surface、Escape 键释放、X11 focus/stacking/全屏缩放修复、dmabuf 纹理生命周期修复。  
  - 教训：这些功能最终上游合并或替代了它们——说明要有稳定的扩展点，而非散落在各 fork 的 patch 里。

- **jdkeke142/glfw-wayland-minecraft**  
  - LWJGL 的 GLFW 的 Wayland 问题修补集（分数缩放、光标、输入），配套 mod **WayFix** 修复多屏全屏定位、分数缩放下的点击偏移等。  
  - 测试于 MC 1.21.11 + Fabric + KDE Plasma 6.3.6 + NVIDIA。

- **bczhc/glfw**（已标记过时）  
  - GLFW 的一堆 patch，包括 IME 支持（glfwSetPreeditCallback / glfwSetIMEStatusCallback / GLFW_IME 模式 / glfwSetPreeditCandidateCallback 等），还有 X11 on-the-spot 输入法风格支持。  
  - 注明 Minecraft ≥26.1 不需要某些补丁了——**Minecraft 在改善 IME，原来的 workaround 正在失效**。

- **0x484558/glfw-mc**  
  - 小幅 Wayland 修复和改进，重基了 IME 支持 PR，改进输入修饰符剥离、滚动事件去重、窗口管理容错等。

- **moehreag/wayland-fixes / Wayland Fix (Modrinth)**  
  - 宣称“no-compromises Wayland 兼容”，处理图标、输入法候选栏等，让游戏在 Wayland 下更原生。注意沙箱（flatpak）路径授权。

---

## 3. 输入法现状——原生兼容 Minecraft 的可行性

### 3.1 Minecraft 自身的问题与趋势

- **MC-306616**：IM E 无法在 Linux 上切换回英文。Minecraft 26.1 snapshot 中陆续有 IME 候选栏在游戏内显示的尝试（目前 Windows/macOS），社区在讨论 Linux 是否也跟进。
- **Minecraft 26.3 Snapshot 4**开始使用 SDL3 替代 GLFW，Linux 下原生偏向 Wayland。这意味着游戏底层输入/文本输入 API 在变化——这是个契机，也是个要 watch 的点。

### 3.2 第三方模组的解决思路

- **NLR-DevTeam/Fcitx5-Enhancer（核心参考）**  
  - 针对 Minecraft 26.1+，提供 Fcitx5 兼容。  
  - 核心问题：按键既是游戏快捷键又是输入法按键（如 Tab、Enter）时，事件被输入法和游戏同时处理，导致中断。  
  - 解法：提供可高度配置的 **IMBlocker（可视化元素选择器）**，把冲突键从游戏侧拦截/协调，同时增加原生 Wayland 环境的 IME 支持（可选 `libwayland_support.so`）。  
  - 使用本地库实现功能，给了 x86_64 glibc 2.31 的内置库，其他架构需自编译。  
  - **模式：不是再建一个模拟输入法层，而是在游戏和输入法之间加一道协调和过滤层，外加原生 Wayland IME 支持。**

- **bczhc/glfw、0x484558/glfw-mc、jdkeke142/glfw-wayland-minecraft** 都在修 GLFW 侧的 IME 支持和 Wayland 输入问题，其中 bczhc/glfw 已经标记“过时，因为 MC 26.3 在 IME 上支持更好了”。

- **wayland-fixes / Wayland Fix** 这类模组则在做“让游戏在 Wayland 下更原生”，包括图标、输入法候选栏等，不是单纯输入法问题。

### 3.3 结论

- 输入法“一直修不好”的一个大背景是：**Minecraft 本身的文本输入模型在 Linux/Wayland 下是薄弱的，社区正在靠 GLFW/SDL3 补丁 + 第三方模组（像 Fcitx5-Enhancer）来填补**。
- 你的方向“**不依赖模拟层、原生兼容 Minecraft、窗口属于 Minecraft 的一部分**”**是对的**，并且在技术发展趋势上是向前走的——尤其是在 MC 26.3+ SDL3、原生 Wayland 倾向的背景下。
- 借鉴 Fcitx5-Enhancer：**“协调/过滤层 + 原生 Wayland IME 支持”** 是一个可取的中间态，但 RenderCraft 的目标可以更进一步——把窗口对应的文本输入完全接入 Minecraft 的文本输入通道，让窗口本身就是 Minecraft 的可聚焦文本输入目标。

---

## 4. 音视频延迟与缓冲设计

### 4.1 Discord Go Live 架构

- 多进程管道，streamer/viewer/backend 三端协调。
- 捕获有**稳固的 fallback 系统**，一个方法挂了快切另一个。
- 管道里的每部分都可能成为瓶颈，所以每个组件必须高效。

### 4.2 编码器与帧类型

- 使用自制捕获/编码代码，集成 OS 和显卡驱动。
- 用 WebRTC 传输视频，低延迟目标下实时调节目标比特率和帧率。
- 关键帧（key frame）自包含、不依赖之前帧；新观众加入或丢失画面时发 key frame；之后 delta frame 只编码变化。
- 良好流下，key frame 数据量是 delta frame 的 **6~10 倍**。

### 4.3 缓存上限——来自 discord-plays-mario-kart 的教训

- 问题：Node 事件循环里，同步跑主模拟循环 + ffmpeg stdin 喂/stdout 读/RTP 发送，ffmpeg 被饿到低于实时，导致 pushFrame 队列无界增长、输入延迟飙到 ~20s、OOM 风险。
- **解法：给框架的 PassThrough 队列加上大小上限 MAX_SINK_BUFFER_BYTES（~3 帧），一旦超标丢最新的帧。**  bound 住端到端输入延迟，去掉 OOM 风险。上线 shouldDropFrame() 逻辑，还加了 dropped 计数器和每段会话摘要里的“last speed ratio”。
- 这个思路非常贴合你说的“服务端缓存 + 客户端缓存，各自 4~8 秒”：缓存是为了吸收抖动/短时卡顿，但缓存不可无限增长，否则延迟飞升。

### 4.4 音画同步

- WiseChecker 分析：音频和视频分两个线程处理，CPU/GPU 负载重时视频可能丢帧而音频线程继续全速播，产生 gap。硬件编码器（NVENC/AMD VCE）也可能导致 drift。
- 提示：音画要同步，播放端要有统一的时钟/缓冲管理。

### 4.5 低延迟编码器的典型最佳实践（来自 Discord-video-stream 的 ffmpeg 参数示例）

- `-tune zerolatency`
- `-preset ultrafast`
- keyint 等于帧率、插入 AUD。

### 4.6 结论

- 4~8 秒缓存的方向没错——它吸收的是网络/编码抖动，让短时卡顿不表现为播放卡顿。
- 但必须有一个**缓存上限 + 丢帧/丢节策略**，否则一旦编码/网络慢，缓存无限长，延迟就真的飙上去。
- 音画同步要单独对待：它们最好共享一个“呈现时钟”或至少彼此知晓对方的播放位置。
- 协议层（WebRTC 或你自己的）要支持自适应比特率/帧率，QoS 反馈环要存在。
- 对于 RenderCraft：在你自己的帧/音频 relay 上，把 **客户端播放时钟 + 服务端发射缓冲 + 丢帧阈值** 三件事明确建模，比起盲目堆缓存大小，实际上更稳。

---

## 5. CLI 重构——Fabric 社区里的命令设计范式

### 5.1 Brigadier（Mojang 的树形命令库）

- `/rc window ...` 这种层级命名天然适合 Brigadier 的树。
- 命令通过 `CommandRegistrationCallback` 注册，`CommandDispatcher` 负责解析/执行。
- 有 `redirect` 机制，可用来做别名或子命令重定向（如 `/wl` 到 `/rc` 的兼容层）。

### 5.2 司令官类库（减少样板）

- **celestialfault/commander**：Kotlin 库，用 `@Group/@RootCommand/@Command` 注解把函数定义转成 Brigadier 命令，减少手写树的乏味工作。
- **itzmetanjim/commander**：号称“1240% 更好的 brigadier 替代”，Kotlin DSL，可从命令文件生成，支持客户端/服务端区分。

### 5.3 客户端命令支持

- Fabric API 有 `fabric-client-command-api-v2` / `ClientCommands`，管理客户端命令。
- RenderCraft 的 `/rc` 大部分应该是客户端可见的（窗口管理、模板、分享控制、输入模式切换），但 give/permission 等需要服务端参与——这点要在 CLI 设计中明确分离**客户端命令**和**需要服务端上下文的命令**。

### 5.4 设计教训（来自现有 waylandcraft 的问题）

- 扁平地堆很多命令字符串容易失控；树形结构 (`rc window place`, `rc share start`, …) 更易维护、帮助文本可自动生成。
- 命令与按键绑定要分清：CLI 是文本操作接口，快捷键是即时交互接口，两者的动作尽量复用同一底层实现，减少“命令能做但按键做不到”或反之的不一致。

### 5.5 结论

RenderCraft 的 CLI 用 Brigadier 树，/`rc` 下分 namespace（window/layout/share/input/protocol/setting），每命令一个明确用途，帮助文本从元数据生成，客户端/服务端命令分开注册。

---

## 6. 高可用 / 高扩展 / 高并发 / 高性能——类似项目里的架构模式

### 6.1 Pumpkin MC（Rust）

- 模块化 Cargo workspace，异步区块加载，内存 DashMap 缓存，插件系统用 libloading 动态加载。  
- 启示：**把功能切成有清晰生命周期的模块，热点数据放内存缓存，加载/卸载可独立**。

### 6.2 Pillar / Fulcrum（Minecraft 网络控制平面）

- 基于 Redis Streams 的容错消息运输（at-least-once），节点靠心跳续约，死节点自动消失，无单中心。  
- 启示：**RenderCraft 的多人共享/权限/窗口状态如果将来要分布式扩展，可以从这类“心跳 + 队列 + 无单点故障”模式借鉴**。

### 6.3 Astatine（Purpur 分支，Java）

- 区域化执行—按地域 cell 独立 tick、降级 worker 队列防止一个超载区拖垮其他区域，动态区域所有权，精确 cell 锁，跨所有者手递队列。  
- 启示：**将来 Minecraft 本身有类似“分区独立执行”的路子，RenderCraft 的窗口/输入/渲染层可以考虑地域或按窗口 shard，避免一个慢窗口拖累所有窗口**。

### 6.4 IWM（Rust/Tokio）

- 网关/工作者/存储三服务，gRPC + Protobuf 通信，面向高并发设计。  
- 启示：**Rust 生态里的多服务 async 的模式，如果 RenderCraft 的 native 侧要拆任务/线程模型，能参考**。

### 6.5 GlobalProfileSync / Nexus Core

- SOA + Redis Pub/Sub 实时一致性，多层弹性。  
- 启示：**共享/权限等状态的多实例同步，redis 队列或类似的“事件流”模型可选**。

### 6.6 Quantified-API (QAPI)

- 提醒“别乱加 async 就祈祷不炸”，提供统一 API 把工作路由到 CPU/缓存/GPU 后端，避免死锁。  
- 启示：**高性能不等于一味并行，要有统一任务分派和后端抽象**。

### 6.7 通用教训

- “hot path 上别隐藏同步”（Astatine 的 async ownership guards）。
- “一个慢消费者不要阻塞所有人”（discord-plays 的 bounded frame queue）。
- “失败只影响局部，不要让整个 mod 崩”（Protocol Abstraction 层 + fallback 捕获，像 Discord 那种）。
- “可观测性是高可用的前提”——trace/logging/metrics 要在关键路径上。

---

## 7. 对应到 RenderCraft 的设计要点

- **协议抽象层** → 按 Wayland 适配器、X11 适配器的思路，后续加新协议只加适配器，不改上层。
- **窗口底座** → 统一窗口状态机、约束求解式布局、线程安全、渲染线程只拿快照。
- **输入与 IME** → 借鉴 Fcitx5-Enhancer 的“协调/过滤 + 原生 Wayland IME 支持”，进一步做到窗口文本输入接入 Minecraft 文本输入通道。
- **CLI** → Brigadier 树、/`rc` namespace、元数据驱动 help、客户端/服务端命令分开注册。
- **音视频** → 建模客户端缓冲 + 服务端发射缓冲 + 丢帧上限 + 呈现时钟 + 音画同步对齐；参考 Discord/discord-plays 里的 bounded queue 和自适应编码。
- **高可用/高扩展/高并发/高性能** → 模块化、线程安全的窗口模型、失败局部化、可观测性，参考 Pumpkin/Astatine/QAPI 的思路。

---

*调研完成于 2026-09-03。EXA API  키로 직접 검색하고 정리한 결과입니다.*
