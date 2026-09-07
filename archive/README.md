# RenderCraft Archive

RenderCraft 仓库从 2026-09-07 起**冻结开发**，转向在
`scapking/waylandcraft`（已 clone 到 `research/waylandcraft/`，只读参考）修复 bug。

## 本目录作用

保留 RenderCraft 调研期间产出的**有价值的纯文本成果**（调研笔记、协议参考、已知坑清单），
供 waylandcraft 后续修复时查阅。**代码全部不归档**——`src/` 下 5331 行 Java 全是空壳/TODO，
waylandcraft 已有完整实跑实现。

## 归档分类

### `archive/waylandcraft-research/` — waylandcraft 调研归档

| 子目录 | 内容 | 用途 |
|--------|------|------|
| `docs/` | 同类项目 landscape、CLI 设计范式、AV 缓冲教训、IME 现状 | 架构决策参考 |
| `capture/` | PipeWire portal + X11 XGetImage 接入路径、PBO/共享帧路由实战 | 抓帧 bug 排查参考 |
| `capture/AUDIT-rendcraft-stub.md` | RenderCraft 原 `src/` 抓帧骨架审计（结论：骨架对、实现是占位） | 历史决策存档 |
| `ime/` | waylandcraft IME 调研结论、嵌套 wayland + ibus focus state 限制、ti3 重建 | IME bug 排查主参考 |
| `ime/RESEARCH_CONCLUSIONS.md` | **必读** — 嵌套 wayland IME 不可单 mod 修复的最终结论 | 避免重复无效修复 |

### `research/` — 调研参考子项目（不动）

git 子项目：smithay / xcapture / xcb-window-capture / echofield / SpatialAudio /
Discord-video-stream / discord-selfstream / stream-discord-rs / BuroSound /
Resounding / xcapture。

waylandcraft 作为唯一只读参考源。

## 不归档

- `src/main/java/dev/scapking/rendcraft/` 全部空壳 Java 代码
- `build/` 编译产物
- `.gradle/` gradle 缓存
- `research/exa_research_round1.md` — Exa 原始搜索结果，结论已纳入 `archive/waylandcraft-research/docs/EXTERNAL_RESEARCH_SUMMARY.md`

## 工作流

1. **遇到 bug** → 先查 `archive/waylandcraft-research/ime/RESEARCH_CONCLUSIONS.md` 看是否生态层已知限制
2. **不重复造轮** → waylandcraft 已有实跑实现，对应功能去 `research/waylandcraft/src/` 和 `research/waylandcraft/native/` 看
3. **决策存档** → 任何"为什么不做 Y"的决策写 `archive/waylandcraft-research/decision/`

## 历史

- 2026-09-04：RenderCraft 调研启动
- 2026-09-06：waylandcraft clone 完成（v0.13.10 / mod 1.2.14）
- 2026-09-07：决定放弃 RenderCraft 重构，转修 waylandcraft；本归档建立