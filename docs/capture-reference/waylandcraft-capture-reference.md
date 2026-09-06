# RenderCraft 窗口捕捉参考设计（Minecraft Fabric 侧实锤）

来源：对 `EVV1E/waylandcraft`（即 `scapking/waylandcraft`）和周边 Minecraft Fabric 项目的实现阅读。
结论点：不只看了做法，还读了 Java 和 native 代码。可以直接用于 RenderCraft 的 `WaylandAdapter` / `X11Adapter` / `FrameSnapshot` / `CompositorConnection` 补全。

---

## 1. 捕捉链路总览（Waylandcraft 实测可用的那一套）

### 1.1 Wayland 端：XDG Desktop Portal ScreenCast + PipeWire
- Java 入口：`PipeWireCaptureManager`
- 原生入口：`WaylandCraftBridge.portalCaptureStart/Frame/Stop`（JNI）
- 原生实现：`native/src/portal_capture.rs`

流程
- `portalCaptureStart()` → D-Bus 调用 `org.freedesktop.portal.ScreenCast.CreateSession/SelectSources/Start`
- 弹出系统选择对话框，用户选窗口后，拿到 PipeWire 节点 ID
- 原生侧用 `libpipewire` 连该节点，开独立线程读帧
- `portalCaptureFrame()` → `[width(4B), height(4B), rgba...]`
- `portalCaptureStop()` → 停掉 PipeWire mainloop / stream

关键细节
- 帧格式由 PipeWire 决定，可能是 BGRx/BGRA/RGBA
- native 侧统一转成 RGBA（每像素 4 字节，R,G,B,A）
- Java 侧拿到的是 `byte[]`，前 8 字节是宽高，其余是像素
- PipeWire 读帧跑在独立线程里（`pw-stream`），Java 侧每 tick 取一次

这对 RenderCraft 的含义
- `CompositorConnection` 里面搞一个假的“发字符串读字符串”模型是不够的
- Wayland 捕捉应该直接走 PipeWire portal，不一定非得连远程 compositor 进程
- 若保留“原生合成器进程”模型，也应该改为真的 IPC（例如同步/异步消息 + 二进制帧传输），而不是 `readLine()` 那种文本协议

### 1.2 X11 端：JNA XGetImage
- Java 入口：`X11Capture`
- 辅助枚举：`X11WindowLister`

能力
- `X11Capture.captureRgba(displayName, xid)` → RGBA `ByteBuffer`（top-down）
- `X11Capture.getGeometry(...)` → 宽高 + 根窗口坐标
- `X11WindowLister.getDesktopWindows(...)` → 枚举顶层窗口（标题 / appId / pid / xid）

实现特点
- 方法内打开/关闭独立 X 连接，不保留跨调用状态
- 支持 32bpp 和 24bpp，解析 red/green/blue mask 和字节序
- 适合 xwayland 下的传统桌面窗口（尤其是不在 xdg toplevel 里的窗口）

这对 RenderCraft 的含义
- `X11Adapter` 现在是几乎空的，可以直接把 X11Capture 的思路 transplant 进来
- 枚举和抓帧最好分开：枚举靠 `X11WindowLister` 风格，抓帧靠 `X11Capture` 风格
- 坐标/几何信息最好在 `WindowMetadata` 或单独的几何模型里保留，方便后续交互和锚点计算

### 1.3 共享/转发侧：图像用最新帧覆盖、音频用有序队列
- 图像：`SharedWindowFrameRelay`
  - 按窗口 handle 缓存最新帧
  - 服务端每 2 tick 收集后再分片多线程发送
  - 超过 ~1.9MB 的帧直接跳过
- 音频：`AudioFrameRelay`
  - 每窗口一个队列，积压时丢最旧的帧
  - 服务端 tick 批量出队，再分片发送
- 载体：`SharedWindowImagePayload` 和 `SharedWindowAudioPayload`
  - 都带 `windowHandle`，用于路由和权限

这对 RenderCraft 的含义
- `BoundedAudioVideoBuffer` / `BufferStrategy` 的设计方向是对的
- 视频和音频最好采用不同的丢帧策略：视频保最新，音频保顺序
- 若 RenderCraft 要支持多人共享，帧/音频分开发、再合并路由是成熟做法

### 1.4 Minecraft 内部渲染侧：帧buffer 捕捉 + JPEG 压缩 + PBO
- Java 入口：`ImageCapture`
- 用途：共享窗口发送端把窗口 framebuffer 编码后发给其他客户端

优化点
- PBO 双缓冲异步回读，避免 GPU→CPU 同步阻塞
- GPU 侧缩放（`glBlitFramebuffer`），不走 CPU scale
- 直接 RGBA→JPEG 编码，跳过中间 `BufferedImage`
- 像素差异检测：无变化帧跳过发送
- 窗口句柄隔离的 PBO / 缩放 FBO 状态，避免多窗口相互串扰

关于 MC 兼容性的真实坑
- MC 26.x 渲染器在 pass 之间会把 `GL_READ_BUFFER` 设为 `GL_NONE`
- 读像素前必须显式 `glReadBuffer(GL_COLOR_ATTACHMENT0)`
- 读完后最好恢复为 `GL_NONE`，否则可能污染后续渲染
- PBO 在某些 Mesa/EGL 环境下可能 `glGenBuffers` 返回 0，需要永久降级为同步读取

这对 RenderCraft 的含义
- 如果 RenderCraft 是在 Minecraft 内渲染“外部窗口画面”，那么帧捕捉不一定来自外部协议，而可能来自 Minecraft 自身 framebuffer 或内部纹理
- 无论哪种来源，OpenGL 回读/编码/差异检测这套模式都值得复用
- `FrameSnapshot` 目前的字段太少，最好能表达：捕捉时间、尺寸、格式、是否来自 GPU 回读、是否已压缩、是否为差异帧

### 1.5 音频捕捉（进程粒度）
- Java 入口：`AudioCaptureManager`
- 原生入口：`WaylandCraftBridge.audioCaptureStart/Poll/Stop/Status`
- 原生实现：`native/src/audio_capture.rs`

思路
- 窗口 → 进程 PID → PipeWire 默认 sink 的 monitor 端口 → 捕获该源的 PCM
- 拿到 PCM 后，前 8 字节写入 `[sampleRate, channels]`，然后是 PCM 数据
- Java 侧按 `MAX_PACKET_BYTES` 分包发送

细节
- 目前做法偏向“捕获默认输出的 monitor”，而不是按应用节点精确匹配
- 窗口所属进程是通过 SO_PEERCRED（原生 Wayland）或 `_NET_WM_PID`（X11）拿的
- 如果 PID 解析不到，就不启动音频捕捉，但画面共享不受影响

这对 RenderCraft 的含义
- 音视频同步设计里，音频捕捉最好也按“窗口/进程”语义绑定，而不是全机捕捉
- `AudioCaptureService` / `AudioEncoder` 若要对接，建议沿用“先拿 PCM 流，再编码/分发”的顺序
- 音频状态查询（是否启动、是否有回调、已捕获字节数）对排障很有用，RenderCraft 也可以照这种方式暴露状态

---

## 2. 推荐的捕捉抽象模型

### 2.1 捕捉会话模型
```
CaptureSession
  start(...)
  pollFrame() -> FrameSnapshot | null
  stop()
```

两种实现
- WaylandCaptureSession：Portal + PipeWire（外部桌面窗口）
- X11CaptureSession：JNA XGetImage（X11 窗口）
- 若 RenderCraft 在游戏内渲染外部内容，也可以有一个 InternalRenderCaptureSession，专门从游戏帧buffer 或纹理抓帧

### 2.2 FrameSnapshot 建议扩展
现有字段：
- `captureTimeMs`
- `width`, `height`
- `imageData`
- `format`

建议至少补充：
- 捕捉来源类型（Wayland/X11/Internal）
- 是否为压缩数据（JPEG/PNG/raw RGBA 等）
- 若为网络共享准备：帧序号、是否差异帧、是否为最新帧覆盖策略产物
- 若涉及同步：音频时间基准或关联音频包序号

### 2.3 窗口元数据建议扩展
现有看起来已有 `WindowMetadata`，建议至少包含：
- 窗口标识（协议相关 ID：Wayland surface/serial，或 X11 xid）
- 标题、应用标识
- 尺寸和可见性
- 几何信息（位置或锚点所需信息）
- 是否可捕捉（有些窗口可能无法抓取）

---

## 3. RenderCraft 当前骨架的问题与修订优先级

### 3.1 `CompositorConnection`
问题
- 现在的模型是“启动一个原生合成器进程，然后用文本协议通信”
- 帧数据读取是假的（填零）
- 窗口列表解析也是空的（TODO JSON）

调整建议
- 如果目标是连接外部 Rust/Smithay 合成器，应该定义清楚二进制帧传输协议
- 如果目标是直接抓取桌面窗口，则不一定需要远程合成器进程模型
- 最好把“连接原生进程”和“调用本地捕捉 API”分开，避免混成一个类

### 3.2 `WaylandAdapter`
问题
- 现在是通过 `CompositorConnection` 去拿窗口和帧
- 如果不先把连接模型修掉，它就会一直停留在“看起来有结构、实际上依赖假实现”的状态

调整建议
- 可以保留适配器模式，但内部改为调用真的 Wayland capture 路径
- 若保留 Process/IPC 模型，至少让 `captureFrame` 返回真实数据结构，而不是零填充

### 3.3 `X11Adapter`
问题
- `listWindows` 返回空数组
- `captureFrame` 直接抛“未实现”

调整建议
- 按 X11Capture 模式实现窗口枚举和抓帧
- 最好支持显示器/显示名（`DISPLAY` 或指定显示）配置

### 3.4 `WindowListener`
问题
- 只有窗口生命周期和属性变更回调
- 没有“帧就绪”回调

建议
- 如果上层是轮询式捕捉，当前模式也可以接受
- 如果上层希望事件驱动，建议增加帧就绪或捕捉可用通知

---

## 4. 抓捕捉实现时最容易踩的坑（Waylandcraft 已踩过的）

- PipeWire 节点格式不固定，到手的帧可能是 BGRx/BGRA/RGBA，必须统一转换
- Portal D-Bus 交互别用固定 token 硬编码推导路径，要从 Response 里提取 session/node
- gdbus monitor 别用 `timeout` 包完整流程，否则会极慢；应该用 `grep -m1 Response` 类方式尽早退出
- PBO 在有些环境下失效，必须有同步回退路径
- MC 渲染器会改 `GL_READ_BUFFER`，抓帧时要显式设置和恢复
- 多窗口共享时，PBO/缩放 FBO 状态要按窗口隔离，否则画面会串
- 透明像素的窗口若强制 JPEG，会出现黑边；若走 PNG，质量参数失效；两种都可能触发帧大小保护失败
- 音频按窗口抓取的粒度极限是进程，不能细到每个标签/子窗口

---

## 5. 小结

当前最靠谱的窗口捕捉路线是：
1. Wayland → XDG Desktop Portal ScreenCast + PipeWire，帧统一成 RGBA 再给上层
2. X11 → JNA `XGetImage`，转 RGBA，按窗口枚举和几何信息补全元数据
3. 游戏内渲染/共享 → 帧buffer 回读 + GPU 缩放 + JPEG 压缩 + 差异检测
4. 音视频分发 → 视频最新帧覆盖、音频有序队列、按窗口路由

RenderCraft 现在缺的不是模型雏形，而是把这些真实实现接到骨架里。最先 deduplicate/补全的应该是：
- `X11Adapter` 的枚举与抓帧
- Wayland 侧的捕捉实现（直接 PipeWire portal 或真 IPC）
- `FrameSnapshot` 的表达能力
- `CompositorConnection` 的协议真实性或职责拆分
