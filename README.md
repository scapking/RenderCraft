# RenderCraft — Frozen (2026-09-07)

> **状态：归档 / 只读**
>
> RenderCraft 已于 2026-09-07 决定**停止重构**。原因是：
> - `src/` 5331 行 Java 代码全部是接口/空 stub/TODO
> - 实跑实现全部在 [`scapking/waylandcraft`](https://github.com/scapking/waylandcraft) (`research/waylandcraft/`)
> - 后续工作在该上游修 bug，不在 RenderCraft 重写

## 工作流

- **修复目标**：`research/waylandcraft/`（只读参考 → 上游 PR）
- **参考归档**：`archive/waylandcraft-research/`
- **关键必读**：
  - `archive/waylandcraft-research/ime/RESEARCH_CONCLUSIONS.md` — 嵌套 wayland + IME 不可单 mod 修复
  - `archive/waylandcraft-research/capture/waylandcraft-capture-reference.md` — PipeWire portal + X11 JNA 路径
  - `archive/waylandcraft-research/docs/EXTERNAL_RESEARCH_SUMMARY.md` — 同类项目 landscape

## 归档结构

```
RenderCraft/
├── archive/
│   ├── README.md                    # 归档入口
│   └── waylandcraft-research/       # waylandcraft 调研归档（IME/capture/docs）
├── research/
│   └── waylandcraft/                # waylandcraft 完整 clone（v0.13.10）
├── src/                             # ⚠️ 空壳代码，仅作历史参考
└── docs/                            # 原 RenderCraft 调研笔记（已迁入 archive/）
```

## 历史

| 日期 | 事件 |
|------|------|
| 2026-09-04 | RenderCraft 调研启动 |
| 2026-09-05 | loom 1.13 + Gradle 9 + JDK 21 配置验证 |
| 2026-09-06 | waylandcraft clone 完成 (commit 9dc3fe0, CI 13/13) |
| 2026-09-07 | 决定冻结 RenderCraft，转修 waylandcraft；建立归档 |

详见 `archive/README.md`。