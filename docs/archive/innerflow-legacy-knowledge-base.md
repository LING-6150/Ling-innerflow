# InnerFlow 历史知识库

> 本地整合版 · 2026-07-29
>
> 本文整合七份飞书文档中的设计、实验、失败记录、面试素材与历史简历版本。
> 它是一份历史档案，不代表当前已经冻结的
> **Memory Reliability and Failure Localization for Stateful AI Agents**
> 路线。当前路线、指标和简历主张以仓库内最新冻结协议及后续 M0–M4
> 结果为准。

## 使用说明

- 本文只做归档和主题重组，不把旧实验包装成当前结果。
- 相同内容只保留一份主叙述，并在“来源索引”中标记出处。
- 文档之间存在数字差异时不擅自选择，统一列入“待复核证据”。
- `historical` 表示当时真实讨论或实现，但已不再是当前项目定位。
- `still useful` 表示即使路线改变，技术证据或面试素材仍有保留价值。
- `superseded` 表示已被当前 M0–M4 协议取代，不应直接用于新实验。

---

# 一、来源索引

| 编号 | 飞书文档 | Revision | 在本知识库中的作用 |
|---|---|---:|---|
| S1 | [InnerFlow Pattern Engine V2.2 设计方案 — 语义召回生成器 + 早期 abstain](https://lcn6dqn3m0yr.feishu.cn/docx/UtwGdDDZdo3Fs5xMBDQc15D8nLe) | 8 | Pattern Engine 根因、semantic recall 审计、失败结论 |
| S2 | [InnerFlow v2 架构设计 — 记忆 + 可验证安全的陪伴 Agent（Python 重构）](https://lcn6dqn3m0yr.feishu.cn/docx/SBWOdyMiIoBVAxxx74EcII9AnCb) | 8 | 旧 v2 定位、Memory/Safety Kernel、Stage 1 范围 |
| S3 | [InnerFlow v2 · Stage 2 设计 — 记忆 eval harness + baselines + 真实 kernel](https://lcn6dqn3m0yr.feishu.cn/docx/X1hRdRC7loXkPqxivBTcMzYKnqg) | 5 | 旧 memory harness、baseline、公平性与防作弊规则 |
| S4 | [InnerFlow v2 — 面试讲稿 / Case Study](https://lcn6dqn3m0yr.feishu.cn/docx/WLMCdrMQUoa5fuxRLq8cir8knsf) | 8 | 30 秒 pitch、3–5 分钟主线、技术追问、安全叙事 |
| S5 | [InnerFlow — 简历定稿（中文，可直接抄）](https://lcn6dqn3m0yr.feishu.cn/docx/GU3wdzAKBoeni2xCGtIckoFrn5e) | 7 | 历史中文/英文 bullet、数据口径和红线 |
| S6 | [InnerFlow 简历主文档 — 三岗位版（AIE / AI-Infra / SWE · 中英）](https://lcn6dqn3m0yr.feishu.cn/docx/YJK4dIHnToRdpHxTwC7cm026nTh) | 61 | 三岗位历史版本、typed loop、故障注入和数字来源 |
| S7 | [AIE 面试准备 — InnerFlow 五大深度叙事](https://lcn6dqn3m0yr.feishu.cn/docx/X817d1zsIoUuMUxKcUHcs6FXnTL) | 5 | ReAct loop、成本、anti-gaming、止损和 MCP 口述 |

---

# 二、项目演进总览

## 2.1 Java 完整应用阶段

InnerFlow 最初是一个端到端 Java/Spring AI companion-agent：

- LangGraph4j 情绪路由与多节点编排；
- ReAct 工具调用；
- RAG / HyDE / hybrid retrieval；
- MCP 工具暴露；
- WebSocket 流式对话；
- Kafka 异步事件；
- Redis 缓存与记忆；
- L1–L5 安全路由；
- JWT、限流、监控和部署。

这一阶段证明了完整系统交付能力，但与 code-generation 项目在技术栈、
编排、流式、工具和可观测性上高度重叠。旧文档因此提出：InnerFlow 不应继续
把“又一个完整 Agent 应用”作为主峰。

**状态：still useful。** Java 底座仍是工程可信度和后续竞态修复的真实载体，
但不再承担当前项目的唯一主叙事。

## 2.2 Pattern Engine / failure-driven eval 阶段

项目随后把重点转向 Pattern Engine 与评测：

- 自建 ground-truth loader；
- B0–B3 baselines；
- human held-out；
- recall-retention、confidence、dedup 和 evidence-chain 指标；
- threshold sweep；
- candidate-generator audit；
- recoverability analysis；
- evidence-gated expansion；
- 多轮失败实验 R1–R4。

最重要的研究式发现不是“某个 prompt 更好”，而是定位出候选生成阶段的
不可逆召回损失：如果真 pattern 从未进入候选，后面的阈值、排序和 abstain
都无法恢复。

**状态：still useful。** 具体 Pattern Engine 不是当前 memory reliability
主线，但 failure localization、sealed 数据、anti-gaming 和诚实止损的方法论
直接影响了当前 M0–M4。

## 2.3 Python Memory/Safety Kernel 阶段

旧 v2 设计曾提出把 InnerFlow 重构成 Python Memory/Safety Kernel：

- 不逐接口迁移 Java；
- 不再做另一个 Agent framework；
- 主打跨会话记忆巩固、冲突消解与可验证安全；
- eval 只服务可证伪主张；
- Python 用于快速迭代 harness，Java 保留为 v1 原型。

Stage 1 先建立接口、fixtures、metrics 和最小 SafetyGuard；Stage 2 再加入
memory harness、baselines、确定性 kernel 与 LLM/embedding kernel。

**状态：historical / partially superseded。** “不做 Java 重写、harness 用
Python”的判断仍有效；“Memory/Safety 两条并列主线”已被当前更聚焦的
Memory Reliability and Failure Localization 取代。

## 2.4 当前方向

当前项目定位已冻结为：

> **InnerFlow — Memory Reliability and Failure Localization for Stateful AI Agents**

当前 M0→M4 不等同于旧 22-case memory eval，也不应直接复用旧 headline。
旧资料的价值是：

1. 保留工程实现和实验史；
2. 记录哪些方向已被证伪；
3. 保存可用于面试追问的深层素材；
4. 为当前实验提供方法论来源，而不是提供可直接搬运的结果。

---

# 三、旧 Memory/Safety Kernel 设计

## 3.1 旧 thesis

旧设计的核心主张：

1. companion agent 需要处理跨会话记忆的巩固与冲突；
2. 危机安全需要成为不可绕过、可验证的不变量；
3. 不能只把聊天历史塞入 context 或做朴素 RAG-over-chat；
4. 实现必须在矛盾率、冲突消解、相关召回和安全指标上接受 baseline 检验。

## 3.2 原边界设计

| 组件 | 旧职责 |
|---|---|
| `Agent / RuntimeLoop` | 显式状态、MAX_ITER、超时与工具失败处理 |
| `Tool` | 统一工具协议与结果回流 |
| `Memory` | 写入、检索、巩固、冲突、衰减 |
| `SafetyGuard` | 所有入口先经过危机检查 |
| `Event / Trace` | token、延迟、迭代、记忆操作记录 |
| `Session` | 会话状态与跨会话写入边界 |

旧设计强调自有业务边界，不让 LangGraph 成为公开 surface；只有存在真实图调度
需求时才把框架作为内部 runtime。

## 3.3 旧记忆能力模型

- **写入**：从会话抽取事实、偏好、触发点和进展。
- **检索**：向量相关性与结构化 user wiki 混合。
- **巩固**：多次观察合并、去重并抽象成稳定画像。
- **冲突消解**：
  - `supersede`：新事实替代旧事实；
  - `context-specific keep-both`：不同情境下两个偏好同时有效；
  - `recurrence accumulation`：重复触发累积证据。
- **衰减**：时间和频率加权，旧且未复现的观察降权。
- **provenance**：claim 关联 source observation IDs。

旧设计对 Letta / MemGPT 的理解是：借鉴工作记忆/长期记忆分层与可编辑记忆，
但把差异化集中在跨会话 conflict resolution 与 consolidation 的可测正确性。

## 3.4 旧 Stage 1 范围

第一阶段只允许：

- Python package skeleton；
- `MemoryKernel` / `SafetyGuard` / `TraceRecorder` 接口；
- fixture schema；
- metric definitions；
- 最小真实 deterministic SafetyGuard；
- schema、metric 和 safety invariant tests。

明确不做：

- LangGraph 或自定义多 Agent 编排；
- MCP / A2A / FHIR；
- FastAPI、UI、多模态；
- LLM provider、真实 embedding、向量数据库；
- OpenTelemetry；
- Java 功能迁移。

旧 review 后的安全口径：

- 每条输入都经过 `SafetyGuard.check`；
- 当前预注册 red-team 中 crisis route 正确且 `llm_allowed=False`；
- 采用 leetspeak 规整和真实危机词表，而不是逐条背测试答案；
- 不声称“never fail-open”或完备检测；
- 第三方危机描述可能误报，曾观察到 FP 约 0.25；
- bypass metric 必须同时检查 route 与 `must_block_llm`。

---

# 四、旧 Memory Eval Harness

## 4.1 旧协议

```python
class MemorySystem(Protocol):
    def ingest(self, sessions: list[Session]) -> None: ...
    def profile(self) -> MemoryProfile: ...
    def retrieve(self, query: str, k: int) -> list[str]: ...
```

评测器只面对统一协议，使 treatment 和 baseline 的输入输出一致。

## 4.2 Baselines

- **B-full**：保留全历史，不巩固；旧版 profile 是原始观察堆叠。
- **B-rag**：按相似度检索，无巩固、无冲突处理。
- **B-latest-by-key**：同 semantic key 后写覆盖。
- **B-extract-only**：抽取与检索，但不做冲突裁决。
- **Kernel-deterministic**：用规则实现 supersede / keep-both /
  recurrence accumulation。

B-latest-by-key 被认为是关键对照：它在 direct contradiction 和 stale
preference 上很强，treatment 必须在 context-specific exception 与历史保留上
胜出，才能说明贡献不只是 last-write-wins。

## 4.3 指标与防作弊

### `contradiction_rate`

- 只在同一个封闭 `semantic_key` 内比较；
- evaluated claims 与当前有效 gold facts 对齐；
- 缺失 required claim 必须受到惩罚；
- 空 profile 不能通过缩小分母获得低矛盾率。

### `relevant_recall_at_k`

- 每个 query 预注册 relevant memory IDs；
- system 只能返回自己 ingest 后产生的 observation ID；
- 不允许直接返回 evaluator 的 gold ID。

### `conflict_resolution_accuracy`

- 按 old/new observation IDs 对齐；
- 不使用容易漂移的文本字符串作为唯一键；
- baseline 不产生 resolution 时按预定义规则计分。

### 额外 guard

- `extra_claim_rate`：防止系统多吐 claim 蹭 recall；
- `false_conflict`：防止系统凭空制造冲突；
- system 只接收 observations；
- gold、case ID 和 evaluator-side semantic keys 不传给 system。

## 4.4 Fixtures 与 split

旧规划从 5 条扩展到 15–20 条，最终历史文档记录为 22 条：

- direct contradiction；
- explicit correction；
- stale preference；
- context-specific exception；
- recurring trigger；
- hard-negative / no-conflict；
- retrieval ambiguity。

使用 dev / locked / challenge splits。旧纪律是：先写 fixtures，再写 kernel
规则；locked/challenge 不用于调参。

## 4.5 历史结果

旧文档记录：

- 确定性 kernel：
  - contradiction `0.0`；
  - conflict resolution `1.0`；
  - historical retrieval `1.0`。
- last-write-wins：
  - 历史检索曾记录为 `0.0`；
  - 在 keep-both 类别失败。
- LLM kernel：
  - 初版 contradiction `0.2`；
  - conflict resolution `0.6`；
  - retrieval `1.0`；
  - extra claim `0.077`。
- 后续 prompt v2：
  - conflict resolution `0.40 → 0.80`；
  - 旧文档声称增益在 held-out challenge split 保持。

### 使用限制

这些数字属于旧 22-case harness，不能填入当前 M0/M1 指标。尤其：

- deterministic kernel 是结构化输入下的 diagnostic upper bound；
- 它不等于 raw conversation 到最终 action 的端到端表现；
- 小样本应报告 counts 和 split 构成；
- 当前新协议必须独立运行后才能形成新 headline。

---

# 五、Pattern Engine 与 Semantic Recall 审计

## 5.1 根因

`PatternRecallService.recall()` 采用 lexical cues / evidence shapes 的字面
子串匹配。真人改述不包含固定短语时，真实 pattern 不进入候选。

虽然 `PatternHyDEService` 已产生 exemplar vectors，但候选在进入语义步骤前
就被 lexical gate 截断。只有零命中时才 fallback 到全部 keys；少量误命中
反而会形成有偏候选集。

因此旧结论是：

> 主要问题不是排序，而是候选欠生成；从未生成的 candidate 不可能被后续
> threshold、verifier 或 abstain 恢复。

## 5.2 早期审计数字

| 指标 | 历史值 |
|---|---:|
| Tier A candidate recall ceiling | `4/12 = 0.333` |
| 漏标签中从未被提出 | `7/8` |
| 真人非诱饵 recall ceiling | `0/14 = 0.000` |

旧 12/12 结果被标记为 in-sample，因为候选扩展曾根据 Tier A 答案反推，不能
证明泛化。

## 5.3 被证伪的简单修法

- post-hoc LLM gate：R1–R3 未解决；
- similarity margin：R4 未解决；
- 单纯 evidence count：full-decoy 也能生成多条貌似具体 evidence；
- evidence≥2、terms≥2：曾把 full-decoy FP 压到 2，但 Tier A FP 仍为 11；
- R3：历史记录 recall `0.083`、abstain `0.917`；
- R4：F1 `0.333`，wall time `3614s`，full-decoy 仍为 `13`。

## 5.4 Semantic candidate recall audit

使用 `text-embedding-3-small` 的离线审计结果：

| 操作点 | Tier A recall | Tier A FP | Full-decoy FP |
|---|---:|---:|---:|
| topK=3, τ≤0.35 | `7/12 = 0.583` | 11 | 6 |
| topK=3, τ=0.45 | `6/12 = 0.500` | 8 | 5 |
| topK=5 | `9/12 = 0.750` | 21 | 10 |
| topK=12 | `12/12 = 1.000` | 60 | 22–24 |

结论：

- semantic recall 确实找回了 lexical gate 漏掉的真标签；
- 但 cosine 大量集中在约 0.3–0.6，阈值判别力弱；
- 整个 sweep 的 full-decoy FP floor 为 5，未达到原目标 ≤2；
- “语义召回必要但不充分”；
- recall-only 不应进入 production；
- 下一步曾被定义为 quote-level specificity、intent/valence 与
  decoy-contrastive judgment。

旧文档还记录了一个方法学 caveat：当时使用整篇日记作为一个 chunk，
chunk 粒度和通用 exemplar 可能削弱阈值判别力。更公平的后续实验应考虑句子级
chunk 与 per-pattern threshold，但不能因为这个 caveat 抹去 decoy FP 结果。

## 5.5 证据驱动止损

Pattern Structure 下游功能曾因底层 evidence pipeline 在真人文本上 F1 接近 0
而暂停。旧面试叙事把它定义为“有纪律的暂停”：

- 预注册判据未达到；
- threshold sweep 证明后处理无法恢复未生成候选；
- 暂停扩展功能，保留设计和 recovery criteria；
- 不在不可信输入上继续堆结构化能力。

**状态：still useful。** 这是问题定位和止损能力的证据，但不应作为当前简历
的首要结果。

---

# 六、Safety 设计与历史证据

## 6.1 Crisis fail-safe

历史根因：

- `EmotionAnalyzerNode` 让 LLM 返回 1–5；
- `parseInt` 失败时默认 L1；
- 危机输入若返回非数字文本，可能被错误降级。

历史修复：

- 中英文确定性危机关键词；
- 数字解析鲁棒化；
- `level = max(LLM level, deterministic keyword level)`；
- leetspeak 规整，例如 `k1ll → kill`；
- 所有入口经过 guard；
- route 与 `llm_allowed` 同时进入 bypass metric。

允许的表述：

- 在当时的预注册 red-team 集上 bypass 为 0；
- 确定性层提供 fail-safe lower bound；
- 结构路径可测试。

禁止的表述：

- 完备识别所有危机表达；
- “永不 fail-open”；
- 把小样本 0 bypass 外推为生产世界 100% 安全。

## 6.2 Anti-dependency companion safety

旧产品原则：

> “镜子，不是奶嘴。”

Agent 不制造依赖，不表达“你需要我”“我想你”“快来陪我”等强化依赖的话，
而是把用户自己的应对、进展和能力反射回去。

历史实现：

- `PetGreetingGuard` 确定性后置守卫；
- 中英文 dependency-inducing 表达检测；
- 违规时拒绝并回退到安全模板；
- red-team 单测证明守卫可捕捉构造违规；
- live adversarial eval 测量模型在 guard 前的行为；
- 危机 L5 时非语言宠物退出，让位危机流程。

历史 live eval 记录 `dependency_violation_rate = 0/6`。旧文档对此的诚实解释是：
prompt 在这 6 条对抗 fixture 上已经稳定，守卫的主要价值是防模型漂移和温度变化，
而不是声称守卫在该次运行中频繁救场。

**状态：historical / optional interview material。** 当前三条简历主线未把它列为
核心，但它仍可用于 safety-focused 面试变体。

---

# 七、Result-driven ReAct Loop

## 7.1 原始问题

旧工具回路把工具返回的原始文本直接作为 observation，并容易隐含
“返回了文本就算成功”：

- 错误字符串可能被当成业务内容；
- 空结果与失败混在一起；
- 瞬时故障没有结构化重试语义；
- 未捕获异常可能中断完整 turn；
- 不同模型理解原始错误文本的能力不稳定。

## 7.2 类型化契约

旧重构引入：

```text
SUCCESS
PARTIAL
FAILURE
```

- `SUCCESS`：正常 payload；
- `PARTIAL`：信息不完整，要求模型 hedge；
- `FAILURE`：回灌 recovery instruction，不把原始错误当事实；
- failure 时 retry once；
- 未捕获异常 fail-safe 为 FAILURE；
- 通过接口默认方法扩展，历史材料声称无需修改 6 个现有工具。

## 7.3 故障注入 A/B

历史确定性 harness：

- 两臂使用同一决策模型；
- baseline 有 naive 和 robust 两种 profile；
- treatment 使用 typed result；
- 注入 failure、partial、exception 与 flaky tool；
- harness 无真实 LLM、可重复、可进入 CI。

历史结果：

| 指标 | Baseline | Treatment |
|---|---:|---:|
| failure-handling（naive） | 20% | 100% |
| failure-handling（robust） | 60% | 100% |
| crash | 存在 | 0 |
| error-as-data | 存在 | 0 |
| flaky-tool success | 69.7% | 91.2% |
| run-to-run variance | 0.016 | 0.012 |

理论解释：一次独立重试把失败概率由 `p` 降为约 `p²`。更多重试会增加延迟和
成本，因此旧方案只重试一次并保留可配置性。

## 7.4 Live LLM validation

旧文档记录每格 N=8、temperature=0.7：

- gpt-4o-mini：baseline 100%，treatment 100%；
- gpt-3.5-turbo：baseline 80%，treatment 100%。

旧解释：

- 类型化结果不会让强模型退化；
- 对较弱模型，明确契约降低对隐式错误文本理解能力的依赖；
- 结构保证主要来自异常处理和重试，而不是让模型“更聪明”。

## 7.5 Speculative tool dispatch

旧实现边流式解析 `Action / Action Input`，识别到工具调用后立即异步派发，
让工具执行与模型剩余输出并行；下一轮 reasoning 前仍等待 tool future 完成，
因此不以跳过真实结果换取延迟。

历史开发期观察：

- 单工具路径 TTFT `2.8s → ~0.9s`。

必须保留的限定：

- 来自 Phase-1 日志和开发期观察；
- 不是多次压测分布；
- 不应包装为跨模型、跨网络条件的稳定 P95。

---

# 八、Eval 方法论资产

## 8.1 预注册

- 先冻结场景和 success criteria；
- 再写 treatment；
- 不根据逐条失败回改 gold；
- dev 可调，locked/challenge 不调；
- 小样本报告 counts 和明确分母。

## 8.2 System / evaluator 隔离

- system 只收到允许的 observations 或 conversation state；
- gold、case ID、expected resolution 只在 evaluator；
- 输出必须引用 system 实际 ingest 的 IDs；
- 不允许通过 gold IDs 或测试文件路径偷看答案。

## 8.3 Anti-gaming metrics

- 缺失 claim 不能靠空输出逃避；
- `extra_claim_rate` 抑制多吐；
- `false_conflict` 抑制凭空制造冲突；
- recall 改善必须同时看 decoy / harmful exposure；
- threshold sweep 不能恢复未生成 candidate；
- 自动指标与定性审核的解释边界要分开。

## 8.4 Failure localization

旧 Pattern Engine 已经体现：

```text
candidate generation
        ↓
evidence assembly
        ↓
ranking / verification
        ↓
abstention
        ↓
downstream use
```

每个阶段分别测量，避免仅用端到端 accuracy 掩盖真正瓶颈。这一思想后来演化为
当前 M0→M4 的 STORE / UPDATE / RETRIEVE / USE bottleneck localization。

## 8.5 诚实结论纪律

- semantic recall 提升 recall，但 decoy FP 证明它必要不充分；
- LLM kernel 不如 deterministic upper bound 时如实报告；
- prompt 已稳定时，guard 的价值定位为 defense-in-depth；
- baseline/headroom 不存在时应停止，而不是扩大功能；
- 失败实验可以成为面试素材，但不伪装成简历正向指标。

---

# 九、成本、Token 与延迟

旧文档总结的工程权衡：

1. 使用 gpt-4o-mini 而不是更大模型，需要用 eval 证明任务质量足够；
2. 记忆只注入当前有效画像和相关片段，避免全历史 context 线性增长；
3. reranker 是以成本和延迟换质量，必须通过 recall / downstream 指标判断；
4. ReAct loop 设置 2–3 次最大迭代，限制失控调用；
5. speculative dispatch 通过并行而非跳过结果降低延迟；
6. 旧 full eval 成本曾记录约 `$0.025`，但一次完整对话的精确 token 和美元成本
   当时没有系统测量。

**保留价值：** 面试时应能说明 quality / latency / cost 的取舍。

**历史盲区：** 不应声称已经建立完整 per-turn cost accounting。当前如果需要
成本 finding，应重新按真实模型价格、cache hit/miss 和 token usage 计算。

---

# 十、MCP 的诚实定位

旧项目使用 MCP 暴露工具，价值是：

- 工具定义与具体 Agent 解耦；
- 跨 Agent / 跨进程复用；
- schema 和调用协议标准化；
- 与 Agent / Tool / Runtime / Session 边界设计一致。

代价：

- 多一层协议与序列化；
- schema 维护；
- 调试链路变长；
- 单 Agent 小项目直接 function calling 可能更简单。

面试红线：

- 如果实现较薄，不把 MCP 吹成核心创新；
- 重点讲何时值得用、何时不值得用；
- 当前项目已明确放弃 FHIR 作为主线，不因历史资料再次引入。

---

# 十一、历史面试叙事

## 11.1 30 秒旧 pitch

> I had a Java companion-agent application that overlapped heavily with my
> code-generation agent project. I therefore re-conceived InnerFlow around
> cross-session memory correctness and a verifiable safety floor. I built it
> eval-first: metrics and adversarial fixtures came before implementation,
> deterministic baselines established an upper bound, and live LLM evaluation
> exposed where free-text inference still failed.

这是历史版本。当前更合适的核心名词应替换为：

> Memory Reliability and Failure Localization for Stateful AI Agents.

## 11.2 3–5 分钟旧叙事顺序

1. 诊断 Java 项目的安全 bug 与作品集同质化；
2. 先修 fail-open、安全与资源问题；
3. 把项目重新定位为 Memory/Safety Kernel；
4. 先定义指标、fixtures 和 baselines，再实现；
5. 报告 deterministic upper bound 与 LLM inference gap；
6. 说明下一步如何由失败证据决定，而不是继续堆功能。

## 11.3 五个历史深度故事

优先级曾定义为：

1. Result-driven ReAct loop；
2. Cost / token / latency tradeoffs；
3. Anti-gaming eval；
4. Evidence-driven stopping；
5. MCP boundary。

这些仍适合作为面试追问素材，但最终简历只应选择与目标岗位及当前项目 headline
一致的部分。

---

# 十二、历史简历版本

## 12.1 AIE / Agent 版（历史）

旧版本重点：

- 端到端 companion-agent 底座；
- 22-case deterministic memory kernel；
- typed result-driven ReAct loop；
- eval infrastructure；
- anti-dependency safety；
- MCP 与 L1–L5 routing。

历史代表性表达：

> Designed an eval-first long-term memory kernel for cross-session conflicts,
> stale facts, and context-specific exceptions; benchmarked a deterministic
> upper bound against full-history, RAG, last-write-wins, and extract-only
> baselines across dev/locked/challenge splits.

> Re-architected the ReAct tool loop around typed
> `SUCCESS/PARTIAL/FAILURE` results, recovery instructions, retry-once, and
> exception fail-safe behavior; validated it with deterministic fault
> injection and a small live-LLM A/B.

## 12.2 AI Infra / Platform 版（历史）

旧版本重点：

- Redis/Kafka 分布式可靠性；
- eval/reliability infrastructure；
- hybrid RAG；
- typed tool-result contract；
- Prometheus/Grafana；
- Docker/AWS。

历史候选内容：

- 三级 Redis 缓存防护；
- 幂等 Kafka consumer、分布式锁、指数退避、DLQ；
- BM25/Elasticsearch + Pinecone + HyDE + reranker；
- fault-injection A/B；
- CI 与不变量回归。

## 12.3 SDE / SWE 版（历史）

旧版本重点：

- Java 21 / Spring Boot / Vue 3 全栈；
- WebSocket、Kafka、Redis、JWT；
- 分布式可靠性；
- 非破坏式重构；
- 自动化测试和 CI；
- AI 能力放在工程基础之后。

## 12.4 历史简历红线

- 不写未测的留存、62% 或虚构延迟；
- `0 / 1.0` 必须带样本、baseline 和 upper-bound 限定；
- semantic recall `0.33 → 1.0` 必须同时说明 decoy FP；
- TTFT `2.8s → ~0.9s` 必须说明是单工具路径开发期观察；
- safety 0 bypass 必须带预注册 red-team 范围；
- 使用“实现 / 评测 / 部署于 AWS”，不随意写“上线”；
- 多模态和宠物功能不独立成为主 bullet；
- negative result 适合面试，不直接伪装成 headline。

---

# 十三、历史证据账本

| 主张 | 历史数字 | 原用途 | 当前处置 |
|---|---|---|---|
| Deterministic memory upper bound | contradiction 0 / conflict 1.0 / recall 1.0 | 旧 22-case harness | 保留，不能替代当前 M0/M1 |
| LLM conflict prompt | 0.40 → 0.80 | 旧 prompt v2 | 保留，需按原 split 和 counts 解释 |
| Semantic candidate recall | 0.333 → 1.0 | Pattern Engine audit | 保留，必须同时报 FP |
| Full-decoy FP | floor 5，topK=12 时 22–24 | 证明 recall-only 不足 | 保留为 null/negative finding |
| Crisis red-team | 历史集合 0 bypass | safety floor | 仅限旧 fixture 范围 |
| Anti-dependency live | 0/6 violation | prompt 已稳定 | 不适合作强 headline |
| Loop failure handling | 20%/60% → 100% | deterministic A/B | 保留，注意 profile 定义 |
| Flaky tool | 69.7% → 91.2% | retry-once | 保留，解释 p→p² 假设 |
| Live LLM loop | strong 100/100；weak 80→100 | 小样本 cross-model | 保留 N=8/格和 temp=0.7 |
| Speculative dispatch | 2.8s → ~0.9s | 开发期单工具日志 | 保留，但非正式压测 |
| Test count | 211 或 219 JUnit + 50 pytest | 工程规模 | **存在版本差异，使用前重算** |
| Total tests | 260+ | 工程信誉 | 使用前按当前 commit 重算 |
| Reviewed PRs | 50+ / 58 | 工程过程 | 使用前按仓库当前状态重算 |
| Old eval cost | 约 $0.025 | 成本量级 | 模型价格和配置已变化，勿直接复用 |

---

# 十四、冲突与待复核项

## 14.1 测试数量不一致

不同文档分别记录：

- `211 JUnit + 50 pytest`；
- `219 JUnit + 50 pytest`；
- 合计统一写成 `260+`。

这些可能对应不同 commit。未来使用时应在目标 commit 上重新统计，而不是选择
更大的数字。

## 14.2 “唯一全优”需要保留完整定义

必须同时说明：

- 22 条旧预注册 fixtures；
- 4 个 baseline / 5 个 systems；
- deterministic structured-input upper bound；
- 指标具体是 contradiction、conflict resolution 和 historical recall。

不能简化成“生产记忆准确率 100%”。

## 14.3 Held-out claim

旧文档声称 prompt v2 的 `0.40 → 0.80` 在 held-out challenge split 保持。
未来若继续使用，应回到原 raw result、run manifest 和逐项 counts 核验，不仅引用
总结文档。

## 14.4 “生产级”与“上线”

旧 Java 系统具有较完整工程组件，但“生产级”“上线”仍需部署、流量、告警和
真实用户证据。简历应按实际情况使用“构建”“部署”“评测”，避免外推。

---

# 十五、已暂停或不再作为主线的内容

- Pattern Structure 下游功能；
- recall-only production change；
- post-hoc LLM abstain gate；
- similarity-margin gate；
- 仅用 evidence count 的 gate；
- Java→Python 全量重写；
- 另一个多 Agent 编排框架；
- FHIR；
- 多模态和宠物卖萌作为简历主峰；
- token-saving 作为记忆项目 headline；
- therapist 拟人化或语音陪伴能力竞争。

这些内容保留在历史档案中，是为了记录判断过程，而不是等待重新启动。

---

# 十六、与当前 M0→M4 的接轨

## 16.1 可继承的方法论

- 预注册；
- frozen splits；
- baseline 公平性；
- system / evaluator 隔离；
- anti-gaming；
- counts 而非小样本假精度；
- negative result；
- bottleneck localization；
- 不在没有 headroom 时继续堆 treatment。

## 16.2 不可直接继承的结果

- 旧 22-case memory 数字；
- Pattern Engine semantic recall 数字；
- crisis / anti-dependency fixture 结果；
- typed loop 指标；
- 旧简历 headline。

它们分别属于不同 SUT、不同任务和不同评测协议。

## 16.3 当前三条候选简历主线

以下只是产品需求，不是已完成结果：

1. conflict-aware memory lifecycle；
2. Redis asynchronous compression lost-update race 与 Lua 原子修复；
3. memory reliability eval 与 bottleneck localization。

最终措辞必须等待当前 M0/M1/M2 的真实 counts 与 gate 结论。

---

# 十七、归档结论

七份文档共同留下了四类长期资产：

1. **系统资产**：Java/Spring Agent、RAG、ReAct、Redis/Kafka、安全与工具链；
2. **正确性资产**：memory conflict、typed tool results、concurrency race；
3. **评测资产**：预注册、baselines、sealed splits、anti-gaming 和 failure localization；
4. **判断资产**：语义召回必要但不充分、知道何时停止、对小样本和上界保持诚实。

当前项目不需要重新启用所有旧方向。保留这份档案的目的，是确保聚焦时没有
丢掉真实做过的工作，同时防止旧结果被误写成当前实验结论。
