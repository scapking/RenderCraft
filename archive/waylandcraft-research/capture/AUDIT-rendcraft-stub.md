# RenderCraft 窗口捕捉对接笔记（基于 Waylandcraft 参考）

目标：把外部参考成果对准当前骨架，列出可直接接的实现点和先修顺序。
范围：`WaylandAdapter`、`X11Adapter`、`FrameSnapshot`、`CompositorConnection`、`WindowListener`。

---

## 1. 当前骨架的现实情况

- `ProtocolBackend` 接口已经有了，包括 `listWindows / getMetadata / captureFrame / addWindowListener` 这一套。
- `FrameSnapshot` 也有了，但表达能力偏少：只有时间、尺寸、数据、格式。
- `WaylandAdapter` 看起来成形了，实际上窗口和帧都是经由 `CompositorConnection` 拿的，而那个连接目前更像占位。
- `X11Adapter` 几乎是空实现。
- `CompositorConnection` 的通信模型是文本命令/文本响应，帧数据还是填零，窗口列表解析也是空的。

结论
- 模型形状没错
- 缺的不是接口原型，而是把真实捕捉路径接进来

---

## 2. Wayland 侧应该怎么接

### 2.1 优先考虑的捕捉来源
参考 Waylandcraft 的做法，Wayland 捕捉不一定非得通过远程合成器进程。更直接的是：
- XDG Desktop Portal ScreenCast
- PipeWire 节点读帧
- 原生 bridge 暴露 start/frame/stop 三个操作

如果 RenderCraft 最终还是想保留一个本地 native 进程/库，那也应该让这个native侧负责：
- 启动和维护 PipeWire 流
- 把帧摘出来，按统一格式返回
- 提供停止/状态查询

### 2.2 `CompositorConnection` 的两种改法
#### 方式 A：保留远程进程模型，但把协议做实
- 命令/响应从文本改为清晰的二进制或至少带长度前缀的帧传输
- `captureWindow(handle)` 返回真正的宽、高、格式、数据
- 窗口列表也要能解析出有效句柄和元数据

#### 方式 B：把“连接原生后端”和“窗口捕捉”拆开
- 一个连接/启动原生组件的类
- 一个捕捉会话类，负责 start/poll/stop
- 适配器层只负责把两者串起来

方式 B 更接近 Waylandcraft 的结构：Java 侧有会话管理，原生侧有流维护，两者通过 JNI/IPC 协作。

### 2.3 `WaylandAdapter` 该保留什么
保留
- 对 `ProtocolBackend` 的实现
- 协议名、高层语义

替换/强化
- 不要再假装从文本协议读帧
- 至少在 `captureFrame` 内部调用真 capture 路径，或者明确标记为未完成、但结构正确
- 窗口元数据也不要一直返回硬编码样本

---

## 3. X11 侧应该怎么接

### 3.1 首先补上的是枚举
`X11Adapter.listWindows()` 现在返回空数组，建议参考 `X11WindowLister` 的思路：
- 可指定显示名
- 枚举根窗口的子窗口
- 取标题、应用标识、PID 等
- 返回 RenderCraft 能用的窗口句柄列表

### 3.2 抓帧按独立实现走
`X11Adapter.captureFrame()` 应该按 `X11Capture` 的思路实现：
- 按窗口 ID 调用 X11 获取像素
- 返回可直接用于后续处理的 RGBA 数据
- 必要时附带几何信息

### 3.3 注意事项
- 显示名/`DISPLAY`要可配，因为 RenderCraft 可能在多个显示环境下运行
- X11 路径最好能单独关闭/重连，不要跟整个适配器声明周期死死绑死
- 如果既有 Wayland 又有 X11，建议在更上层决定“抓哪个协议的窗口”，而不是让适配器互相越界

---

## 4. `FrameSnapshot` 怎么补

当前字段
- 捕捉时间
- 宽高
- 图像数据
- 格式

根据参考链路，至少还应考虑
- 来源协议类型
- 是否压缩、是什么压缩
- 是否 GPU 回读来的
- 若用于共享：帧序号、是否差异帧、是否最新帧
- 若用于同步：关联的音频基准信息

这不一定一次性全加，但起码架子里要留出扩展点。否则后面音视频同步、共享、压缩路径一接，`FrameSnapshot` 会立刻变成阻塞点。

---

## 5. 窗口变化监听怎么看

`WindowListener` 目前覆盖了：
- 创建
- 关闭
- 移动
- 尺寸变化
- 标题变化

这个覆盖是合理的。不过如果捕捉是事件驱动的，还可以补充：
- 窗口可捕捉状态变化
- 帧可用通知
- 几何语义变化（例如锚点需要的位置变化）

如果设计是轮询式的，就不必急着补事件。但轮询也得有合理的节奏控制，不能让上层Freedom地每帧猛扫。

---

## 6. 音视频矩阵怎么跟捕捉联动

这一块的关键不是“先做完整音视频服务”，而是让捕捉能把图/音的来源标清楚。

建议
- 视频捕捉至少能表达：这是 Wayland 桌面窗口帧、还是 X11 窗口帧、还是内部渲染帧
- 音频捕捉应绑定到窗口/进程语义，不要默认变成全机捕捉
- 缓冲策略开始可以简单，但丢帧语义要早定：视频最新帧覆盖、音频顺序队列

这样后续接入 `BoundedAudioVideoBuffer`、`SyncModel`、`BufferStrategy` 的时候，入口是一致的。

---

## 7. 先修顺序建议

1. 先把 X11 枚举和抓帧补成真实现
2. 把 Wayland 捕捉从假协议里剥离，接到真实的 portal/PipeWire 或真 IPC 路径
3. 扩展 `FrameSnapshot`，让它能表达来源和压缩/同步所需信息
4. 视情况拆分 `CompositorConnection` 的职责，避免它既当连接器又当假帧工厂
5. 再把捕捉输出接到缓冲/编码/共享链路

这一顺序的好处是：最容易验证的 X11 真实现先上，Wayland 再跟真路径对齐，数据结构最后再扩，避免前面花力气补了一个随后又立刻不够用的模型。

---

## 8. 不要照搬的地方

- 不要把 Waylandcraft 的 D-Bus/gdbus 细节原封不动搬过来，协议细节可能和 RenderCraft 的架构不符
- 不要照搬文本命令式的合成器通信模型，帧传输最好明确定义
- 不要假设所有窗口都能抓，失败语义要有
- 不要把音频捕捉默认设计为全系统捕捉，来源粒度要尽早想清楚

---

## 9. 小结

当前 RenderCraft 的窗口捕捉骨架是“形状正确、实现缩水”。参考 Waylandcraft 之后，最直接可行的路径是：
- X11 侧照 `X11Capture/X11WindowLister` 补全枚举与抓帧
- Wayland 侧按 portal + PipeWire 或真原生桥来实现，不再依赖文本协议
- `FrameSnapshot` 提前预留来源、压缩、同步信息
- 捕捉与音视频缓冲/分发接口提前对齐语义

这样做完这一轮，窗口捕捉就不再是“看起来有模块、实际上跑不了真截图”，而是能直接继续推音视频和共享实现的真实入口。
