# 科学计算器后端服务 — 设计文档（Spec）

- 日期：2026-09-12
- 状态：已评审，待实现
- 关联交付文档：`docs/01-需求分析.md`、`docs/02-架构设计.md`、`docs/03-AI协作记录.md`
  （本 spec 是 02-架构设计.md 的源头，实现阶段整理润色后产出）

---

## 1. 背景与目标

题目给的是"极简原始需求"：实现一套科学计算器 HTTP 后端服务，仅内存存储，无数据库、无缓存中间件、无任何第三方外部接口调用，打包为可独立运行的 runnable jar。功能边界需自行拆解。

本设计要交付的：

1. 一个 `java -jar` 直接启动、无需任何中间件的 HTTP 服务
2. 一套分层清晰、内核可独立测试的表达式求值引擎
3. 有实质内容的线程安全内存存储层（历史 + 变量 + 内存寄存器）
4. 完整的分层测试
5. 过程文档，逐步标注 AI 产出与人工校验/修正

## 2. 约束与约束解读

| 题目约束 | 本设计的落实 |
|---|---|
| Java 17 | `maven.compiler` 设为 17；使用 record、sealed interface、switch 表达式、文本块 |
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

- 表达式求值：`POST /calculator/calculate`
- 结构化算子调用：`POST /calculator/binary`、`POST /calculator/unary`
- 能力元数据：`GET /calculator/functions`
- 计算历史：记录、分页查询、详情、清空
- 变量寄存器：定义（覆盖式）、查询、列举、删除；可在表达式中引用
- 内存寄存器：`M+` / `M-` / `MR` / `MC`
- 健康检查：`GET /health`
- 统一错误响应与全局异常处理

### 3.2 不做（YAGNI）

- 用户体系、鉴权、多租户
- 持久化与落盘
- 表达式化简、符号求导、方程求解
- 复数、矩阵、高精度任意精度模式
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
│   ├── operator/                  BinaryOperator, UnaryOperator, OperatorRegistry
│   └── eval/                      Evaluator, EvaluationContext
├── api/
│   ├── CalculatorController
│   ├── HistoryController
│   ├── VariableController
│   ├── MemoryController
│   ├── MetaController             /functions, /health
│   ├── dto/                       record 形式的请求/响应体
│   └── error/                     GlobalExceptionHandler, ErrorResponse
├── service/                       CalculationService, HistoryService,
│                                  VariableService, MemoryService
├── store/                         接口 + InMemory 实现
│   ├── CalculationHistoryStore    / InMemoryCalculationHistoryStore
│   ├── VariableStore              / InMemoryVariableStore
│   └── MemoryRegisterStore        / InMemoryMemoryRegisterStore
└── config/                        CalculatorProperties
```

**依赖方向严格单向**：`api → service → store`、`service → core`、`core → 无`。`core` 不认识 `store`，求值时所需的变量通过 `EvaluationContext` 接口注入，避免内核反向依赖存储层。

`store` 采用「接口 + InMemory 实现」的原因：题目禁掉了 MySQL/Redis，但把接口留出来，"未来可替换持久化实现"才是真实成立的设计陈述，而非空话。

### 4.1 组件职责

| 组件 | 职责 | 依赖 |
|---|---|---|
| `Lexer` | 字符串 → Token 流，携带位置信息 | 无 |
| `ExpressionParser` | Token 流 → AST（递归下降） | Lexer 产物 |
| `Evaluator` | AST → `CalcNumber`，配合 `EvaluationContext` 解析变量 | AST、注册表 |
| `FunctionRegistry` | 函数名 → 函数实现的查表 | `MathFunction` 实现 |
| `OperatorRegistry` | 算子名 → 算子实现的查表（供 `/binary`、`/unary` 用） | 算子实现 |
| `CalculationService` | 编排：解析 → 求值 → 落历史；角度单位处理 | core、store |
| `HistoryService` | 历史记录的分页查询与清理 | `CalculationHistoryStore` |
| `VariableService` | 变量的增删查改与合法性校验 | `VariableStore` |
| `MemoryService` | 内存寄存器读写 | `MemoryRegisterStore` |

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
| `IDENT` | `[A-Za-z_][A-Za-z0-9_]*`，用于函数名、变量名、内置常量 |
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
           | IDENT                           // 变量或常量
           | '(' expression ')'
args       → expression (',' expression)*
```

### 6.3 优先级与结合性决定（均以测试固定）

- `^` **右结合**：`2^3^2` = `2^9` = `512`
- 一元负号优先级**低于** `^`：`-2^2` = `-4`（不是 `4`）
- `!` 阶乘**高于**所有二元算子：`3! + 1` = `7`
- 乘除模同级左结合；加减同级左结合

### 6.4 常量与函数集

**内置常量**（走 IDENT 通道；解析优先级：用户变量 → 内置常量 → 报 `UNKNOWN_VARIABLE`。
**注意**：用户变量优先，即用户可以定义 `pi = 3` 覆盖内置常量，这是刻意的选择——变量是用户显式意图，常量只是缺省值。）

- `pi` → 3.141592653589793
- `e` → 2.718281828459045

**一元函数**：
`sin` `cos` `tan` `asin` `acos` `atan` `sinh` `cosh` `tanh` `asinh` `acosh` `atanh`
`sqrt` `cbrt` `abs` `exp` `ln` `log10` `log2` `floor` `ceil` `round` `sign`

**二元函数**：
`pow(x,y)` `mod(x,y)` `hypot(x,y)` `max(x,y)` `min(x,y)` `atan2(y,x)` `log(x,base)`

**角度单位**：请求级参数 `angleUnit ∈ {DEGREE, RADIAN}`，缺省取配置项 `calculator.default-angle-unit`（默认 `DEGREE`）。仅对 `sin cos tan asin acos atan atan2` 生效，其余函数忽略此参数。

**定义域校验**（违者抛 `DOMAIN_ERROR`，非静默返回 NaN）：
- `sqrt(x)`：x ≥ 0
- `ln(x)` / `log10(x)` / `log2(x)`：x > 0
- `log(x, base)`：x > 0 且 base > 0 且 base ≠ 1
- `asin(x)` / `acos(x)`：-1 ≤ x ≤ 1
- `acosh(x)`：x ≥ 1
- `atanh(x)`：-1 < x < 1
- `x / 0`、`mod(x, 0)`：`DIVISION_BY_ZERO`
- `n!`：n 必须为非负整数，且 n ≤ 170（否则 `171!` 溢出），超出抛 `NON_FINITE_RESULT`

**非有限结果兜底**：任何运算结果为 `Infinity` 或 `NaN` 时抛 `NON_FINITE_RESULT`，不把 Inf/NaN 透出到响应体。

## 7. HTTP API 契约

统一前缀 `/api/v1`。请求与响应均为 `application/json; charset=UTF-8`。

### 7.1 计算

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

- `expression`：必填，非空，长度 ≤ 1000
- `angleUnit`：可选，缺省取配置
- `variables`：可选，**请求级临时变量**，仅本次求值生效、不落库；同名时覆盖存储中的变量

**`POST /calculator/binary`**

```json
// 请求
{ "operator": "add", "left": 1, "right": 2 }
// 响应 200
{ "operator": "add", "result": 3, "resultType": "DECIMAL", "historyId": 43, "elapsedMs": 0.11 }
```

`operator ∈ { add, subtract, multiply, divide, modulo, power }`。结构等价于表达式 `left <op> right`，同样记录历史。

**`POST /calculator/unary`**

```json
// 请求
{ "operator": "sin", "operand": 30, "angleUnit": "DEGREE" }
// 响应 200
{ "operator": "sin", "result": 0.5, "resultType": "FLOATING", "historyId": 44, "elapsedMs": 0.08 }
```

`operator` 取值同 6.4 的一元函数集。

### 7.2 能力元数据

**`GET /calculator/functions`**

```json
{
  "constants": ["pi", "e"],
  "unaryFunctions": ["sin", "cos", "..."],
  "binaryFunctions": ["pow", "mod", "..."],
  "binaryOperators": ["add", "subtract", "..."],
  "angleUnits": ["DEGREE", "RADIAN"],
  "defaultAngleUnit": "DEGREE"
}
```

供客户端发现服务能力，避免函数表硬编码在调用方。

### 7.3 历史

**`GET /history?page=0&size=20`** — 倒序（最新在前）

```json
{
  "items": [
    { "id": 44, "type": "UNARY", "expression": "sin(30)", "result": 0.5,
      "resultType": "FLOATING", "angleUnit": "DEGREE", "elapsedMs": 0.08,
      "createdAt": "2026-09-12T12:00:00Z" }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "hasNext": false
}
```

- `page` ≥ 0，缺省 0；`size` ∈ [1, 100]，缺省 20。越界参数返回 `INVALID_REQUEST`
- `type ∈ { EXPRESSION, BINARY, UNARY }`，记录该条历史的来源入口
- **`expression` 字段语义统一**：无论来源入口，历史中一律存**规范化后的中缀表达式文本**。`/binary` 与 `/unary` 的调用会被重建为中缀形式（如 `{operator:"add",left:1,right:2}` → `"1 + 2"`，`{operator:"sin",operand:30}` → `"sin(30)"`）。这样历史表结构单一，重放与展示都无需按 `type` 分支
- `angleUnit` 仅对三角函数相关的记录有意义，其余记录该字段为 `null`
- `GET /history/{id}` 返回上述 `items` 数组中的单个对象结构

**`GET /history/{id}`** — 单条详情，不存在返回 `HISTORY_NOT_FOUND`

**`DELETE /history`** — 清空，返回 `{ "deleted": 44 }`（删除条数）

### 7.4 变量

**`PUT /variables/{name}`** — **幂等 upsert**，覆盖式定义

```json
// 请求
{ "value": 5 }
// 响应 200
{ "name": "x", "value": 5, "createdAt": "...", "updatedAt": "..." }
```

- `name` 必须匹配 `[A-Za-z_][A-Za-z0-9_]*` 且长度 ≤ 64，否则 `INVALID_REQUEST`
- `name` 不得与内置常量以外的**函数名**冲突（如 `sin`），否则 `INVALID_REQUEST`（防止把 `sin` 定义成数字后 `sin(30)` 语义崩坏）
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

### 7.5 内存寄存器

**`POST /memory/{op}`**，`op ∈ { add, subtract, recall, clear }`

| 调用 | 请求体 | 语义 |
|---|---|---|
| `POST /memory/add` | `{ "value": 10 }` | `M = M + 10` |
| `POST /memory/subtract` | `{ "value": 3 }` | `M = M - 3` |
| `POST /memory/recall` | 无（忽略请求体） | 返回当前 M，不改动 |
| `POST /memory/clear` | 无（忽略请求体） | `M = 0` |

四个操作返回同一结构，`memory` 为操作完成后的 M 值：

```json
{ "operation": "add", "memory": 10 }
```

`add` / `subtract` 的 `value` 必填且必须为合法数字，否则 `INVALID_REQUEST`。

**`GET /memory`** → `{ "memory": 0 }`

**内存寄存器操作不写入历史**。理由：历史记录的语义是"计算过的算式"，`MR`/`MC` 只是寄存器读写，不是计算。同理，变量的增删改也不写入历史。这条边界在此明示，避免实现时各自发挥。

### 7.6 健康检查

**`GET /health`** → `{ "status": "UP", "uptimeMs": 12345 }`

### 7.7 错误响应与错误码

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
| 400 | `INVALID_REQUEST` | 请求体字段缺失/类型不符/越界/格式非法 |
| 400 | `UNKNOWN_FUNCTION` | 调用了未注册的函数名 |
| 400 | `UNKNOWN_OPERATOR` | `/binary`、`/unary` 传入了未注册算子 |
| 404 | `VARIABLE_NOT_FOUND` | 查询或删除不存在的变量 |
| 404 | `HISTORY_NOT_FOUND` | 查询不存在的历史记录 |
| 404 | `NO_HANDLER` | 路径不存在 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不匹配 |
| 422 | `UNKNOWN_VARIABLE` | 表达式引用了未定义变量 |
| 422 | `DIVISION_BY_ZERO` | 除零、模零 |
| 422 | `DOMAIN_ERROR` | 违反函数定义域（见 6.4） |
| 422 | `NON_FINITE_RESULT` | 结果溢出为 Inf/NaN、阶乘超界 |
| 500 | `INTERNAL_ERROR` | 兜底，不外泄堆栈信息 |

实现方式：业务异常统一继承 `CalculatorException(code, httpStatus)`，由 `GlobalExceptionHandler`（`@RestControllerAdvice`）转换为上述响应体。**不依赖 Spring 默认错误页**，`server.error.whitelabel.enabled=false`。

## 8. 并发与存储

三种存储按各自的访问模式选择并发策略，这是存储层唯一值得展开的技术点：

| 存储 | 数据结构 | 并发策略 | 理由 |
|---|---|---|---|
| 变量 | `ConcurrentHashMap<String, VariableRecord>` | 无锁读，`put` 写入 | 读多写少；单键操作天然原子，无需额外锁 |
| 历史 | `ArrayDeque<HistoryRecord>` | `ReentrantReadWriteLock` | 有**容量上限**，插入时必须原子地完成"追加 + 淘汰最旧"，`ConcurrentLinkedDeque` 无法保证精确边界；读操作占比高，读锁可并发 |
| 内存寄存器 | `AtomicReference<BigDecimal>` | CAS 循环 | 单值，`M+`/`M-` 的读改写必须原子 |

**历史容量**：默认上限 1000 条，配置项 `calculator.history-capacity`。超出时淘汰最旧记录（FIFO）。设为 ≤ 0 表示不限制（此时不再淘汰）。

**记录 id**：`AtomicLong` 单调递增，服务启动从 1 开始。**不依赖时间戳**，避免同毫秒碰撞。

**时间戳**：`createdAt` / `updatedAt` 用 `Instant`，序列化为 ISO-8601 UTC。

**内存寄存器初始值**：`BigDecimal.ZERO`。

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
  division-math-context: 34      # MathContext 有效数字位数
```

对应 `@ConfigurationProperties(prefix = "calculator")` 的 `CalculatorProperties`，启动时校验（`default-angle-unit` 必须是合法枚举，`max-expression-length` 必须 > 0），非法配置**快速失败**而非运行时才暴露。

## 10. 测试策略

分层覆盖，内核层要求分支全覆盖。

| 测试类 | 覆盖内容 |
|---|---|
| `LexerTest` | 分词正确性、位置信息、非法字符报错、数字字面量各形态 |
| `ExpressionParserTest` | AST 结构断言；**每个语法错误的 `position` 断言**；优先级与结合性 |
| `EvaluatorTest` | 四则、优先级、一元负号、阶乘边界、全部函数、全部定义域错误 |
| `PrecisionTest` | `0.1+0.2` 精确等于 `0.3`；`1/3` 的 DECIMAL128 行为；Decimal/Floating 提升规则 |
| `FunctionRegistryTest` / `OperatorRegistryTest` | 注册表完整性、未知名处理 |
| `InMemoryCalculationHistoryStoreTest` | 容量淘汰边界、并发写、分页 |
| `InMemoryVariableStoreTest` | 覆盖写、并发读写、删除 |
| `InMemoryMemoryRegisterStoreTest` | CAS 累积、并发 `M+` 不丢更新 |
| `CalculationServiceTest` | 历史落库、请求级变量覆盖、角度单位传递 |
| 各 `*ControllerTest` | `@WebMvcTest` + MockMvc，断言每个错误码与响应体结构 |
| `CalculatorEndToEndTest` | `@SpringBootTest(RANDOM_PORT)` 全链路：算 → 查历史 → 用变量 → 内存寄存器 |

**并发测试**：用 `CountDownLatch` + 多线程（如 8 线程 × 1000 次）验证存储层无丢失更新，断言最终状态精确。

**精度测试是本设计数值模型的存在理由**，必须存在且必须精确断言（用 `assertEquals` 而非 delta 比较）。

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

## 12. 风险与已知取舍

| 项 | 说明 |
|---|---|
| Spring Boot 降级 | 原脚手架为 4.1.1，按题目"SpringBoot3"约束降为 3.5.x。已在本文档记录该决策 |
| 除法的精度上限 | `1/3` 在 DECIMAL128 下为 34 位有效数字，非无限精度。这是刻意的工程取舍，已在文档明示 |
| 变量可覆盖内置常量 | `pi = 3` 是允许的。这是刻意选择（用户显式意图优先），已在 6.4 明示 |
| 单实例限制 | 内存存储意味着不支持多实例横向扩展与重启保留。这是题目约束的直接结果，非缺陷 |
| 无鉴权 | 任意调用方可读写变量与历史。题目未要求，且加了需要用户体系，超出范围 |

## 13. 验收标准

1. `mvn clean package` 成功，产出 `target/scientific-calculator-0.0.1-SNAPSHOT.jar`
2. `java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar` 直接启动，无任何中间件依赖
3. `POST /api/v1/calculator/calculate` 传 `{"expression":"1 + 2 * sin(30) ^ 2"}` 返回 `result: 1.5`
4. `0.1 + 0.2` 返回 `0.3`（精确）
5. `GET /api/v1/calculator/functions` 返回完整能力清单
6. 全部测试通过，内核层分支全覆盖
7. 三份文档齐备
