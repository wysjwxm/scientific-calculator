# 科学计算器后端服务 — 设计文档（Spec）

- 日期：2026-09-12
- 版本：v3（v2 的表达式语言与数值模型不变；v3 将架构风格改为 DDD 四层，变更见 §12 D10–D13）
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

## 4. 领域驱动设计（DDD）落地

### 4.0 限界上下文与统一语言

**限界上下文只有一个：表达式计算上下文。** 变量、求值、历史三者共用同一套词汇——"变量名"在这三处含义完全相同，不存在需要翻译的同形异义词，也没有需要隔开的团队边界。硬拆成多个上下文只会引入没有翻译内容的防腐层。**判断"不拆"与判断"拆"同样是 DDD 的设计动作**，此处如实记录结论。

统一语言（Ubiquitous Language）——代码类名与文档用语一一对应，不使用同义词、不做别名：

| 领域概念 | 类型 | 代码元素 |
|---|---|---|
| 表达式原文 | 值对象 | `ExpressionText`（构造即校验：非空白、长度上限） |
| 表达式 | 值对象 | `Expression`（sealed AST 及节点） |
| 数值 | 值对象 | `CalcNumber` / `DecimalNumber` / `FloatingNumber` |
| 算符 | 值对象 | `Operator`、`OperatorTable` |
| 函数 | 值对象 | `MathFunction`、`UnaryFunction`、`BinaryFunction` |
| 保留常量 | 值对象 | `MathematicalConstant` |
| 保留名集合 | 值对象 | `ReservedNames`（函数名 ∪ 常量名） |
| 角度制 | 值对象 | `AngleUnit` |
| 变量名 | **值对象** | `VariableName`（构造即校验，无旁路） |
| 变量 | 实体 | `Variable`（身份 = `VariableName`，含 created/updated 生命周期） |
| 变量表 | **聚合根** | `VariableSet` |
| 计算 | 实体 | `Calculation`（身份 = id） |
| 计算历史 | **聚合根** | `CalculationHistory` |

**为什么 `VariableName` 必须是值对象。** 保留名规则若只放在应用服务的方法里，它就只是"记得调用才生效"的纪律——`CalculationService` 漏调一次，`{"pi": 3}` 就能绕过（v2 的 `VariableService.validateVariableName()` 正是这个形态，且需要调用方从另一个服务上调它）。做成值对象后，名字在**构造**时即完成校验，进入求值器的变量集合类型是 `Map<VariableName, CalcNumber>`——**不合格的名字根本造不出对象**。不变量由类型系统保证，而非由调用者的自觉保证。

**校验可以完全放进构造器**，因此不需要额外的领域服务：函数集（`UnaryFunction` / `BinaryFunction`）与常量集（`MathematicalConstant`）都是编译期固定的枚举，保留名集合 `ReservedNames.standard()` 因此是静态可求的，不依赖任何运行时配置或外部状态。`VariableName` 的规范构造器于是能独立完成格式、长度、保留名三项校验——**构造即校验，无旁路**。见 §12 D13。

### 4.1 分层

DDD 四层，依赖方向严格单向：

```
interfaces       用户接口层   Controller、DTO、错误状态映射
                              ← 同时是防腐层（ACL）：JSON ↔ 领域对象，两侧不互相渗透
application      应用层       用例编排、配置注入 —— 不含业务规则
domain           领域层       全部业务规则；零框架依赖
infrastructure   基础设施层   聚合根的内存实现、Spring 装配

依赖方向：interfaces → application → domain ← infrastructure
```

`domain` 不依赖任何其他层，也不 import 任何 `org.springframework.*`，因此可被最纯粹地单测。`infrastructure` 反向依赖 `domain`（实现其声明的聚合根接口），装配在启动时完成。

```
com.wysjwxm.calculator
├── ScientificCalculatorApplication
├── domain/                              ← 零框架依赖
│   ├── model/
│   │   ├── expression/                   Expression(sealed) 及节点,
│   │   │                                 ExpressionText, Operator, OperatorTable,
│   │   │                                 Fixity, Associativity, Token, TokenType
│   │   ├── expression/parse/             Lexer, ExpressionParser          ← 领域服务
│   │   ├── expression/eval/              ExpressionEvaluator,
│   │   │                                 EvaluationContext, AstInspection ← 领域服务
│   │   ├── number/                       CalcNumber, DecimalNumber,
│   │   │                                 FloatingNumber, Numbers          ← 领域服务
│   │   ├── function/                     MathFunction, UnaryFunction,
│   │   │                                 BinaryFunction, FunctionRegistry,
│   │   │                                 ReservedNames
│   │   ├── variable/                     VariableName(VO, 构造即校验),
│   │   │                                 Variable(实体), VariableSet(聚合根, 接口)
│   │   └── calculation/                  Calculation(实体), PageResult,
│   │                                     CalculationHistory(聚合根, 接口)
│   ├── AngleUnit, MathematicalConstant
│   └── error/                            CalcErrorCode, CalcException
├── application/                          CalculationUseCase, VariableUseCase,
│                                         HistoryUseCase, CalculationPolicy
├── infrastructure/                       InMemoryVariableSet,
│   │                                     InMemoryCalculationHistory
│   └──config/                            CalculatorProperties, CalculatorConfiguration
└── interfaces/                           CalculatorController, HistoryController,
                                          VariableController, MetaController,
                                          dto/, error/
```

**聚合根与仓储在本设计中合并，这是刻意的。** 教科书里 `VariableSet`（聚合根）与 `VariableRepository`（仓储）是两个类型，仓储负责加载/保存聚合。但那套分离的前提是**存在持久化机制**——加载要查库、保存要写库，是真实的、可能失败的操作，值得单独抽象并注入事务语义。本系统纯内存，聚合根本身就是存储，再加一层只是把调用原样转发一遍。因此：**领域层声明聚合根接口（`VariableSet`、`CalculationHistory`），基础设施层提供内存实现**，不制造空的仓储层。见 §12 D11。

**`infrastructure` 不向 `application` 反向暴露配置类。** `CalculatorProperties` 是 Spring 绑定类，属基础设施关切，放在 `infrastructure/config`；由同包的 `CalculatorConfiguration` 把它翻译成下游能用的 bean：

- `Numbers`（注入 `divisionPrecision`）→ 领域服务
- `FunctionRegistry`、`ReservedNames` → 领域对象
- `CalculationPolicy`（`defaultAngleUnit` + `maxExpressionLength`）→ **应用层的纯 Java record，无框架残留**
- `InMemoryCalculationHistory`（注入 `historyCapacity`）→ 基础设施自身消费

这样 `application` 只依赖 `CalculationPolicy` 这个无注解的 record，不 import `CalculatorProperties`，依赖方向不被配置类破口。

### 4.2 组件职责

| 组件 | 层 | 职责 | 依赖 |
|---|---|---|---|
| `Lexer` | domain | 字符串 → Token 流，携带字符位置 | 无 |
| `ExpressionParser` | domain | Token 流 → `Expression`（**优先级爬升**式递归下降，由 `OperatorTable` 驱动） | Lexer 产物、`OperatorTable` |
| `OperatorTable` | domain | 算子优先级与结合性的**唯一事实来源**。解析器与 `/functions` 清单均由此生成 | 无 |
| `ExpressionEvaluator` | domain | `Expression` → `CalcNumber`，配合 `EvaluationContext` 解析变量 | AST、注册表 |
| `ExpressionText` | domain | 表达式原文的值对象，构造即校验非空白与长度上限 | 长度上限以 `int` 参数传入（**不依赖 `CalculationPolicy`——那是应用层类型，依赖方向不允许**） |
| `FunctionRegistry` | domain | 函数名 → 函数实现的查表 | `MathFunction` 实现 |
| `ReservedNames` | domain | 保留名集合（函数名 ∪ 常量名）的值对象，`standard()` 静态可求 | `UnaryFunction`、`BinaryFunction`、`MathematicalConstant` |
| `VariableName` | domain | **值对象，构造即校验**格式 / 长度 / 非保留名 —— 三项全在构造器内，无旁路 | `ReservedNames` |
| `VariableSet` | domain | **聚合根接口**：变量的增删查改（身份 = `VariableName`） | `VariableName`、`Variable` |
| `CalculationHistory` | domain | **聚合根接口**：追加（含容量淘汰）、按 id 查、分页、清空 | `Calculation`、`PageResult` |
| `CalculationUseCase` | application | 编排：`ExpressionText` → 解析 → 求值 → 落历史；角度单位缺省；请求级变量名翻译为 `VariableName` | domain |
| `VariableUseCase` | application | 编排变量读写；原始名翻译为 `VariableName` | domain |
| `HistoryUseCase` | application | 历史分页参数校验与查询 | domain |
| `InMemoryVariableSet` | infrastructure | `ConcurrentHashMap` 实现 | `VariableSet` |
| `InMemoryCalculationHistory` | infrastructure | `ArrayDeque` + 读写锁实现 | `CalculationHistory` |

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

所有提升逻辑集中在 `Numbers` 领域服务，不在求值器里散落：

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

**这条规则由类型系统落实，而非由调用纪律落实**（见 §4.0）：禁用集合是值对象 `ReservedNames`，用户变量名必须构造为 `VariableName` 才能进入任何 API，而 `VariableName` 的构造器**本身就拒绝保留名**。存储写入与请求级变量两条入口，最终都只能拿到 `Map<VariableName, CalcNumber>`。因此不存在"某个入口忘了校验"的可能——校验不在入口处，在类型里。

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

实现方式：业务异常为领域层的 `CalcException(code, message, position)`——**刻意不携带 HTTP 状态码**，因为传输协议是接口层的关切，领域异常不该知道 HTTP 的存在。`interfaces/error/ErrorStatusMapper` 集中承担 `CalcErrorCode → HttpStatus` 的映射，由 `GlobalExceptionHandler`（`@RestControllerAdvice`）转换为上述响应体，**不依赖 Spring 默认错误页**（`server.error.whitelabel.enabled=false`）。

映射表拆成两处会漂移，因此由 `ErrorStatusMapperTest` 断言**每个 `CalcErrorCode` 都有映射**——新增错误码却忘了给状态时，测试直接失败，而不是等到运行时才以 500 的形式暴露。

## 8. 并发与存储

两个聚合根的内存实现按各自的访问模式选择并发策略：

| 聚合根（内存实现） | 数据结构 | 并发策略 | 理由 |
|---|---|---|---|
| `VariableSet`（`InMemoryVariableSet`） | `ConcurrentHashMap<VariableName, Variable>` | 无锁读，`compute` 写入 | 读多写少；单键操作天然原子，无需额外锁 |
| `CalculationHistory`（`InMemoryCalculationHistory`） | `ArrayDeque<Calculation>` | `ReentrantReadWriteLock` | 有**容量上限**，插入时必须原子地完成"追加 + 淘汰最旧"，`ConcurrentLinkedDeque` 无法保证精确边界；读操作占比高，读锁可并发 |

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

| 测试类 | 层 | 覆盖内容 |
|---|---|---|
| `LexerTest` | domain | 分词正确性、位置信息、非法字符报错、数字字面量各形态 |
| `ExpressionParserTest` | domain | AST 结构断言；**每个语法错误的 `position` 断言**；优先级与结合性（§6.3 全部三条） |
| `ExpressionEvaluatorTest` | domain | 四则、优先级、一元正负号、阶乘边界（0!、170!、171! 报错）、全部函数、全部定义域错误 |
| `PrecisionTest` | domain | `0.1+0.2` 精确等于 `0.3`；`1/3` 的 DECIMAL128 行为；Decimal/Floating 提升规则 |
| `FunctionRegistryTest` | domain | 注册表完整性、未知名处理、一元/二元元数校验 |
| `OperatorTableTest` | domain | `precedence` 数值与 §7.2 清单一致（防止清单与实现漂移） |
| `VariableNameTest` | domain | **`VariableName` 值对象构造即校验**：函数名、保留常量名、非法格式、超长名一律构造失败 |
| `InMemoryCalculationHistoryTest` | infrastructure | 容量淘汰边界（FIFO 顺序）、并发写、分页 |
| `InMemoryVariableSetTest` | infrastructure | 覆盖写、并发读写、删除 |
| `CalculationUseCaseTest` | application | 历史落库、请求级变量覆盖存储中的用户变量、**请求级变量使用保留名被拒（400）**、角度单位缺省与覆盖 |
| `ErrorStatusMapperTest` | interfaces | **每个 `CalcErrorCode` 都有 HTTP 状态映射**（防止新增错误码漏映射） |
| `*ControllerTest` | interfaces | `MockMvcBuilders.standaloneSetup` + 显式挂载 `GlobalExceptionHandler`，断言每个错误码与响应体结构 |
| `CalculatorEndToEndTest` | 全链路 | `@SpringBootTest(RANDOM_PORT)`：算 → 查历史 → 定义变量 → 用变量再算 |

**关键测试点**：

- **精度测试**是本设计数值模型的存在理由，必须精确断言（`assertEquals` 而非 delta 比较）
- **`OperatorTableTest` 断言清单与实现一致**：`/functions` 返回的优先级表是手写还是从 `OperatorTable` 生成，这条测试决定了两者不会漂移。**实现上直接由 `OperatorTable` 生成该清单**，测试只做兜底
- **并发测试**：`CountDownLatch` + 多线程（8 线程 × 1000 次）验证聚合根实现无丢失更新，断言最终状态精确
- **控制器测试用 `standaloneSetup` 而非 `@WebMvcTest`**：`@WebMvcTest` 会拉起 Spring 切片上下文，而本项目的控制器行为几乎不依赖容器特性（无过滤器、无拦截器、无安全）。`standaloneSetup` 更快，且能**显式挂载 `GlobalExceptionHandler`**——错误码路径因此被真实覆盖，而不是依赖"切片扫描恰好扫到了它"
- **`ErrorStatusMapperTest` 遍历 `CalcErrorCode.values()` 断言每个值都有映射**：这条测试是防漂移的守门人。映射表若与枚举分处两地且无人看守，新增错误码会静默退化成 500

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

D1–D9 出自 v1→v2 评审（收敛功能边界）；D10–D13 出自 v2→v3 评审（改换架构风格）。D12 记录的是**否决**——即"决定不引入某模式"，与 D1–D6 同类。

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
| **D10** | v2 的分层是 `core / api / service / store / config` | **改为 DDD 四层** `domain / application / infrastructure / interfaces` | 原分层是**技术分层**：`core` 是"纯计算"，`store` 是"存储"，`service` 是"服务"——按代码的技术角色划分。DDD 四层按**依赖方向与职责边界**划分，`domain` 承载全部业务规则、`application` 只做编排。真正的收益有三处（见 D11–D13），其余是改名，此表中如实区分 |
| **D11** | 聚合根（`VariableSet`）与仓储（`VariableRepository`）是否分成两个类型 | **合并**——领域层声明聚合根接口，基础设施层提供内存实现 | 教科书式分离的前提是**存在持久化**：加载要查库、保存要写库，是真实的、可能失败的操作，值得单独抽象并承载事务语义。本系统纯内存，聚合根本身就是存储，再加一层仓储只是把调用原样转发一遍。**为不存在的复杂度预留抽象不是设计，是负债** |
| **D12** | 是否引入领域事件（如 `CalculationPerformed`）+ 事件发布器 | **不引入** | 领域事件的价值在于**跨聚合、跨上下文的最终一致性**。本设计只有一个限界上下文、只有一个聚合需要在计算后被写入，且该写入是同步的、属于同一个用例——用事件解耦等于把一个方法调用拆成"发布 + 监听"两跳，只增加间接层。**在文档里说明为什么不引入，比引入了更值钱**；同理不做规约模式、工厂类、CQRS |
| **D13** | `VariableName` 的保留名校验放在哪 | **全部放进值对象的规范构造器**，不引入领域服务 | 采纳过程中曾被否决的中间方案：既然保留名依赖函数表，而值对象构造器"拿不到外部上下文"，那就加一个领域服务 `VariableNames`（持有 `ReservedNames`）来产出 `VariableName`。**此方案被推翻**——函数集（`UnaryFunction`/`BinaryFunction`）与常量集（`MathematicalConstant`）都是编译期固定的枚举，保留名集合静态可求，压根不是"运行时上下文"。既然构造器能独立完成格式、长度、保留名三项校验，那层领域服务就是多余的间接层。相比 v2 的 `VariableService.validateVariableName()`（调用方需从另一个服务上调它，漏调即失效），这里校验在类型里，没有旁路 |

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
