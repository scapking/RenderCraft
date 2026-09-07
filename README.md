# RenderCraft — Frozen (2026-09-07)

> **状态：归档 / 只读**
>
> RenderCraft 已于 2026-09-07 决定**停止重构**。原因是：
> - `src/` 5331 行 Java 代码全部是接口/空 stub/TODO
> - 实跑实现全部在 [`scapking/waylandcraft`](https://github.com/scapking/waylandcraft)
> - 后续工作在该上游修 bug，不在 RenderCraft 重写

## 本仓库

RenderCraft 主仓库，仅含：
- `src/` — Java 骨架（冻结，不再开发）
- `build.gradle` / `gradle*` — 构建配置
- `README.md` — 本文件（冻结声明）
- `LICENSE` — MIT
- `docs/` — 原 RenderCraft 调研笔记（保留作为历史）
- `research/` — 调研期间 clone 的参考子项目（smithay/xcapture/echofield/stream-discord-rs 等，非 waylandcraft）

## waylandcraft 调研归档（独立位置）

已迁出本仓库，在 **`/home/hermes/rendcraft-archive/`**：

```
/home/hermes/rendcraft-archive/
├── README.md                              # 归档入口
└── waylandcraft-research/                 # waylandcraft 调研归档
    ├── ime/                               # IME 调研（必读 RESEARCH_CONCLUSIONS.md）
    ├── capture/                           # PipeWire portal + X11 JNA + PBO
    ├── docs/                              # 外部项目 landscape / AV / CLI
    └── README.md
```

## 工作流

1. **修复目标**：`scapking/waylandcraft` 仓库
2. **查阅归档**：`/home/hermes/rendcraft-archive/waylandcraft-research/`
3. **关键必读**：
   - `waylandcraft-research/ime/RESEARCH_CONCLUSIONS.md` — 嵌套 wayland + IME 不可单 mod 修复
   - `waylandcraft-research/capture/waylandcraft-capture-reference.md` — PipeWire portal + X11 JNA 路径
   - `waylandcraft-research/docs/EXTERNAL_RESEARCH_SUMMARY.md` — 同类项目 landscape

## 历史

| 日期 | 事件 |
|------|------|
| 2026-09-04 | RenderCraft 调研启动 |
| 2026-09-05 | loom 1.13 + Gradle 9 + JDK 21 配置验证 |
| 2026-09-06 | waylandcraft clone 完成 (commit 9dc3fe0, CI 13/13) |
| 2026-09-07 | 决定冻结 RenderCraft，转修 waylandcraft；建立归档；归档迁出至 `/home/hermes/rendcraft-archive/` |