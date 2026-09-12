# scientific-calculator

科学计算器 HTTP 服务：**提交一个表达式，拿回一个结果**。打包为可独立运行的 jar，`java -jar` 直接启动，不依赖任何中间件（无数据库、无缓存、无外部接口调用），全部状态在内存中。

本期是**MVP**：只交付 3 个端点（求值、健康检查、能力清单）。被有意裁剪掉的部分（变量存储、计算历史）见 [`docs/mvp-and-roadmap.md`](docs/mvp-and-roadmap.md)。

---

## 技术栈

| 项 | 取值 |
|---|---|
| 语言 | Java 17（`pom.xml` 的 `java.version`） |
| 框架 | Spring Boot 3.5.x（parent `spring-boot-starter-parent:3.5.16`，`pom.xml:8`） |
| 构建 | Maven。**仓库里没有 `mvnw`**，直接用系统的 `mvn` |
| 测试 | JUnit 5 + AssertJ（由 `spring-boot-starter-test` 提供） |
| 第三方依赖 | **零**。依赖只有 `spring-boot-starter-web` 与 `spring-boot-starter-test` 两项（`pom.xml:35,40`），不引入数据库、缓存、工具库（Lombok / Guava / Commons 一律不用） |

---

## `POST /api/v1/calculator/calculate` —— 完整能力说明

这是本服务的核心接口。下面把「接受什么表达式、算到什么范围」逐条写清楚。

> **本节的符号表、函数表、常量表、数值边界表是快照，权威来源是 `GET /api/v1/calculator/functions`** ——
> 那份清单由源码生成（`OperatorTable` 与领域枚举），不会滞后于代码；本节的表格是人手抄的，
> 没有任何测试能守住它与代码的一致性。两者不一致时，以 `/functions` 的输出为准。

### 请求体字段

| 字段 | 必填 | 说明 |
|---|---|---|
| `expression` | **是** | 表达式原文。去首尾空白后不得为空；长度上限见「数值计算范围」。缺字段 / 非字符串 → 400 `INVALID_REQUEST` |
| `angleUnit` | 否 | `DEGREE` 或 `RADIAN`。缺省取服务端配置 `calculator.default-angle-unit`（默认 `DEGREE`）。只影响 `sin` `cos` `tan` `asin` `acos` `atan` `atan2` 七个函数 |
| `variables` | 否 | 请求级临时变量，**只对本次求值生效、不落库**。键**不做标识符格式校验** —— 非标识符的键会被接受，但永远取不到（求值器只在表达式里按标识符查找，`a-b` 这种键不可能被命中）；键**落在禁用集合**（23 个一元函数名 ∪ 5 个二元函数名 ∪ `pi` `e`）内则 400 `INVALID_REQUEST`；值为 `null` 同样拒收 |

响应 `200`：

```json
{ "expression": "1 + 2 * sin(30) ^ 2", "result": 1.5, "resultType": "FLOATING", "angleUnit": "DEGREE" }
```

- `resultType ∈ {DECIMAL, FLOATING}`，标明本次结果落在哪条数值路径上（见「数值计算范围」）。
- `angleUnit` 回显本次求值实际生效的角度单位；表达式**没有用到**角度敏感函数时为 `null`
  （**字段在场、值为 JSON null**，不省略字段）。这与错误响应的形态刻意不同，见「错误响应形态」。
- 本期响应**没有** `historyId` 与 `elapsedMs`：这两个字段的读者是历史记录，而历史属 Phase 2
  （spec §7.1 的 200 样例给出的是完整形态）。

### 算符表（9 条）

优先级数值**越大结合越紧**。位置（Fixity）为 `INFIX` / `PREFIX` / `POSTFIX`；结合性只对中缀有意义。

| 符号 | 位置 | 优先级 | 结合性 | 出处 |
|---|---|---|---|---|
| `+` | 中缀 | 1 | 左 | `domain/model/expression/OperatorTable.java:25` |
| `-` | 中缀 | 1 | 左 | `OperatorTable.java:26` |
| `*` | 中缀 | 2 | 左 | `OperatorTable.java:27` |
| `/` | 中缀 | 2 | 左 | `OperatorTable.java:28` |
| `%` | 中缀 | 2 | 左 | `OperatorTable.java:29` |
| `^` | 中缀 | 4 | **右** | `OperatorTable.java:30` |
| `+` | 前缀（一元正号） | 3 | — | `OperatorTable.java:31` |
| `-` | 前缀（一元负号） | 3 | — | `OperatorTable.java:32` |
| `!` | 后缀（阶乘） | 5 | — | `OperatorTable.java:33` |

`+` 与 `-` 各出现两次，分别对应中缀与前置两种语法角色 —— 这是同一符号的两种用法，如实分列。

**三条必须记住的优先级规则**（均有用例钉住）：

- `-2^2 = -4`（一元负号 3 级，**低于** `^` 的 4 级，故先算幂再取负）
- `2^3^2 = 512`（`^` **右结合**，等于 `2^(3^2)`）
- `3! + 1 = 7`（后缀 `!` 5 级最高）

这条表是 `OperatorTable` 的如实转录；解析器与 `/functions` 清单读的是同一份数据，因此三者不会各说各话。

### 一元函数表（23 个）

「定义域」与「说明」两列的文案**逐字取自领域枚举**（`UnaryFunction.java` 的 `Domain` 枚举 `:80-88`
与各枚举常量的 `description`），与 `/functions` 接口输出的字符串完全一致。

| 名字 | 定义域 | 受 `angleUnit` 影响 | 说明 | 出处 |
|---|---|---|---|---|
| `sin` | 任意实数 | 是 | 正弦。入参按 angleUnit 解释为角度或弧度，返回比值 | `UnaryFunction.java:24` |
| `cos` | 任意实数 | 是 | 余弦。入参按 angleUnit 解释为角度或弧度，返回比值 | `UnaryFunction.java:27` |
| `tan` | 任意实数 | 是 | 正切。入参按 angleUnit 解释为角度或弧度，返回比值 | `UnaryFunction.java:28` |
| `asin` | 闭区间 [-1, 1] | 是 | 反正弦。入参是比值不换算，返回值按 angleUnit 换算为角度或弧度 | `UnaryFunction.java:30` |
| `acos` | 闭区间 [-1, 1] | 是 | 反余弦。入参是比值不换算，返回值按 angleUnit 换算为角度或弧度 | `UnaryFunction.java:32` |
| `atan` | 任意实数 | 是 | 反正切。入参是比值不换算，返回值按 angleUnit 换算为角度或弧度 | `UnaryFunction.java:34` |
| `sinh` | 任意实数 | 否 | 双曲正弦。与角度单位无关 | `UnaryFunction.java:38` |
| `cosh` | 任意实数 | 否 | 双曲余弦。与角度单位无关 | `UnaryFunction.java:39` |
| `tanh` | 任意实数 | 否 | 双曲正切。与角度单位无关 | `UnaryFunction.java:40` |
| `asinh` | 任意实数 | 否 | 反双曲正弦。用带符号的恒等式计算，避免大负数下的相减抵消 | `UnaryFunction.java:45` |
| `acosh` | x ≥ 1 | 否 | 反双曲余弦 | `UnaryFunction.java:48` |
| `atanh` | 开区间 (-1, 1) | 否 | 反双曲正切 | `UnaryFunction.java:50` |
| `sqrt` | 非负实数（x ≥ 0） | 否 | 平方根 | `UnaryFunction.java:54` |
| `cbrt` | 任意实数 | 否 | 立方根，负数亦可（结果与入参同号） | `UnaryFunction.java:55` |
| `abs` | 任意实数 | 否 | 绝对值 | `UnaryFunction.java:58` |
| `exp` | 任意实数 | 否 | 自然指数 e 的 x 次幂 | `UnaryFunction.java:59` |
| `ln` | 正实数（x > 0） | 否 | 自然对数 | `UnaryFunction.java:60` |
| `log10` | 正实数（x > 0） | 否 | 常用对数，以 10 为底 | `UnaryFunction.java:61` |
| `log2` | 正实数（x > 0） | 否 | 以 2 为底的对数 | `UnaryFunction.java:62` |
| `floor` | 任意实数 | 否 | 向下取整到最近的整数 | `UnaryFunction.java:65` |
| `ceil` | 任意实数 | 否 | 向上取整到最近的整数 | `UnaryFunction.java:66` |
| `round` | \|x\| < 2^63，超出则舍入会饱和到长整型上界 | 否 | 四舍五入到最近的整数，.5 向上取整 | `UnaryFunction.java:67` |
| `sign` | 任意实数 | 否 | 符号函数，返回 -1、0 或 1 | `UnaryFunction.java:69` |

两点容易踩的语义：

- **反三角函数的入参不换算、返回值才换算**。`asin` / `acos` / `atan` 的入参是**比值**（无量纲），
  只有返回值按 `angleUnit` 换算（`UnaryFunction.java:150-152`）。正三角（`sin` / `cos` / `tan`）反过来：
  入参是角度要换算，返回值是比值。
- **违反定义域一律拒收，不静默返回 NaN**：抛 `DOMAIN_ERROR` → HTTP 422（`UnaryFunction.java:167-181`）。

调用形式为 `名字(参数)`，参数个数不符 → 400 `INVALID_REQUEST`，函数名未注册 → 400 `UNKNOWN_FUNCTION`。

### 二元函数表（5 个）

只收录**无法用中缀运算符表达**的运算 —— 幂与取余已有 `^` 与 `%` 两种中缀写法，因此不注册 `pow` / `mod`
函数形式（`BinaryFunction.java:13-14`）。

| 名字 | 定义域 | 受 `angleUnit` 影响 | 说明 | 出处 |
|---|---|---|---|---|
| `hypot` | 任意实数 | 否 | 直角三角形斜边 sqrt(x²+y²)，无中间溢出 | `BinaryFunction.java:23` |
| `max` | 任意实数 | 否 | 两个参数中的较大值 | `BinaryFunction.java:25` |
| `min` | 任意实数 | 否 | 两个参数中的较小值 | `BinaryFunction.java:26` |
| `atan2` | 任意实数 | **是** | atan2(y, x)：按点 (x, y) 所在象限返回角度。两个入参是比值不换算，返回值按 angleUnit 换算 | `BinaryFunction.java:27` |
| `log` | 真数 > 0，且底数 > 0 且底数 ≠ 1 | 否 | 对数 log(真数, 底数) | `BinaryFunction.java:30` |

### 常量表（2 个）

| 名字 | 值 | 说明 | 出处 |
|---|---|---|---|
| `pi` | `Math.PI`（`3.141592653589793`） | 圆周率 | `domain/MathematicalConstant.java:22` |
| `e` | `Math.E`（`2.718281828459045`） | 自然对数的底 | `MathematicalConstant.java:23` |

**符号一律小写**（`MathematicalConstant.symbol()`）。常量值以 `double` 全精度参与计算，**不做** 15 位规整 ——
规整只作用于「算出来的」数（见下节）。表达式中标识符的解析顺序是**常量优先**：先查 `pi` / `e`，
未命中才查请求级变量（`ExpressionEvaluator.java:73-88`）。

### 数值计算范围

**这一节是数值能力的对外承诺，也是「哪些输入不必完美处理」的边界。** 范围之内保证算得对；
范围之外一律**拒收**（抛领域异常 → 4xx），不保证给出哪个错误码，也不承诺优雅降级。
这份契约的完整论证见 spec §5.3。

#### 两条数值路径

| 路径 | 载体 | 覆盖的运算 | 精度 |
|---|---|---|---|
| **精确路径** | `BigDecimal`（`DecimalNumber`） | 十进制字面量、`+` `-` `*`、能整除的 `/`、`%`、非负整数指数的 `^`、阶乘 `!`、取负 | 精确无误差 |
| **浮点路径** | `double`（`FloatingNumber`） | 全部函数（`sin` `log` `exp` …）、除不尽的除法、负指数与分数指数的 `^` | IEEE-754 double，约 15~17 位有效数字 |

响应里的 `resultType` 字段标明本次结果落在哪条路径上：`DECIMAL` = 精确路径，`FLOATING` = 浮点路径
（`interfaces/dto/CalculateResponse.java:25-31`）。

**保底不变量**：结果只要落在 `DECIMAL` 就永远精确；一旦沾上浮点即存在浮点误差。任一侧为浮点数时整体提升为浮点数
（`domain/model/number/Numbers.java:190-192`）。

**规整的边界是「算出来的数」与「写进来的数」之间**：只有**算出来的** double 结果按 15 位有效数字规整
（`Numbers.NORMALIZE_CONTEXT`，`Numbers.java:38`；收口在 `Numbers.floatingFinite`，`Numbers.java:182-188`）——
因为 `sin(30°)` 的原始值是 `0.49999999999999994`，15 位规整才得到数学真值 `0.5`。
而**字面量与内置常量不规整**（字面量直接走 `new DecimalNumber(new BigDecimal(词素))`，
`parse/ExpressionParser.java:151-161`；常量直接构造 `FloatingNumber`，`MathematicalConstant.java:45-47`）——
π 的全精度 double 比它的 15 位规整值更接近真值，规整没有意义。

#### 支持范围（范围内保证正确）

| 维度 | 上界 | 出处 |
|---|---|---|
| 精确十进制数的 scale | `\|scale\| ≤ 100000` | `DecimalNumber.java:20` 的 `MAX_SCALE_MAGNITUDE`（构造器强制点 `:22-30`） |
| 精确幂的未缩放位数 | ≤ 100000 位 | `Numbers.java:33` 的 `MAX_EXACT_DIGITS`（超出即降级到 `double`，`:128-139`） |
| 阶乘自变量 | 非负整数且 ≤ 170 | `Numbers.java:19` 的 `FACTORIAL_LIMIT`（判定 `:152-166`；171! 超出 double 范围） |
| 浮点结果 | 有限，且规整到 15 位有效数字 | `Numbers.java:38` 的 `NORMALIZE_CONTEXT` |
| 除法结果 | 能整除则精确；除不尽按配置精度（默认 **34** 位有效数字，`HALF_EVEN`） | `Numbers.java:46` 构造 `MathContext(divisionPrecision, HALF_EVEN)`；默认值来自 `application.yaml` 的 `calculator.division-precision: 34` |
| 表达式长度 | 默认 **1000**，超限 → 400 `INVALID_REQUEST` | `application.yaml` 的 `calculator.max-expression-length`；强制点 `application/CalculationUseCase.java:65-73` |
| 反双曲函数（`asinh` / `acosh` / `atanh`）的自变量 | `\|x\| ≤ √Double.MAX_VALUE`（≈ `1.34e154`） | spec §5.3.1；实现见 `UnaryFunction.java:45-51` 的手写恒等式 |
| `round` 的自变量 | `\|x\| < 2^63`（`9.223372036854776E18`） | `UnaryFunction.java:175`（`checkDomain` 的 `LONG_RANGE` 分支） |

关于反双曲那一行的来由：JDK 17 的 `java.lang.Math` **没有** `asinh` / `acosh` / `atanh`
（它们到 JDK 20 才加入），因此这三个函数按数学恒等式手写，恒等式里的 `x²` 中间量会先溢出，
上界由此而来（`UnaryFunction.java:41-43` 的注释与 `:45-51` 的手写恒等式）。`asinh` 用的是带符号形式而非 `log(x + √(x²+1))`，
后者在 x 为大负数时是两个大数相减、有效位被抵消光。三者中 `atanh` 自身的定义域是开区间 `(-1, 1)`，
比这条上界更紧。

#### 范围之外的行为

- 抛 `CalcException`，由 `interfaces/error/ErrorStatusMapper` 映射为 **HTTP 400 / 422** ——
  **不是 500，也不是静默的 NaN / Infinity**（spec §5.3.2；非有限值拒收见 `Numbers.requireFinite`，
  `Numbers.java:176-181`）。
- 精确幂超出位数预算时**降级**到 `double`（属范围内行为，不是边界修补），再由非有限值检查兜底。
- 未枚举的其它极端输入只需给出任一 `CalcException`，不要求逐一穷举、不要求专门的错误语义。

调用方的建议：**普通十进制的加减乘除与整数幂精确无误差；超越函数约 15 位有效数字；
除不尽时至多 34 位有效数字；数值量级不超出 IEEE-754 double（约 `1.8e±308`）。**

---

## `GET /health` 与 `GET /api/v1/calculator/functions`

### 健康检查 `GET /health`

**不带 `/api/v1` 前缀**。响应：

```json
{ "status": "UP", "uptimeMs": 12345 }
```

`status` 固定为 `UP`（能构造出响应即说明上下文已就绪）；`uptimeMs` 是**进程启动至今**的毫秒数
（`interfaces/dto/HealthResponse.java:9`，实现在 `interfaces/MetaController.java:16-21`）。

### 能力清单 `GET /api/v1/calculator/functions`

一次返回本服务可用的接口、函数说明与能力边界。客户端据此可自建输入校验或表达式预览，
不必把语法硬编码在调用方。返回字段（`application/CapabilityManifest.java:15-24`）：

| 字段 | 内容 |
|---|---|
| `endpoints` | 接口清单，每项含 `method` / `path` / `description` |
| `constants` | 内置常量：`name` / `value` / `description`（按枚举声明顺序） |
| `unaryFunctions` | 一元函数：`name` / `arity` / `angleSensitive` / `domain` / `description`（按枚举声明顺序，全部 23 个） |
| `binaryFunctions` | 二元函数：同上字段（按枚举声明顺序，全部 5 个） |
| `operators` | 算符：`symbol` / `fixity` / `precedence` / `associativity`（按 `OperatorTable` 顺序） |
| `angleUnits` | `["DEGREE", "RADIAN"]` |
| `defaultAngleUnit` | 服务端缺省角度单位 |
| `limits` | 能力边界：`maxExpressionLength` / `divisionPrecision` / `maxExactPowerDigits` / `maxDecimalScaleMagnitude` |

**清单由源码生成，不从文档抄写**：`operators` 直接取 `OperatorTable.all()`，函数与常量清单由领域枚举
（`UnaryFunction` / `BinaryFunction` / `MathematicalConstant`）生成，定义域与说明文案住在枚举里，
`limits` 取 `CalculationPolicy` 与代码里的真实常量（`application/CapabilityQuery.java:37-71`）。
**唯一手写项是 `endpoints`** —— HTTP 路由只有接口层知道，由接口层以常量提供
（`interfaces/CapabilityController.java:33-36`）；它是手写的，因此有漂移风险，
真正的防线是 `CapabilityEndToEndTest.manifestEndpointsMatchTheActuallyRegisteredRoutes`
从 `RequestMappingHandlerMapping` 取**真实注册路由**与它对账。顺序是对外契约：不排序、不去重。

---

## 构建与启动

**以下命令由本人执行**（本次交付只跑到 `mvn -o test`，打包与启动属验收步骤，未代为执行）：

```bash
# 构建（离线可用；仓库里没有 mvnw，用系统 mvn）
mvn -o clean package

# 启动（前台运行，Ctrl-C 停止；端口 8080，可用 --server.port=xxxx 改）
java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar

# 求值
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
     -H 'Content-Type: application/json' -d '{"expression":"1 + 2*sin(30)^2"}'

# 健康检查
curl -s localhost:8080/health

# 能力清单
curl -s localhost:8080/api/v1/calculator/functions
```

jar 文件名由 `pom.xml` 的 `artifactId`（`scientific-calculator`，`pom.xml:12`）与 `version`
（`0.0.1-SNAPSHOT`，`pom.xml:13`）决定，即 `target/scientific-calculator-0.0.1-SNAPSHOT.jar`。

---

## 表达式语法速查

| 成分 | 形态 |
|---|---|
| 数字字面量 | `123`、`1.5`、`.5`、`1e-3`、`1.5E+10`。字面量按十进制**精确**解析，不走 double |
| 标识符 | `[A-Za-z_][A-Za-z0-9_]*`，用于函数名与常量名（`pi` / `e`）。这是**表达式词法**的规则（决定 token 怎么切），请求级变量名**不受**它约束 |
| 括号 | `( )`，可任意嵌套 |
| 算符 | `+ - * / % ^`（中缀）、前缀 `+` `-`、后缀 `!`（阶乘） |
| 函数调用 | `名字(参数)`；二元函数写成 `名字(a, b)`，参数间用 `,` 分隔，如 `log(8, 2)`、`atan2(1, 1)` |
| 优先级 | 见上表。`!`(5) > `^`(4) > 前缀 `+`/`-`(3) > `*` `/` `%`(2) > `+` `-`(1) |

三条必记的例子：`-2^2 = -4`、`2^3^2 = 512`、`3! + 1 = 7`。

阶乘**只有后缀 `!` 一种写法**（`3!` 而不是 `fact(3)`）：凡有中缀 / 后缀写法的运算一律不注册函数形式，
避免同一能力出现两种拼写（spec §6.5、决策 D3）。同理没有 `pow` / `mod`。

---

## 错误响应形态与错误码

所有错误走同一个响应体结构：

```json
{ "code": "PARSE_ERROR", "message": "第 7 个字符处缺少右括号", "position": 7,
  "timestamp": "2026-09-12T12:00:00Z", "path": "/api/v1/calculator/calculate" }
```

`position` 仅在**与位置相关的错误**里出现（语法错误，0 基字符下标），其余错误该字段**整个不出现**
（`interfaces/error/ErrorResponse.java` 的类级 `@JsonInclude(NON_NULL)`）。

> **两处 null 形态不对称，这是有意的**：成功响应的 `angleUnit` 为 `null` 时**字段在场且值为 JSON null**
> （`CalculateResponse` 上**没有** `@JsonInclude`），错误响应里为 `null` 的字段则**整个不出现**
> （`ErrorResponse` 上有类级 `@JsonInclude(NON_NULL)`，理由是避免调用方把 `position: null` 误读为 0）。
> 两个契约各自有意，不要「统一」：成功响应里保留字段比省略更能表达「本次求值不涉及角度」；
> 错误响应里省略字段正满足 spec §7.6「`position` 仅在与位置相关的错误中出现」的规定。

错误码共 **12** 个（`domain/error/CalcErrorCode.java:10-21`），到 HTTP 状态的映射集中在一处
（`interfaces/error/ErrorStatusMapper.java:20-29`）：

| HTTP | 错误码 | 触发条件 | 本期是否会触发 |
|---|---|---|---|
| 400 | `PARSE_ERROR` | 语法错误（响应含 `position`） | 会 |
| 400 | `INVALID_REQUEST` | 字段缺失 / 类型不符 / 越界 / 格式非法 / 变量名落入禁用集合 / 表达式超长 | 会 |
| 400 | `UNKNOWN_FUNCTION` | 调用了未注册的函数名 | 会 |
| 404 | `VARIABLE_NOT_FOUND` | 查询或删除不存在的变量 | **不会**（没有变量存储） |
| 404 | `HISTORY_NOT_FOUND` | 查询不存在的历史记录 | **不会**（没有计算历史） |
| 404 | `NO_HANDLER` | 路径不存在 | 会 |
| 405 | `METHOD_NOT_ALLOWED` | 方法不匹配（如 `GET` 打求值端点） | 会 |
| 422 | `UNKNOWN_VARIABLE` | 表达式引用了未定义的变量（如 `y+1`） | 会 |
| 422 | `DIVISION_BY_ZERO` | 除零、模零 | 会 |
| 422 | `DOMAIN_ERROR` | 违反函数定义域（如 `sqrt(-1)`） | 会 |
| 422 | `NON_FINITE_RESULT` | 结果溢出为 Inf / NaN、阶乘超界 | 会 |
| 500 | `INTERNAL_ERROR` | 兜底，不外泄堆栈信息 | 会（见下） |

关于那 500：它是兜底分支。**在出厂配置下即可由客户端错误到达** —— `POST /api/v1/calculator/calculate`
带非 JSON 的 `Content-Type`（如 `text/plain`）时，消息转换器在进入 handler 之前就抛
`HttpMediaTypeNotSupportedException`，而全局异常处理器没有它的分支，于是落到兜底返回
500 `INTERNAL_ERROR`（响应体仍是统一结构）。**这是本期的已知取舍**：spec 的错误码表里没有 415，
而映射是穷尽 `switch`、`INVALID_REQUEST` 固定映 400，想要 415 就得新增错误码（属擅自扩契约）。
另有一处已知边界：`Accept: application/xml` 会得到 **406 + 空响应体** —— 兜底 handler 被调用了一次，
但它产出的 JSON 在「客户端只接受 XML」的前提下渲染不出来。spec 未定义 406，如实记为已知边界，不是缺陷。

---

## 已知取舍与后续建设

| 取舍 | 影响 | 出处 / 何时重新评估 |
|---|---|---|
| **没有变量存储** | `variables` 只是**请求级临时量**，不能跨请求参与计算，也没有 `PUT /variables/{name}` 之类的接口。保留名校验本身**已经做了**（键落在禁用集合内 → 400 `INVALID_REQUEST`），缺的只是存储 | Phase 2 第一优先，因为它影响接口形态。见 [`docs/mvp-and-roadmap.md`](docs/mvp-and-roadmap.md) §二.1 |
| **没有计算历史** | 每次求值不落库、不留痕；响应里因此没有 `historyId` / `elapsedMs`；`VARIABLE_NOT_FOUND` / `HISTORY_NOT_FOUND` 两个错误码在 MVP 中不会被触发（词汇表保持完整是刻意的，避免 Phase 2 改动接口契约） | Phase 2，与变量并列。见 roadmap §二.2、§五 |
| `calculator.history-capacity` 配置项**本期无消费者** | 该键能绑定、能校验，但没有任何代码读它（历史属 Phase 2）。不要把它当成「已生效的容量上限」 | Phase 2 建历史聚合根时消费 |
| **README 的能力表是手抄快照** | 本节的符号 / 函数 / 常量 / 边界表是人手抄的，没有测试能守住它与代码的一致性，可能滞后于实现 | 任何时候以 `GET /api/v1/calculator/functions` 为准；改代码后若表格没跟上，以接口输出为准 |
| **静态资源映射被显式关闭**（`spring.web.resources.add-mappings: false`） | 这是未匹配路径返回统一 404 + `NO_HANDLER` 的**唯一承重配置**：撤掉它，未匹配路径会被资源处理器接走、抛 `NoResourceFoundException`，最终由兜底分支返回 **500** | **升级 Spring Boot 时必须重测 404 路径**：`CalculatorEndToEndTest.unknownPathReturns404WithNoHandlerCode` 变红即为信号 |
| 单实例限制 | 内存实现意味着不支持多实例横向扩展与重启保留，这是「不引入任何中间件」的直接结果，非缺陷 | 若需求变成多实例 / 持久化，需重新做架构决策 |
| 无鉴权 | 题目未要求；加了就需要用户体系，超出范围 | — |

被有意砍掉、Phase 2 再补的完整清单（变量管理、计算历史、配置运行时可变、更深的测试与评审）
见 [`docs/mvp-and-roadmap.md`](docs/mvp-and-roadmap.md)。四份交付文档之间的关系：
[`docs/01-需求分析.md`](docs/01-需求分析.md)（要做什么、为什么这么划边界）、
[`docs/02-架构设计.md`](docs/02-架构设计.md)（怎么搭的、为什么这么搭）、
[`docs/03-AI协作记录.md`](docs/03-AI协作记录.md)（哪一步是 AI 产出、哪一步待本人校验）。
设计权威是 [`docs/superpowers/specs/2026-09-12-scientific-calculator-design.md`](docs/superpowers/specs/2026-09-12-scientific-calculator-design.md)。
