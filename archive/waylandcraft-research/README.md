# waylandcraft 调研归档索引

`scapking/waylandcraft`（本地 `research/waylandcraft/`，只读）调研期间产出的文档，按"先修方向"分主题存放。

## 按主题

### IME（最关键、bug 最多）
- **`RESEARCH_CONCLUSIONS.md`** — **必读**
  - 嵌套 wayland + ibus + mutter 的 IME 不可单 mod 修复
  - 推荐 v1.2.4 stable，回滚后续 ti3 实验
  - 4 种 workaround（v1.2.4 / host session 直跑 / X11 fallback / 等 mutter 50.4+）
- `RESEARCH_NOTES.md` — IME 调研原始过程
- `TI3_REBUILD_TODO.md` — v0.13 (mod 1.2.5) 重建 zwp_text_input_v3 server 步骤
- `DIAGNOSTIC_FIX_TODO.md` — `runImeDiagnosticNative` NoSuchMethod 修复

### Capture（抓帧）
- `capture/waylandcraft-capture-reference.md` — PipeWire portal + JNA XGetImage + PBO 完整链路 + 踩坑清单
- `capture/AUDIT-rendcraft-stub.md` — RenderCraft 原 `CompositorConnection` 占位审计

### 架构 / 决策参考
- `docs/EXTERNAL_RESEARCH_SUMMARY.md` — 同类项目 landscape（YourSandwich fork、PainDeMie64 ext、Fcitx5-Enhancer 协调过滤思路、MC 26.3 SDL3 趋势）
- `docs/RenderCraft_RESEARCH_FINDINGS.md` — 调研过的外部项目清单（smithay/xcapture/echofield/stream-discord-rs 等）
- `docs/TROUBLESHOOTING.md` — waylandcraft 官方 troubleshooting（IME、诊断日志）
- `docs/IMPLEMENTATION_PLAN.md` — waylandcraft 实现计划

## 决策原则

> 不再尝试修复**嵌套 wayland IME**——这是 mutter/ibus/portal 三方协同的事，waylandcraft 单 mod 改不动。
> 转到修：bridge panic、capture session、shared window 帧路由、X11 端 JNA 路径。