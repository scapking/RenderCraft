# RenderCraft 功能评估与音频实现方案

## 1. RenderCraft 是否可以实现 scapking/waylandcraft 的功能

### 已有基础
RenderCraft 已经具备以下核心组件：

- **协议抽象层** (`ProtocolBackend`, `WindowHandle`, `WindowMetadata`)：支持 Wayland/X11 协议，后续可扩展其他协议
- **窗口管理** (`WindowManager`, `WindowState`)：线程安全，支持窗口状态机、显示/隐藏/关闭
- **模板布局** (`TemplateLayoutManager`, `TemplateSlotConfig`)：约束求解器，改善窗口移动体验
- **CLI** (`RcCommandTree`, `RcCommandExecutor`, `RcCommandRegistration`)：基于 Brigadier 的 `/rc` 命令树，精简了混乱的 CLI
- **音视频缓冲** (`BoundedAudioVideoBuffer`, `BufferStrategy`)：为延迟缓存奠定基础
- **输入法** (`ImeInputBridge`, `ImeStatusMonitor`, `NativeInputAdapter`)：原生兼容 Minecraft，不依赖模拟层
- **快捷键** (`KeyboardShortcutRegistry`, `QuickOperationRegistry`)：统一管理快捷键，集中绑定

### 与 waylandcraft 的功能对比

| 功能 | waylandcraft | RenderCraft | 备注 |
|------|--------------|-------------|------|
| Wayland 窗口捕获 | ✅ | ✅ (ProtocolBackend 接口) | 需要具体实现 (WaylandAdapter) |
| X11 支持 | ✅ (xwayland-satellite) | ✅ (ProtocolBackend 接口) | 需要具体实现 (X11Adapter) |
| 窗口显示/隐藏 | ✅ | ✅ | WindowManager 已实现 |
| 窗口移动/旋转/缩放 | ✅ | ✅ (TemplateLayoutManager) | 约束求解器可实现更好的移动体验 |
| 模板布局 | ✅ (体验较差) | ✅ (改进版) | ConstraintSolver 提供更好的布局体验 |
| CLI 命令 | ✅ (混乱) | ✅ (精简) | `/rc` 命令树，基于 Brigadier |
| 输入法 | ❌ (不可用) | ✅ (原生兼容) | ImeInputBridge 等 |
| 快捷键 | ❌ (不统一) | ✅ (统一管理) | KeyboardShortcutRegistry |
| 音视频流 | ❌ (音频不可用) | ⏳ (基础框架已建立) | 需要实现音频流和基于区块的声音传播 |
| 多人共享 | ✅ | ⏳ (待实现) | 需要实现共享功能 |
| 高可用/高扩展 | ❌ | ✅ (部分) | 线程安全、状态验证、参数验证 |

### 结论

RenderCraft **可以实现** waylandcraft 的核心功能（窗口捕获、显示、布局、CLI 等），并且在以下几个方面进行了改进：
- 输入法原生兼容（waylandcraft 中不可用）
- 快捷键统一管理（waylandcraft 中不统一）
- CLI 精简（waylandcraft 中混乱）
- 模板布局体验改善（waylandcraft 中较差）
- 线程安全和高可用设计

但需要注意：
- 具体的 WaylandAdapter 和 X11Adapter 实现尚未编写
- 音频功能尚未实现（waylandcraft 中音频不可用，RenderCraft 也需要实现）
- 多人共享功能尚未实现

---

## 2. 是否使用调研获得方式解决输入法问题

### 是的，RenderCraft 的输入法实现基于调研结果

根据之前的 Exa 搜索调研：

1. **NLR-DevTeam/Fcitx5-Enhancer**：提供了 Fcitx5 兼容，采用"协调/过滤层 + 原生 Wayland IME 支持"的思路
2. **bczhc/glfw**：提供了 IME 支持的 GLFW patch（glfwSetPreeditCallback 等）
3. **Minecraft 26.3 Snapshot 4**：开始使用 SDL3 替代 GLFW，Linux 下原生偏向 Wayland，这是个契机

RenderCraft 的输入法设计遵循了调研结论：
- **不依赖模拟层**：ImeInputBridge 基于原生 Minecraft text-input v3 接口
- **原生兼容 Minecraft**：窗口本身就是 Minecraft 的一部分，输入法事件直接接入 Minecraft 的文本输入通道
- **支持所有输入法**：通过 ImeStatusMonitor 监控输入法状态，支持 Fcitx5、IBus 等
- **支持所有协议**：通过 ProtocolBackend 接口，支持 Wayland、X11 等协议

---

## 3. 音视频问题

### 调研结论

根据之前的 Exa 搜索：

1. **Discord Go Live 架构**：多进程管道，捕获有 fallback 系统，每个组件高效
2. **缓冲策略**：客户端和服务端各缓存 4-8 秒，吸收短时卡顿
3. **丢帧策略**：缓冲不可无限增长，否则延迟飞升（参考 discord-plays-mario-kart 的 MAX_SINK_BUFFER_BYTES）
4. **音画同步**：需要统一的呈现时钟，音频和视频线程分离但要相互校准

### RenderCraft 的音视频实现

RenderCraft 已经建立了基础框架：
- `BoundedAudioVideoBuffer`：有界缓冲，支持 DROP_OLDEST 和 DROP_NEWEST 策略
- `BufferStrategy`：缓冲策略接口
- `ClientBufferManager`：客户端播放缓冲管理器
- `ServerBufferManager`：服务端发射缓冲管理器
- `SyncModel`：音画同步管理器
- `AdaptiveBitrateController`：自适应比特率控制器

**但音频功能尚未完全实现**，需要补充：
- 音频捕获功能
- 音频编码功能
- 基于区块的声音传播功能（新目标）

---

## 4. 新目标：以窗口为中心向周围区块发出声音，不在范围内收不到

### 需求分析

- 声音以窗口为中心
- 向周围区块发出声音
- 不在范围内的玩家收不到声音
- 需要共享功能（多人游戏时其他玩家也能听到）

### 需要调研的内容

1. **Minecraft 中的基于区块的声音传播**
   - Minecraft 本身的声音传播机制
   - 如何实现基于区块的范围限制

2. **空间音频/区域音频实现方案**
   - 类似功能在其他游戏/模组中的实现
   - 基于距离衰减的音量控制

3. **多人游戏中的共享音频**
   - 如何在服务器上同步音频
   - 基于权限的音频访问控制

### 下一步

使用 Exa 搜索类似的声音实现方案，然后在 RenderCraft 中实现基于区块的声音传播功能。

---

## 5. 待办事项

1. ✅ 完成 RenderCraft 项目创建 (MIT)
2. ✅ 实现协议抽象层
3. ✅ 实现输入法原生兼容
4. ✅ 实现快捷键统一
5. ✅ 实现模板布局管理器
6. ✅ 实现音视频缓冲基础框架
7. ⏳ 实现音频捕获和编码功能
8. ⏳ 实现基于区块的声音传播功能（新目标）
9. ⏳ 实现多人共享功能
10. ⏳ 编写具体的 WaylandAdapter 和 X11Adapter 实现

---

*根据方式landcraft 调研结果和 RenderCraft 现有基础评估。*
