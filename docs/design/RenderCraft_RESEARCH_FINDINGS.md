# RenderCraft 研究调查结果

创建时间: 2026-09-04

## 参考项目和找到的实现

### 1. Wayland 协议和窗口捕获

- Smithay/smithay
  - https://github.com/Smithay/smithay
  - Rust Wayland compositor 构建模块
  - 支持核心协议、官方扩展、部分 wlroots/KDE 扩展
  - 示例 compositor: anvil、smallvil
  - RenderCraft 参考点：协议抽象思路、窗口捕获协议接口模式
  - 本地克隆: /tmp/RenderCraft/research/smithay

- Smithay 图像捕获协议
  - ext-image-capture-source-v1
  - ext-image-copy-capture-v1
  - 文档位置: smithay/docs/wayland/image_capture_source/
  - RenderCraft 用途: 窗口图像捕获协议设计参考

### 2. X11 窗口捕获

- xcapture
  - https://github.com/dominikh/xcapture
  - 命令行 X11 窗口录制工具
  - 按窗口 ID 捕获，支持帧率和尺寸控制
  - 本地克隆: /tmp/RenderCraft/research/xcapture

- xcb-window-capture
  - https://github.com/AndreyBarmaley/xcb-window-capture
  - 基于 ffmpeg/xcb/pulseaudio 的 X11 窗口录制
  - 本地克隆: /tmp/RenderCraft/research/xcb-window-capture

### 3. 输入法 (IME)

- 核心参考
  - NLR-DevTeam/Fcitx5-Enhancer
  - bczhc/glfw (IME 支持补丁)
  - 0x484558/glfw-mc
  - wayland-fixes / Wayland Fix

- Minecraft IME 现状
  - MC-306616: Linux 上 IME 切回英文有问题
  - Minecraft 26.3 开始用 SDL3，改善 Linux Wayland 原生支持
  - 社区趋势: GLFW/SDL3 补丁 + 第三方模组填补

- RenderCraft 设计方向
  - 不依赖模拟层，原生兼容 Minecraft
  - 窗口本身就是 Minecraft 的一部分
  - 支持所有输入法(中文、英文、日文及其他)
  - 使用 text-input v3 接口思路

### 4. 音视频缓冲和延迟

- Discord Go Live 架构
  - 多进程管道: streamer/viewer/backend 三端协调
  - 捕获有 fallback 系统
  - 缓存: 服务端和客户端各 4-8 秒
  - 自适应编码器，根据网络条件调整
  - 音画同步: RTP 分别发送，接收端同步

- discord-video-stream
  - https://github.com/Discord-RE/Discord-video-stream
  - WebRTC、VP8/H264、NVENC 硬件编码
  - 自适应比特率控制
  - 缓冲策略: 不要无限增长缓存，避免延迟飞升
  - 参考文件: PERFORMANCE.md

- discord-selfstream
  - https://github.com/baoayano2/discord-selfstream
  - 架构: 生命周期状态机
  - 自动恢复: FFmpeg 失败、RTC 损失、网关重新连接
  - 参考文件: docs/PLAN.md

- stream-discord-rs
  - https://github.com/Tky567/stream-discord-rs
  - Rust 实现
  - 特性: DAVE/E2EE 加密、WebRTC ICE/DTLS-SRTP、所有视频编解码器、硬件编码器(NVENC/VA-API)、AV 同步(PTS 时间戳)
  - 本地克隆: /tmp/RenderCraft/research/stream-discord-rs

- 缓冲设计教训
  - MAX_SINK_BUFFER_BYTES ~3 帧上限
  - 超限丢弃最新帧
  - 避免 OOM 风险
  - 延迟绑定

### 5. 空间音频和区块声音传播

- Sound Physics Remastered
  - https://github.com/henkelmax/SoundPhysicsRemastered
  - 功能: 真实声音衰减、混响、区块吸声
  - 本地克隆: 失败(认证问题)
  - 替代参考: Modrinth 页面

- SpatialAudio (ZCRAFT-NPE)
  - https://github.com/ZCRAFT-NPE/SpatialAudio
  - 功能: 并行波追踪、改进物理算法、真实声音物理
  - 本地克隆: /tmp/RenderCraft/research/SpatialAudio

- EchoField
  - https://github.com/willnap/echofield
  - 功能: 基于物理的空间音频、HRTF 处理、自然声音环境、15,000+ 行 Java、172 个文件
  - 系统架构: 三层架构、客户端通过 OpenAL EFX、Mixin 注入
  - 本地克隆: /tmp/RenderCraft/research/echofield

- SoundPhysicsPerfected
  - https://github.com/FalseMSP/SoundPhysicsPerfected
  - 功能: 光线追踪音频、从玩家位置射线投射
  - 本地克隆: /tmp/RenderCraft/research/SoundPhysicsPerfected

- BuroSound
  - https://github.com/plngvln/BuroSound
  - 功能: 3D 区域音乐/环境音、sounds.json 配置、框选区域触发、允许重叠
  - 参考区块声音传播设计
  - 本地克隆: /tmp/RenderCraft/research/BuroSound

- Resounding
  - https://github.com/thedocruby/resounding
  - 功能: 并行波追踪、真实声音物理、客户端 Fabric 模组
  - 本地克隆: /tmp/RenderCraft/research/resounding

## RenderCraft 待实现功能映射

### 协议层(Protocol Abstraction)
- [ ] WaylandAdapter 具体实现
- [ ] X11Adapter 具体实现
- [ ] 参考 Smithay 协议接口设计

### 窗口管理
- [ ] 窗口捕获实现(图像捕获协议)
- [ ] 参考 xcapture/X11 窗口 ID 获取方式
- [ ] 参考 Smithay 窗口枚举

### 输入法(IME)
- [ ] 原生 text-input v3 接口整合
- [ ] 支持中英文日文及其他输入法
- [ ] 参考 Fcitx5-Enhancer 协调/过滤层设计
- [ ] 监控输入法状态

### 音视频
- [ ] 音频捕获(PipeWire 或类似方式)
- [ ] 音频编码
- [ ] 服务端 4-8 秒缓存
- [ ] 客户端 4-8 秒预存
- [ ] 自适应比特率控制
- [ ] 音画同步机制
- [ ] 参考 Discord Go Live 架构

### 区块声音传播(新目标)
- [ ] 以窗口为中心的声音发射
- [ ] 周围区块范围声音传播
- [ ] 范围外玩家收不到声音
- [ ] 服务端权威范围判定
- [ ] 参考 SpatialAudio 和 BuroSound 的区域设计

### 多人共享
- [ ] 共享窗口功能
- [ ] 参考方式landcraft 共享设计
- [ ] 服务器权限模型

### CLI
- [ ] 简洁明了的命令树
- [ ] 参考方式landcraft 命令设计
- [ ] 当前已有 /rc 命令树框架

## 已克隆参考项目列表

| 项目 | 本地路径 | 用途 |
|------|---------|------|
| Smithay | /tmp/RenderCraft/research/smithay | Wayland 协议参考 |
| xcapture | /tmp/RenderCraft/research/xcapture | X11 窗口捕获 |
| xcb-window-capture | /tmp/RenderCraft/research/xcb-window-capture | X11 窗口捕获 |
| echofield | /tmp/RenderCraft/research/echofield | 空间音频参考 |
| SpatialAudio | /tmp/RenderCraft/research/SpatialAudio | 空间音频参考 |
| Discord-video-stream | /tmp/RenderCraft/research/Discord-video-stream | 音视频架构 |
| stream-discord-rs | /tmp/RenderCraft/research/stream-discord-rs | 音视频架构(Rust) |
| discord-selfstream | /tmp/RenderCraft/research/discord-selfstream | 生命周期设计 |
| BuroSound | /tmp/RenderCraft/research/BuroSound | 区域声音设计 |
| Resounding | /tmp/RenderCraft/research/resounding | 空间音频物理 |

## 注意事项

1. Sound-Physics 克隆失败(认证问题)，可通过 Modrinth 或其他方式获取
2. 研究项目仅供参考，不直接合并到 RenderCraft
3. 重点是理解架构和设计思路，而不是复制代码
4. RenderCraft 保持 MIT 许可证，使用参考项目时注意许可证兼容性

## 当前实现状态总结

### 已有代码(Java)
- ProtocolBackend.java (接口)
- WindowHandle.java
- WindowMetadata.java
- ProtocolException.java
- WindowManager.java (线程安全状态机)
- WindowState.java (状态枚举)
- TemplateLayoutManager.java (约束求解布局)
- TemplateSlotConfig.java (模板槽位配置)
- ImeInputBridge.java (IME 桥接器)
- ImeStatusMonitor.java (输入法状态监控)
- NativeInputAdapter.java (原生输入适配)
- RcCommandTree.java (Brigadier 命令树)
- BoundedAudioVideoBuffer.java (有界缓冲实现)
- BufferStrategy.java (缓冲策略接口)
- RenderCraftCommon.java
- InputModeController.java

### 缺失功能
1. WaylandAdapter 具体实现
2. X11Adapter 具体实现
3. 音频捕获功能
4. 音频编码功能
5. 基于区块的声音传播功能(新目标)
6. 多人共享功能
7. 客户端/服务器 BufferManager
8. SyncModel(音画同步管理器)
9. AdaptiveBitrateController

### 研究结论应用优先级
1. 协议适配器(先有协议层才能捕获窗口)
2. 窗口捕获实现
3. 输入法原生支持
4. 音视频捕获和编码
5. 区块声音传播
6. 多人共享
