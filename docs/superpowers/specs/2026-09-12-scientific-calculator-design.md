# 科学计算器后端服务 — 设计文档（Spec）

- 日期：2026-09-12
- 版本：v2（v1 经评审后大幅收敛，变更见 §12）
- 状态：已评审，待实现
- 关联交付文档：`docs/01-需求分析.md`、`docs/02-架构设计.md`、`docs/03-AI协作记录.md`
  （本 spec 是 02-架构设计.md 的源头，实现阶段整理润色后产出）

---

## 1. 背景与目标

题目给的是"极简原始需求"：实现一套科学计算器 HTTP 后端服务，仅内存存储，无数据库、无缓存中间件、无任何第三方外部接口调用，打包为可独立运行的 runnable jar。功能边界需自行拆解。

本设计要交付的：

1. 一个 `java -jar` 直接启动、无需任何中间件的 HTTP 服务
2. 一套分层清晰、内核可独立测试的表达式求值引擎
3. 有实质内容的线程安全内存存储层（历史 + 变量）
4. 完整的分层测试
5. 过程文档，逐步标注 AI 产出与人工校验/修正

**设计基调：砍到不能再砍。** 题目没有给功能清单，意味着边界由设计者划定，那么"多做一个接口"不是加分而是负债——每多一个接口就多一份语义要维护、多一处可能自相矛盾。v1 到 v2 的评审（§12）砍掉了两个接口族和一套存储子系统，方向就是收敛。

## 2. 约束与约束解读

| 题目约束 | 本设计的落实 |
|---|---|
| Java 17 | `maven.compiler` 设为 17；使用 record、sealed interface、switch 表达式 |
| Spring Boot 3 | parent 定为 `spring-boot-starter-parent` **3.5.x**（原脚手架为 4.1.1，已确认降级以符合题目文字约束） |
| Maven | 标准 `mvn package` 产出 runnable jar |
| JUnit 5 | 由 `spring-boot-starter-test` 提供 |
| 禁止 MySQL / Redis 等第三方存储缓存 | 全部内存实现，无任何数据源依赖、无连接池 |
| 无任何第三方外部接口 | **解读为：不发起任何对外网络调用、不接入任何外部服务**。服务自身作为 HTTP 服务端对外提供接口不受此约束。`spring-boot-starter-web` 及其传递依赖 Jackson 只承担本进程内 JSON 序列化与 Web 容器职责，不构成"外部接口调用" |
| runnable jar | `spring-boot-maven-plugin` 的 `repackage` 目标 |
| 仅 HTTP 后端，无需前端 | 只交付 JSON API，不含任何静态资源与页面 |

**新增依赖仅一个**：`spring-boot-starter-web`。不加 actuator、不加 validation starter、不加任何工具库（Lombok、Guava、Commons 一律不用），以保证依赖面最小、可审计。

## 3. 范围

### 3.1 做

| 能力 | 接口 |
|---|---|
| 表达式求值 | `POST /calculator/calculate` |
| 能力元数据 | `GET /calculator/functions` |
| 计算历史 | `GET /history`、`GET /history/{id}`、`DELETE /history` |
| 变量寄存器 | `PUT /variables/{name}`、`GET /variables`、`GET /variables/{name}`、`DELETE /variables/{name}` |
| 健康检查 | `GET /health` |

### 3.2 不做（YAGNI）

- **结构化算子接口**（原 `/binary`、`/unary`）：功能被 `/calculate` 完全覆盖，见 §12 决策 D1
- **内存寄存器**（`M+` / `M-` / `MR` / `MC`）：变量可替代，见 §12 决策 D2
- 用户体系、鉴权、多租户
- 持久化与落盘
- 表达式化简、符号求导、方程求解
- 复数、矩阵、任意精度模式
- 限流、熔断、链路追踪
- 前端页面

## 4. 分层架构

核心原则：**`core` 包是纯计算内核，零 Spring 依赖**——不 import 任何 `org.springframework.*`。它因此可以被最纯粹地单测，也具备独立复用与替换的可能。Spring 只存在于 `api` / `service` / `config`。

```
com.wysjwxm.calculator
├── ScientificCalculatorApplication
├── core/                          ← 纯内核，零 Spring 依赖
│   ├── number/                    CalcNumber, DecimalNumber, FloatingNumber, Numbers
│   ├── lexer/                     Token, TokenType, Lexer
│   ├── parser/                    ExpressionParser
│   │   └── ast/                   sealed Expression + 各类节点
│   ├── function/                  MathFunction, FunctionRegistry
│   ├── operator/                  Operator, OperatorTable（解析器内部使用）
│   └── eval/                      Evaluator, EvaluationContext
├── api/
│   ├── CalculatorController       /calculate, /functions
│   ├── HistoryController          /history
│   ├── VariableController         /variables
│   ├── MetaController             /health
│   ├── dto/                       record 形式的请求/响应体
│   └── error/                     GlobalExceptionHandler, ErrorResponse
├── service/                       CalculationService, HistoryService, VariableService
├── store/                         接口 + InMemory 实现
│   ├── CalculationHistoryStore    / InMemoryCalculationHistoryStore
│   └── VariableStore              / InMemoryVariableStore
└── config/                        CalculatorProperties
```

**依赖方向严格单向**：`api → service → store`、`service → core`、`core → 无`。`core` 不认识 `store`，求值时所需的变量通过 `EvaluationContext` 接口注入，避免内核反向依赖存储层。

`store` 采用「接口 + InMemory 实现」的原因：题目禁掉了 MySQL/Redis，但把接口留出来，"未来可替换持久化实现"才是真实成立的设计陈述，而非空话。

### 4.1 组件职责

| 组件 | 职责 | 依赖 |
|---|---|---|
| `Lexer` | 字符串 → Token 流，携带位置信息 | 无 |
| `ExpressionParser` | Token 流 → AST（**优先级爬升**式递归下降，由 `OperatorTable` 驱动） | Lexer 产物、`OperatorTable` |
| `OperatorTable` | 算子优先级与结合性的**唯一事实来源**。解析器与 `/functions` 清单均由此生成，无对应 HTTP 接口 | 无 |
| `Evaluator` | AST → `CalcNumber`，配合 `EvaluationContext` 解析变量 | AST、注册表 |
| `FunctionRegistry` | 函数名 → 函数实现的查表 | `MathFunction` 实现 |
| `CalculationService` | 编排：解析 → 求值 → 落历史；角度单位处理；保留名校验 | core、store |
| `HistoryService` | 历史记录的分页查询与清理 | `CalculationHistoryStore` |
| `VariableService` | 变量的增删查改与保留名校验 | `VariableStore` |

## 5. 数值模型

### 5.1 类型

```java
sealed interface CalcNumber permits DecimalNumber, FloatingNumber {
    double toDouble();
    BigDecimal toDecimal();
}

record DecimalNumber(BigDecimal value) implements CalcNumber { }
record FloatingNumber(double value)    implements CalcNumber { }
```

**设计意图**：`0.1 + 0.2` 必须精确等于 `0.3`（计算器的基本可信度），而 `sin`/`log`/`exp` 在实现层面只能是浮点。用一个 sealed 类型把两条路径显式分开，让"哪里精确、哪里不精确"成为类型系统里可读的事实，而不是散落在各处的隐式约定。

### 5.2 类型提升与运算规则

所有提升逻辑集中在 `Numbers` 工具类，不在求值器里散落：

| 场景 | 规则 |
|---|---|
| `+ - *` 双方均 `DecimalNumber` | 走 `BigDecimal` 原生运算，结果**精确** |
| `/` | `MathContext.DECIMAL128`（34 位有效数字）。除不尽时 `1/3 → 0.3333333333333333333333333333333333` |
| `%`（取余） | 双方 `Decimal` 时走 `BigDecimal.remainder`；否则降级 double |
| 任一侧为 `FloatingNumber` | 整体提升为 `FloatingNumber`，走 `double` |
| 超越函数返回值 | 一律 `FloatingNumber` |
| `^` | 指数为非负整数且底数为 `Decimal` 时，走 `BigDecimal.pow` 保持精确；否则走 `Math.pow` |
| 结果规整 | double 结果做 `Math.rint` 微调，消除 `sin(30) = 0.49999999999999994` 一类毛刺 |

**保底不变量**：任何运算结果只要落到 `DecimalNumber`，就永远精确；一旦沾了 `FloatingNumber`，即存在浮点误差。此不变量在测试中固定。

## 6. 表达式语言规范

### 6.1 词法

| Token | 形态 |
|---|---|
| `NUMBER` | `123`、`1.5`、`.5`、`1e-3`、`1.5E+10` |
| `IDENT` | `[A-Za-z_][A-Za-z0-9_]*`，用于函数名、变量名、保留常量名 |
| `PLUS` `MINUS` `STAR` `SLASH` `PERCENT` | `+ - * / %` |
| `CARET` | `^` |
| `BANG` | `!`（阶乘） |
| `LPAREN` `RPAREN` | `( )` |
| `COMMA` | `,`（多参函数） |
| `EOF` | 流结束 |

每个 Token 携带 `position`（起始字符下标，0 基），供错误定位使用。

### 6.2 语法（递归下降）

```
expression → term (('+' | '-') term)*
term       → unary (('*' | '/' | '%') unary)*
unary      → ('+' | '-') unary | power
power      → postfix ('^' unary)?            // 右结合
postfix    → primary ('!')*
primary    → NUMBER
           | IDENT '(' args? ')'             // 函数调用
           | IDENT                           // 变量或保留常量
           | '(' expression ')'
args       → expression (',' expression)*
```

> **实现策略**：上表是**语言规范**，实现采用**优先级爬升（precedence climbing）**的递归下降解析器，由 `OperatorTable` 驱动，而非把优先级硬编码成嵌套方法。
>
> 原因：硬编码会在语法里写一遍优先级、在 `/functions` 清单里再写一遍，两处必然漂移——更糟的是 `OperatorTableTest` 会通过（清单与表一致），而表与解析器实际行为不一致，防漂移测试形同虚设。由算子表统一驱动后，`precedence`/`associativity` 只有一处定义，清单与解析器读的是同一份数据。

### 6.3 优先级与结合性（均以测试固定）

- `^` **右结合**：`2^3^2` = `2^9` = `512`
- 一元正负号优先级**低于** `^`：`-2^2` = `-4`（不是 `4`）
- `!` 阶乘**高于**所有二元算子：`3! + 1` = `7`
- 乘除模同级左结合；加减同级左结合

### 6.4 命名空间：保留常量与用户变量

**保留常量**（只读，不可被用户变量覆盖）：

- `pi` → `3.141592653589793`
- `e`  → `2.718281828459045`

**用户变量名的禁用集合 = 函数名 ∪ 保留常量名。** 即：

- `PUT /variables/sin` → 400 `INVALID_REQUEST`（与函数名冲突）
- `PUT /variables/pi`  → 400 `INVALID_REQUEST`（与保留常量冲突）

之所以把常量也列入保留名：若允许 `pi = 3`，则 `sin(pi)` 的含义会随调用方的写入操作静默改变，表达式的可重现性被破坏。常量是语言的一部分，不应是可被覆盖的缺省值。

因两个集合互斥，**IDENT 的解析无歧义**：先查用户变量，未命中则查保留常量，仍未命中抛 `UNKNOWN_VARIABLE`。

### 6.5 函数集

函数集**只收录无法用中缀运算符表达的运算**——凡有中缀写法的（幂、取余）一律不收，避免同一能力出现两种拼写（见 §12 决策 D3）。

**一元函数**（23 个）：
`sin` `cos` `tan` `asin` `acos` `atan` `sinh` `cosh` `tanh` `asinh` `acosh` `atanh`
`sqrt` `cbrt` `abs` `exp` `ln` `log10` `log2` `floor` `ceil` `round` `sign`

> 阶乘**只有后缀运算符 `!` 一种写法**，不提供 `fact(n)` 函数形式。理由同 `pow`/`mod`：`fact(3)` 与 `3!` 是同一能力的两种拼写，属设计噪音。此规则**无例外**——凡有中缀/后缀写法的一律不收。

**二元函数**（5 个）：
`hypot(x,y)`、`max(x,y)`、`min(x,y)`、`atan2(y,x)`、`log(x,base)`

**角度单位**：请求级参数 `angleUnit ∈ {DEGREE, RADIAN}`，缺省取配置项 `calculator.default-angle-unit`（默认 `DEGREE`）。仅对 `sin cos tan asin acos atan atan2` 生效，其余函数忽略此参数。

**定义域校验**（违者抛 `DOMAIN_ERROR`，非静默返回 NaN）：

| 函数 | 约束 |
|---|---|
| `sqrt(x)` | x ≥ 0 |
| `ln` `log10` `log2` | x > 0 |
| `log(x, base)` | x > 0 且 base > 0 且 base ≠ 1 |
| `asin(x)` `acos(x)` | -1 ≤ x ≤ 1 |
| `acosh(x)` | x ≥ 1 |
| `atanh(x)` | -1 < x < 1 |
| `x / 0`、`x % 0` | → `DIVISION_BY_ZERO` |
| `n!` | n 必须为非负整数，且 n ≤ 170；否则 → `NON_FINITE_RESULT` |

**非有限结果兜底**：任何运算结果为 `Infinity` 或 `NaN` 时抛 `NON_FINITE_RESULT`，不把 Inf/NaN 透出到响应体。

## 7. HTTP API 契约

统一前缀 `/api/v1`。请求与响应均为 `application/json; charset=UTF-8`。

### 7.1 表达式求值

**`POST /calculator/calculate`**

```json
// 请求
{ "expression": "1 + 2 * sin(30) ^ 2", "angleUnit": "DEGREE", "variables": { "x": 5 } }
// 响应 200
{
  "expression": "1 + 2 * sin(30) ^ 2",
  "result": 1.5,
  "resultType": "DECIMAL",
  "angleUnit": "DEGREE",
  "historyId": 42,
  "elapsedMs": 0.42
}
```

- `expression`：必填，非空字符串，长度 ≤ `calculator.max-expression-length`（默认 1000）
- `angleUnit`：可选，缺省取配置
- `variables`：可选，**请求级临时变量**，仅本次求值生效、不落库；同名时覆盖**存储中的用户变量**（这是该字段的用途：临时替换某个变量的取值）
- `variables` 的键**同样受禁用集合约束**（函数名 ∪ 保留常量名），传入 `{"pi": 3}` 返回 400 `INVALID_REQUEST`
- **保留名校验的适用范围**：存储写入（`PUT /variables/{name}`）与请求级 `variables` **两个入口统一生效**。不因为"临时、不落库"就开口子——同一表达式在不同入口下对 `pi` 的含义应当一致，否则 `sin(pi)` 的结果取决于它经由哪个入口求值，可重现性依然被破坏
- `resultType ∈ { DECIMAL, FLOATING }`，标明结果落在哪条数值路径上
- 每次成功求值**写入一条历史记录**

### 7.2 能力元数据

**`GET /calculator/functions`**

该接口是**服务能力清单（capability manifest）**：它回答"本服务接受什么样的表达式语法"。客户端据此可以自建输入校验或表达式预览，而不必把语法规则硬编码在调用方。

```json
{
  "constants": ["pi", "e"],
  "unaryFunctions":  ["sin", "cos", "...", "sign"],
  "binaryFunctions": ["hypot", "max", "min", "atan2", "log"],
  "operators": [
    { "symbol": "+", "fixity": "INFIX",   "precedence": 1, "associativity": "LEFT"  },
    { "symbol": "-", "fixity": "INFIX",   "precedence": 1, "associativity": "LEFT"  },
    { "symbol": "+", "fixity": "PREFIX",  "precedence": 3, "associativity": null    },
    { "symbol": "-", "fixity": "PREFIX",  "precedence": 3, "associativity": null    },
    { "symbol": "*", "fixity": "INFIX",   "precedence": 2, "associativity": "LEFT"  },
    { "symbol": "/", "fixity": "INFIX",   "precedence": 2, "associativity": "LEFT"  },
    { "symbol": "%", "fixity": "INFIX",   "precedence": 2, "associativity": "LEFT"  },
    { "symbol": "^", "fixity": "INFIX",   "precedence": 4, "associativity": "RIGHT" },
    { "symbol": "!", "fixity": "POSTFIX", "precedence": 5, "associativity": null    }
  ],
  "angleUnits": ["DEGREE", "RADIAN"],
  "defaultAngleUnit": "DEGREE"
}
```

`precedence` 语义：**数值越大结合越紧**（`+`=1 最松，`!`=5 最紧）。该字段是行为契约的一部分，客户端据此可判断 `1+2*3` 的求值顺序。

`-` 与 `+` 各出现两次，分别对应中缀与前置两种用法——这是**同一符号的两种语法角色**，如实列出而非合并，避免客户端误以为 `-5` 与 `1-2` 中的 `-` 优先级相同。

### 7.3 历史

**`GET /history?page=0&size=20`** — 倒序（最新在前）

```json
{
  "items": [
    { "id": 44, "expression": "sin(30)", "result": 0.5,
      "resultType": "FLOATING", "angleUnit": "DEGREE", "elapsedMs": 0.08,
      "createdAt": "2026-09-12T12:00:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

- `page` ≥ 0，缺省 0；`size` ∈ [1, 100]，缺省 20。越界参数返回 `INVALID_REQUEST`
- `expression` 存**调用方提交的原文**（仅去除首尾空白），不做规范化改写
- `angleUnit` 仅对三角函数相关的记录有意义，其余记录该字段为 `null`
- **无 `type` 字段**：v1 曾有 `type ∈ {EXPRESSION, BINARY, UNARY}` 用于区分入口，结构化接口砍掉后只剩单一入口，该字段失去意义（§12 决策 D1 的连带简化）

**`GET /history/{id}`** — 返回上述 `items` 中的单个对象结构；不存在返回 `HISTORY_NOT_FOUND`

**`DELETE /history`** — 清空，返回 `{ "deleted": 44 }`（删除条数）

### 7.4 变量

**`PUT /variables/{name}`** — **幂等 upsert**，覆盖式定义

```json
// 请求
{ "value": 5 }
// 响应 200
{ "name": "x", "value": 5, "createdAt": "...", "updatedAt": "..." }
```

- `name` 必须匹配 `[A-Za-z_][A-Za-z0-9_]*` 且长度 ≤ 64
- `name` 不得落在**禁用集合**（函数名 ∪ 保留常量名）内，否则 `INVALID_REQUEST`
- `value` 必填且必须为合法数字
- 覆盖已存在变量返回 200（非 201），语义为"整体替换"，保持 PUT 幂等性

**`GET /variables`** — 列出全部，按 `name` 字典序升序

```json
{ "items": [ { "name": "x", "value": 5,
               "createdAt": "...", "updatedAt": "..." } ],
  "total": 1 }
```

**`GET /variables/{name}`** — 返回上述 `items` 中的单个对象；不存在返回 `VARIABLE_NOT_FOUND`

**`DELETE /variables/{name}`** — 成功返回 **204 No Content**（无响应体）；不存在返回 `VARIABLE_NOT_FOUND`

> 与 `DELETE /history` 返回 `{ "deleted": <条数> }` 的差异是刻意的：批量删除需要反馈删了多少，单条删除无此信息，用 204 更符合语义。

**变量的增删改不写入历史。** 历史的语义是"计算过的算式"，变量管理不是计算。此边界在此明示，避免实现时各自发挥。

### 7.5 健康检查

**`GET /health`** → `{ "status": "UP", "uptimeMs": 12345 }`

### 7.6 错误响应与错误码

统一响应体：

```json
{
  "code": "PARSE_ERROR",
  "message": "第 7 个字符处缺少右括号",
  "position": 7,
  "timestamp": "2026-09-12T12:00:00Z",
  "path": "/api/v1/calculator/calculate"
}
```

`position` 仅在与位置相关的错误中出现，其余为 `null`。

| HTTP | code | 触发条件 |
|---|---|---|
| 400 | `PARSE_ERROR` | 语法错误，响应含 `position` |
| 400 | `INVALID_REQUEST` | 字段缺失/类型不符/越界/格式非法/**变量名落入禁用集合** |
| 400 | `UNKNOWN_FUNCTION` | 调用了未注册的函数名 |
| 404 | `VARIABLE_NOT_FOUND` | 查询或删除不存在的变量 |
| 404 | `HISTORY_NOT_FOUND` | 查询不存在的历史记录 |
| 404 | `NO_HANDLER` | 路径不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不匹配 |
| 422 | `UNKNOWN_VARIABLE` | 表达式引用了未定义变量 |
| 422 | `DIVISION_BY_ZERO` | 除零、模零 |
| 422 | `DOMAIN_ERROR` | 违反函数定义域（见 §6.5） |
| 422 | `NON_FINITE_RESULT` | 结果溢出为 Inf/NaN、阶乘超界 |
| 500 | `INTERNAL_ERROR` | 兜底，不外泄堆栈信息 |

> `UNKNOWN_OPERATOR`（v1 有）已随结构化算子接口一并删除。

实现方式：业务异常统一继承 `CalculatorException(code, httpStatus)`，由 `GlobalExceptionHandler`（`@RestControllerAdvice`）转换为上述响应体。**不依赖 Spring 默认错误页**，`server.error.whitelabel.enabled=false`。

## 8. 并发与存储

两种存储按各自的访问模式选择并发策略：

| 存储 | 数据结构 | 并发策略 | 理由 |
|---|---|---|---|
| 变量 | `ConcurrentHashMap<String, VariableRecord>` | 无锁读，`put` 写入 | 读多写少；单键操作天然原子，无需额外锁 |
| 历史 | `ArrayDeque<HistoryRecord>` | `ReentrantReadWriteLock` | 有**容量上限**，插入时必须原子地完成"追加 + 淘汰最旧"，`ConcurrentLinkedDeque` 无法保证精确边界；读操作占比高，读锁可并发 |

**历史淘汰策略为 FIFO（淘汰最旧），不是 LRU。** 理由：

1. 历史是**只追加**的，记录一旦写入就不会因被读取而增值，LRU 的价值前提（访问频次反映价值）在此不成立
2. 历史记录的价值随时间是**单调递减**的——最新的最有用。FIFO 与这个语义天然吻合
3. LRU 需要在**每次读时更新访问元数据**，读操作会退化为写操作，从而与上面"读锁可并发"的设计直接冲突。分页查询是最热的路径，不该背这个开销

> 真正有理由用 LRU 的是变量存储（访问频次确有差异），但它是无上限的，不存在淘汰问题，因此不需要。JDK 的 LRU 原语 `LinkedHashMap(accessOrder=true)` 在本设计中刻意未使用。

**历史容量**：默认上限 1000 条，配置项 `calculator.history-capacity`。超出时淘汰最旧记录。设为 ≤ 0 表示不限制（此时不再淘汰）。

**记录 id**：`AtomicLong` 单调递增，服务启动从 1 开始。**不依赖时间戳**，避免同毫秒碰撞。

**时间戳**：`createdAt` / `updatedAt` 用 `Instant`，序列化为 ISO-8601 UTC。

## 9. 配置项

`application.yaml`：

```yaml
spring:
  application:
    name: scientific-calculator

server:
  port: 8080
  error:
    whitelabel:
      enabled: false

calculator:
  default-angle-unit: DEGREE     # DEGREE | RADIAN
  history-capacity: 1000         # <=0 表示不限制
  max-expression-length: 1000
  division-precision: 34         # MathContext 有效数字位数
```

对应 `@ConfigurationProperties(prefix = "calculator")` 的 `CalculatorProperties`，启动时校验（`default-angle-unit` 必须是合法枚举，`max-expression-length` 必须 > 0），非法配置**快速失败**而非运行时才暴露。

## 10. 测试策略

分层覆盖，内核层要求分支全覆盖。

| 测试类 | 覆盖内容 |
|---|---|
| `LexerTest` | 分词正确性、位置信息、非法字符报错、数字字面量各形态 |
| `ExpressionParserTest` | AST 结构断言；**每个语法错误的 `position` 断言**；优先级与结合性（§6.3 全部三条） |
| `EvaluatorTest` | 四则、优先级、一元正负号、阶乘边界（0!、170!、171! 报错）、全部函数、全部定义域错误 |
| `PrecisionTest` | `0.1+0.2` 精确等于 `0.3`；`1/3` 的 DECIMAL128 行为；Decimal/Floating 提升规则 |
| `FunctionRegistryTest` | 注册表完整性、未知名处理、一元/二元元数校验 |
| `OperatorTableTest` | `precedence` 数值与 §7.2 清单一致（防止清单与实现漂移） |
| `InMemoryCalculationHistoryStoreTest` | 容量淘汰边界（FIFO 顺序）、并发写、分页 |
| `InMemoryVariableStoreTest` | 覆盖写、并发读写、删除 |
| `CalculationServiceTest` | 历史落库、请求级变量覆盖存储中的用户变量、**请求级变量使用保留名被拒（400）**、角度单位传递 |
| `VariableServiceTest` | **禁用集合校验**（函数名、保留常量名、非法格式、超长名） |
| `*ControllerTest` | `@WebMvcTest` + MockMvc，断言每个错误码与响应体结构 |
| `CalculatorEndToEndTest` | `@SpringBootTest(RANDOM_PORT)` 全链路：算 → 查历史 → 定义变量 → 用变量再算 |

**关键测试点**：

- **精度测试**是本设计数值模型的存在理由，必须精确断言（`assertEquals` 而非 delta 比较）
- **`OperatorTableTest` 断言清单与实现一致**：`/functions` 返回的优先级表是手写还是从 `OperatorTable` 生成，这条测试决定了两者不会漂移。**实现上直接由 `OperatorTable` 生成该清单**，测试只做兜底
- **并发测试**：`CountDownLatch` + 多线程（8 线程 × 1000 次）验证存储层无丢失更新，断言最终状态精确

## 11. 交付物

```
scientific-calculator/
├── pom.xml
├── README.md                      ← 快速上手：如何构建、启动、调接口
├── docs/
│   ├── 01-需求分析.md              ← 需求拆解、用例、边界、验收标准
│   ├── 02-架构设计.md              ← 本 spec 的整理润色版
│   └── 03-AI协作记录.md            ← 逐步标注 AI 产出 vs 人工校验/修正
├── src/main/java/...
├── src/main/resources/application.yaml
└── src/test/java/...
```

**关于 `03-AI协作记录.md`**：该文档记录每一步的 AI 辅助内容与人工校验/修正。其中"AI 产出"栏由 AI 如实填写；"人工校验、优化、修正"栏**由本人填写**——这是题目考察的核心项，不能由 AI 代写或臆造。文档交付时该栏保留为待填模板，并在文档顶部明确说明。

## 12. 设计决策记录（含被否决的方案）

本节记录评审中**做了但被推翻**的设计。保留否定方案的理由比只留结论更有价值——它说明边界是被论证出来的，不是随手划的。

| # | 决策 | 结论 | 理由 |
|---|---|---|---|
| **D1** | v1 设计了 `POST /binary`、`POST /unary` 结构化算子接口 | **砍掉** | 功能被 `/calculate` 完全覆盖：`{operator:"add",left:1,right:2}` 与 `{expression:"1+2"}` 语义等价。唯一站得住的理由是"客户端不必拼字符串"，但 §7.2 的算子清单暴露了符号映射后，客户端照清单拼即可，该理由不成立。**连带简化**：历史记录的 `type` 字段失去意义，一并删除 |
| **D2** | v1 设计了内存寄存器（`M+`/`M-`/`MR`/`MC`） | **砍掉** | HTTP 客户端可自行持有状态，且变量可完全替代。唯一不可替代点是 `M+` 的原子读-改-写，但单用户场景下无意义。**连带简化**：存储层从三种并发策略收敛为两种 |
| **D3** | v1 同时提供 `pow`/`mod` 函数与 `^`/`%` 运算符 | **删掉 `pow`/`mod`** | 同一能力两种拼写，是设计噪音。`^`/`%` 是中缀数学写法，更符合计算器心智，故保留运算符、删除函数形式。**一般化的规则**：函数集只收录无法用中缀表达的运算 |
| **D4** | v1 允许用户变量覆盖内置常量（`pi = 3`） | **改为保留名** | 覆盖会让 `sin(pi)` 的含义随写入操作静默改变，破坏表达式可重现性。常量属于语言，不属于缺省值。**连带简化**：变量名禁用集合统一为"函数名 ∪ 常量名"，且在**存储写入与请求级临时变量两个入口统一生效**，不为"临时、不落库"开口子 |
| **D5** | v1 用 `type` 字段区分历史记录来源 | **删除** | D1 的连带结果，单入口下该字段恒为同一值，是死重 |
| **D6** | 历史淘汰：FIFO 还是 LRU | **FIFO** | 详见 §8：只追加的数据无访问频次语义，且 LRU 会让读退化为写，与读锁并发设计冲突 |
| **D7** | `/health` 是否保留 | **保留** | 价值低（仅冒烟测试与探针惯例）但成本近零（5 行），且直接服务验收标准第 2 条 |
| **D8** | `/functions` 的定位 | **能力清单** | v1 把它写成并列的两个列表，导致 `^` 与 `pow` 看起来像两种能力。改为如实描述表达式语法（算子含 `fixity`/`precedence`/`associativity`），并**由 `OperatorTable` 直接生成**，杜绝清单与实现漂移 |
| **D9** | Spring Boot 版本 | **4.1.1 → 3.5.x** | 原脚手架为 4.1.1，题目文字约束为"SpringBoot3"，以题目为准 |

## 13. 风险与已知取舍

| 项 | 说明 |
|---|---|
| Spring Boot 降级 | 见 D9，已在本文档记录该决策 |
| 除法的精度上限 | `1/3` 在 DECIMAL128 下为 34 位有效数字，非无限精度。这是刻意的工程取舍 |
| `/functions` 暴露优先级 | 优先级数值成为对外契约的一部分，调整解析器优先级会改变响应。**这是有意的**——该接口的定位就是如实描述语法；`OperatorTableTest` 保证清单与实现不漂移 |
| 变量引用不可重现 | 历史记录只存表达式原文与结果，不存求值时的变量快照。若变量后续被改写，历史记录无法重放出相同结果。**刻意选择**：存快照会让历史记录体积随变量数膨胀，而"重放历史"不在范围内 |
| 单实例限制 | 内存存储意味着不支持多实例横向扩展与重启保留。这是题目约束的直接结果，非缺陷 |
| 无鉴权 | 任意调用方可读写变量与历史。题目未要求，且加了需要用户体系，超出范围 |

## 14. 验收标准

1. `mvn clean package` 成功，产出 `target/scientific-calculator-0.0.1-SNAPSHOT.jar`
2. `java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar` 直接启动，无任何中间件依赖
3. `POST /api/v1/calculator/calculate` 传 `{"expression":"1 + 2 * sin(30) ^ 2"}` 返回 `result: 1.5`
4. `0.1 + 0.2` 返回 `0.3`（精确）
5. `PUT /api/v1/variables/pi` 返回 400 `INVALID_REQUEST`（保留名生效）
6. `GET /api/v1/calculator/functions` 返回完整能力清单，且与 `OperatorTable` 一致
7. 全部测试通过，内核层分支全覆盖
8. 三份文档齐备
