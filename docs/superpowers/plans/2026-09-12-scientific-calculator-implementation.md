# 科学计算器后端服务 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一套纯内存、零外部依赖的科学计算器 HTTP 后端服务，打包为 `java -jar` 可直接启动的 runnable jar。

**Architecture:** 四层单向依赖——`api → service → store`、`service → core`、`core → 无`。`core` 是零 Spring 依赖的纯计算内核（数值模型 → 词法 → 优先级爬升解析 → AST → 求值），因此可被最纯粹地单测。存储层为「接口 + InMemory 实现」，为未来替换持久化留出真实可行的接缝。

**Tech Stack:** Java 17、Spring Boot 3.5.x、Maven、JUnit 5（由 `spring-boot-starter-test` 提供）、Jackson（由 `spring-boot-starter-web` 传递引入）

**Spec:** `docs/superpowers/specs/2026-09-12-scientific-calculator-design.md`

## Global Constraints

以下为 spec 的项目级要求，**每个任务都隐含包含本节**：

- Java 版本：**17**（`<java.version>17</java.version>`）；可使用 record、sealed interface、switch 模式匹配
- Spring Boot parent：**3.5.x**（原脚手架 4.1.1 必须降级，见 spec §12 D9）
- **新增依赖只允许一个**：`spring-boot-starter-web`。**不得**引入 Lombok、Guava、Commons、actuator、validation starter 或任何其他第三方库
- **零外部调用**：不发起任何对外网络请求，不接入任何外部服务、数据库、缓存
- **`core` 包禁止 import 任何 `org.springframework.*`**
- HTTP 前缀：`/api/v1`
- 包根：`com.wysjwxm.calculator`
- 错误码取值（12 个，不可增删改名）：`PARSE_ERROR` `INVALID_REQUEST` `UNKNOWN_FUNCTION` `VARIABLE_NOT_FOUND` `HISTORY_NOT_FOUND` `NO_HANDLER` `METHOD_NOT_ALLOWED` `UNKNOWN_VARIABLE` `DIVISION_BY_ZERO` `DOMAIN_ERROR` `NON_FINITE_RESULT` `INTERNAL_ERROR`
- 算子优先级（**唯一事实来源是 `OperatorTable`**）：`+ -` = 1、`* / %` = 2、一元 `+ -` = 3、`^` = 4、`!` = 5；`^` 右结合，其余左结合
- 保留常量：`pi` = `3.141592653589793`、`e` = `2.718281828459045`
- 变量名禁用集合 = **函数名 ∪ 保留常量名**，在存储写入与请求级临时变量**两个入口统一生效**
- 一元函数 23 个：`sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh sqrt cbrt abs exp ln log10 log2 floor ceil round sign`
- 二元函数 5 个：`hypot max min atan2 log`
- 不提供 `pow`/`mod` 函数（用 `^`/`%` 运算符），不提供 `fact` 函数（用 `!` 后缀）
- 提交信息用中文，格式 `type: 描述`

---

## 文件结构

```
src/main/java/com/wysjwxm/calculator/
├── ScientificCalculatorApplication.java      [已存在，不改]
├── core/                                      ← 零 Spring 依赖
│   ├── AngleUnit.java                         DEGREE / RADIAN 枚举
│   ├── Constants.java                         pi / e 保留常量
│   ├── error/
│   │   ├── CalcErrorCode.java                 12 个错误码枚举（不含 HTTP 状态）
│   │   └── CalcException.java                 业务异常基类，携带 code + position
│   ├── number/
│   │   ├── CalcNumber.java                    sealed interface
│   │   ├── DecimalNumber.java
│   │   ├── FloatingNumber.java
│   │   └── Numbers.java                       算术 + 类型提升（实例类，持有 MathContext）
│   ├── lexer/
│   │   ├── TokenType.java
│   │   ├── Token.java
│   │   └── Lexer.java
│   ├── operator/
│   │   ├── Fixity.java                        INFIX / PREFIX / POSTFIX
│   │   ├── Associativity.java                 LEFT / RIGHT
│   │   ├── Operator.java
│   │   └── OperatorTable.java                 优先级唯一事实来源
│   ├── parser/
│   │   ├── ExpressionParser.java              优先级爬升
│   │   ├── ExpressionPrinter.java             AST → 中缀文本（供测试断言）
│   │   └── ast/
│   │       ├── Expression.java                sealed interface
│   │       ├── LiteralExpr.java
│   │       ├── VariableExpr.java
│   │       ├── UnaryExpr.java
│   │       ├── PostfixExpr.java
│   │       ├── BinaryExpr.java
│   │       └── CallExpr.java
│   ├── function/
│   │   ├── MathFunction.java                  接口：name / arity / angleSensitive
│   │   ├── UnaryFunction.java                 23 个一元函数枚举
│   │   ├── BinaryFunction.java                5 个二元函数枚举
│   │   └── FunctionRegistry.java              名字 → 实现，含禁用名集合
│   └── eval/
│       ├── EvaluationContext.java             变量查找接口
│       └── Evaluator.java                     AST → CalcNumber
├── store/
│   ├── VariableRecord.java
│   ├── HistoryRecord.java
│   ├── PageResult.java
│   ├── VariableStore.java                     接口
│   ├── CalculationHistoryStore.java           接口
│   ├── InMemoryVariableStore.java
│   └── InMemoryCalculationHistoryStore.java
├── service/
│   ├── VariableService.java                   含保留名校验（其他组件复用它）
│   ├── HistoryService.java
│   └── CalculationService.java
├── api/
│   ├── CalculatorController.java
│   ├── HistoryController.java
│   ├── VariableController.java
│   ├── MetaController.java
│   ├── dto/
│   │   ├── CalculateRequest.java
│   │   ├── CalculateResponse.java
│   │   ├── FunctionsResponse.java
│   │   ├── HistoryItemResponse.java
│   │   ├── HistoryPageResponse.java
│   │   ├── VariableResponse.java
│   │   ├── VariableListResponse.java
│   │   ├── PutVariableRequest.java
│   │   ├── ClearHistoryResponse.java
│   │   ├── HealthResponse.java
│   │   └── CalcNumberSerializer.java
│   └── error/
│       ├── ErrorResponse.java
│       ├── ErrorStatusMapper.java             CalcErrorCode → HttpStatus
│       └── GlobalExceptionHandler.java
└── config/
    └── CalculatorProperties.java
```

**依赖方向铁律**：`core` 不 import `store`、`service`、`api`、`config`、`org.springframework.*`。若某个任务发现 `core` 需要反向依赖，说明设计有问题，停下来报告。

---

## 任务总览

| # | 任务 | 产出 |
|---|---|---|
| 1 | 构建基线 | pom 降级、Web 依赖、配置类 |
| 2 | 错误类型词汇表 | `CalcErrorCode`、`CalcException` |
| 3 | 数值模型 | `CalcNumber` 家族、`Numbers` |
| 4 | 词法分析 | `Lexer` |
| 5 | 算子表 | `OperatorTable` |
| 6 | AST 与解析器 | `ExpressionParser` |
| 7 | 变量与函数注册表 | `Constants`、`FunctionRegistry` |
| 8 | 求值器 | `Evaluator` |
| 9 | 变量存储 | `InMemoryVariableStore` |
| 10 | 历史存储 | `InMemoryCalculationHistoryStore` |
| 11 | 服务层 | 三个 Service（含保留名校验） |
| 12 | 错误响应与全局异常处理 | `GlobalExceptionHandler` |
| 13 | HTTP 接口 | 4 个 Controller + DTO |
| 14 | 端到端测试 | 全链路验证 |
| 15 | 交付文档与打包 | README、三份文档、runnable jar |

---

### Task 1: 构建基线

把脚手架从 Spring Boot 4.1.1 降到 3.5.x，加入 Web 依赖，建立配置类。

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/java/com/wysjwxm/calculator/config/CalculatorProperties.java`
- Test: `src/test/java/com/wysjwxm/calculator/config/CalculatorPropertiesTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/ScientificCalculatorApplicationTests.java`（已存在，验证仍通过）

**Interfaces:**
- Consumes: 无（本任务是起点）
- Produces: `CalculatorProperties`，含四个访问器 `defaultAngleUnit()` → `AngleUnit`、`historyCapacity()` → `int`、`maxExpressionLength()` → `int`、`divisionPrecision()` → `int`，注册为 Spring Bean。Task 8/11/13 会注入它。

- [ ] **Step 1: 确认可用的 Spring Boot 3.5.x 版本**

运行：

```bash
curl -s "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml" \
  | grep -oE '<version>3\.5\.[0-9]+</version>' | tail -3
```

记下最新补丁版本号（下文以 `3.5.6` 为例）。**若该命令无输出或网络不可用，跳过此步，直接用 `3.5.6`；后续 `mvn` 命令若报版本不存在会自动暴露，届时换成实际存在的版本即可。**

- [ ] **Step 2: 修改 pom.xml**

把 `<parent>` 的 `<version>` 改为上一步确认的版本（如 `3.5.6`），并在 `<dependencies>` 中，把 `spring-boot-starter` 替换为 `spring-boot-starter-web`：

```xml
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.6</version>
        <relativePath/> <!-- lookup parent from repository -->
    </parent>
```

```xml
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

其余部分（`java.version`、`spring-boot-maven-plugin`）保持不变。**不要**添加任何其他依赖。

- [ ] **Step 3: 验证依赖解析成功**

运行：`mvn -q -DskipTests dependency:resolve`

预期：BUILD SUCCESS。若报 `Could not resolve dependencies` 且原因是 `spring-boot-starter-parent:3.5.6` 不存在，回到 Step 1 换成真实版本。

- [ ] **Step 4: 修改 application.yaml**

写入完整内容：

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
  default-angle-unit: DEGREE
  history-capacity: 1000
  max-expression-length: 1000
  division-precision: 34
```

- [ ] **Step 5: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/config/CalculatorPropertiesTest.java`：

```java
package com.wysjwxm.calculator.config;

import com.wysjwxm.calculator.core.AngleUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProps.class);

    @EnableConfigurationProperties(CalculatorProperties.class)
    static class EnableProps { }

    @Test
    void bindsDefaultsFromApplicationYaml() {
        runner.run(ctx -> {
            CalculatorProperties props = ctx.getBean(CalculatorProperties.class);
            assertThat(props.defaultAngleUnit()).isEqualTo(AngleUnit.DEGREE);
            assertThat(props.historyCapacity()).isEqualTo(1000);
            assertThat(props.maxExpressionLength()).isEqualTo(1000);
            assertThat(props.divisionPrecision()).isEqualTo(34);
        });
    }

    @Test
    void rejectsNonPositiveExpressionLength() {
        runner.withPropertyValues("calculator.max-expression-length=0")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsNonPositiveDivisionPrecision() {
        runner.withPropertyValues("calculator.division-precision=-1")
              .run(ctx -> assertThat(ctx).hasFailed());
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

运行：`mvn -q test -Dtest=CalculatorPropertiesTest`

预期：编译失败，`CalculatorProperties` / `AngleUnit` 不存在。

- [ ] **Step 7: 创建 AngleUnit**

创建 `src/main/java/com/wysjwxm/calculator/core/AngleUnit.java`：

```java
package com.wysjwxm.calculator.core;

/**
 * 三角函数的角度单位。仅对 sin/cos/tan/asin/acos/atan/atan2 生效，其余函数忽略。
 */
public enum AngleUnit {
    DEGREE,
    RADIAN
}
```

- [ ] **Step 8: 创建 CalculatorProperties**

创建 `src/main/java/com/wysjwxm/calculator/config/CalculatorProperties.java`：

```java
package com.wysjwxm.calculator.config;

import com.wysjwxm.calculator.core.AngleUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "calculator")
public record CalculatorProperties(
        @NotNull AngleUnit defaultAngleUnit,
        @Min(1) int maxExpressionLength,
        @Min(1) int divisionPrecision,
        int historyCapacity
) {
    /**
     * historyCapacity 允许为 0 或负数，语义为「不限制容量」，因此不加 @Min 约束。
     */
    public boolean historyUnbounded() {
        return historyCapacity <= 0;
    }
}
```

**注**：`jakarta.validation` 由 `spring-boot-starter-web` 传递引入（Tomcat/Spring 依赖链），无需额外声明。若 Step 9 编译报找不到 `jakarta.validation.constraints`，改为在 `CalculatorProperties` 中不加注解、改在 compact constructor 中手工校验并抛 `IllegalArgumentException`（效果相同，都是启动期快速失败）。

- [ ] **Step 9: 运行测试确认通过**

运行：`mvn -q test -Dtest=CalculatorPropertiesTest`

预期：3 个测试全部 PASS。

- [ ] **Step 10: 运行全部测试**

运行：`mvn -q test`

预期：BUILD SUCCESS，含原有的 `ScientificCalculatorApplicationTests.contextLoads`。

- [ ] **Step 11: 提交**

```bash
git add pom.xml src/main/resources/application.yaml \
        src/main/java/com/wysjwxm/calculator/config/CalculatorProperties.java \
        src/main/java/com/wysjwxm/calculator/core/AngleUnit.java \
        src/test/java/com/wysjwxm/calculator/config/CalculatorPropertiesTest.java
git commit -m "feat: 构建基线 — 降级至 Spring Boot 3.5.x、引入 web starter、新增配置类"
```

---

### Task 2: 错误类型词汇表

建立全项目统一的错误码与业务异常，**不含任何 HTTP 概念**（映射留给 Task 12）。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/error/CalcErrorCode.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/error/CalcException.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/error/CalcExceptionTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `CalcErrorCode` 枚举，12 个常量（见 Global Constraints）
  - `CalcException extends RuntimeException`，方法 `CalcErrorCode code()`、`Integer position()`（无位置时为 `null`）
  - 静态工厂 `CalcException.of(CalcErrorCode code, String message)` 与 `CalcException.at(CalcErrorCode code, String message, int position)`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/core/error/CalcExceptionTest.java`：

```java
package com.wysjwxm.calculator.core.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalcExceptionTest {

    @Test
    void ofCarriesCodeAndMessageAndNullPosition() {
        CalcException ex = CalcException.of(CalcErrorCode.DIVISION_BY_ZERO, "除数不能为零");
        assertThat(ex.code()).isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
        assertThat(ex.getMessage()).isEqualTo("除数不能为零");
        assertThat(ex.position()).isNull();
    }

    @Test
    void atCarriesPosition() {
        CalcException ex = CalcException.at(CalcErrorCode.PARSE_ERROR, "缺少右括号", 7);
        assertThat(ex.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
        assertThat(ex.position()).isEqualTo(7);
    }

    @Test
    void errorCodeVocabularyIsExactlyTwelve() {
        assertThat(CalcErrorCode.values()).hasSize(12);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=CalcExceptionTest`

预期：编译失败，`CalcErrorCode` 不存在。

- [ ] **Step 3: 创建 CalcErrorCode**

创建 `src/main/java/com/wysjwxm/calculator/core/error/CalcErrorCode.java`：

```java
package com.wysjwxm.calculator.core.error;

/**
 * 全项目统一的错误码词汇表。
 *
 * <p>刻意不携带 HTTP 状态码 —— 那属于传输层关切，映射由 api 层的
 * {@code ErrorStatusMapper} 负责。这样 core 包保持对传输协议无感知。
 */
public enum CalcErrorCode {
    PARSE_ERROR,
    INVALID_REQUEST,
    UNKNOWN_FUNCTION,
    VARIABLE_NOT_FOUND,
    HISTORY_NOT_FOUND,
    NO_HANDLER,
    METHOD_NOT_ALLOWED,
    UNKNOWN_VARIABLE,
    DIVISION_BY_ZERO,
    DOMAIN_ERROR,
    NON_FINITE_RESULT,
    INTERNAL_ERROR
}
```

- [ ] **Step 4: 创建 CalcException**

创建 `src/main/java/com/wysjwxm/calculator/core/error/CalcException.java`：

```java
package com.wysjwxm.calculator.core.error;

/**
 * 业务异常基类。携带错误码与可选的字符位置（仅语法类错误有位置）。
 */
public class CalcException extends RuntimeException {

    private final CalcErrorCode code;
    private final Integer position;

    private CalcException(CalcErrorCode code, String message, Integer position) {
        super(message);
        this.code = code;
        this.position = position;
    }

    public static CalcException of(CalcErrorCode code, String message) {
        return new CalcException(code, message, null);
    }

    public static CalcException at(CalcErrorCode code, String message, int position) {
        return new CalcException(code, message, position);
    }

    public CalcErrorCode code() {
        return code;
    }

    /** 字符位置（0 基），非位置相关错误返回 null。 */
    public Integer position() {
        return position;
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=CalcExceptionTest`

预期：3 个测试 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/error/ \
        src/test/java/com/wysjwxm/calculator/core/error/
git commit -m "feat: 新增错误码词汇表与业务异常基类"
```

---

### Task 3: 数值模型

实现混合数值策略：四则运算走 `BigDecimal` 保精确，超越函数走 `double`。**这是整个设计的核心，`0.1 + 0.2` 必须精确等于 `0.3`。**

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/number/CalcNumber.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/number/DecimalNumber.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/number/FloatingNumber.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/number/Numbers.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/number/NumbersTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/number/PrecisionTest.java`

**Interfaces:**
- Consumes: `CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `sealed interface CalcNumber permits DecimalNumber, FloatingNumber`，方法 `double toDouble()`、`BigDecimal toDecimal()`、`boolean isExact()`
  - `record DecimalNumber(BigDecimal value)`、`record FloatingNumber(double value)`
  - `Numbers` **实例类**（非静态工具类），构造器 `Numbers(int divisionPrecision)`，方法：
    - `CalcNumber of(long v)` / `CalcNumber of(BigDecimal v)` / `CalcNumber of(double v)`
    - `CalcNumber add(CalcNumber a, CalcNumber b)`
    - `CalcNumber subtract(CalcNumber a, CalcNumber b)`
    - `CalcNumber multiply(CalcNumber a, CalcNumber b)`
    - `CalcNumber divide(CalcNumber a, CalcNumber b)`
    - `CalcNumber modulo(CalcNumber a, CalcNumber b)`
    - `CalcNumber power(CalcNumber base, CalcNumber exponent)`
    - `CalcNumber negate(CalcNumber a)`
    - `CalcNumber factorial(CalcNumber a)`
    - `CalcNumber floating(double v)`
    - 静态 `void requireFinite(double v, String what)`

- [ ] **Step 1: 写失败测试 — 精度**

创建 `src/test/java/com/wysjwxm/calculator/core/number/PrecisionTest.java`：

```java
package com.wysjwxm.calculator.core.number;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本设计数值模型存在的理由：精确路径必须真的精确。
 * 这些断言使用 equals 而非 delta 比较 —— delta 比较就等于放弃了精度保证。
 */
class PrecisionTest {

    private final Numbers numbers = new Numbers(34);

    @Test
    void decimalAdditionIsExact() {
        // 若走 double，这里会得到 0.30000000000000004
        CalcNumber a = numbers.of(new BigDecimal("0.1"));
        CalcNumber b = numbers.of(new BigDecimal("0.2"));
        assertThat(numbers.add(a, b)).isEqualTo(numbers.of(new BigDecimal("0.3")));
    }

    @Test
    void decimalAdditionNeverDegradesToFloating() {
        CalcNumber result = numbers.add(
                numbers.of(new BigDecimal("0.1")),
                numbers.of(new BigDecimal("0.2")));
        assertThat(result).isInstanceOf(DecimalNumber.class);
        assertThat(result.isExact()).isTrue();
    }

    @Test
    void oneThirdUsesConfiguredPrecision() {
        CalcNumber result = numbers.divide(numbers.of(1L), numbers.of(3L));
        assertThat(result.toDecimal())
                .isEqualTo(new BigDecimal("0.3333333333333333333333333333333333"));
    }

    @Test
    void mixingWithFloatingDegradesToFloating() {
        CalcNumber result = numbers.add(numbers.of(new BigDecimal("0.1")), numbers.floating(0.2));
        assertThat(result).isInstanceOf(FloatingNumber.class);
        assertThat(result.isExact()).isFalse();
    }

    @Test
    void powerWithNonNegativeIntegerExponentStaysExact() {
        CalcNumber result = numbers.power(numbers.of(new BigDecimal("0.1")), numbers.of(3L));
        assertThat(result).isInstanceOf(DecimalNumber.class);
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("0.001"));
    }

    @Test
    void powerWithFractionalExponentGoesFloating() {
        CalcNumber result = numbers.power(numbers.of(2L), numbers.floating(0.5));
        assertThat(result).isInstanceOf(FloatingNumber.class);
        assertThat(result.toDouble()).isCloseTo(Math.sqrt(2), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> numbers.divide(numbers.of(1L), numbers.of(0L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    void moduloByZeroThrows() {
        assertThatThrownBy(() -> numbers.modulo(numbers.of(1L), numbers.of(0L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    void factorialOfNegativeThrowsDomainError() {
        assertThatThrownBy(() -> numbers.factorial(numbers.of(-1L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void factorialOfNonIntegerThrowsDomainError() {
        assertThatThrownBy(() -> numbers.factorial(numbers.of(new BigDecimal("1.5"))))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void factorialAboveBoundThrowsNonFinite() {
        assertThatThrownBy(() -> numbers.factorial(numbers.of(171L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void factorialBoundaries() {
        assertThat(numbers.factorial(numbers.of(0L))).isEqualTo(numbers.of(1L));
        assertThat(numbers.factorial(numbers.of(5L))).isEqualTo(numbers.of(120L));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=PrecisionTest`

预期：编译失败，`Numbers` 不存在。

- [ ] **Step 3: 创建 CalcNumber**

创建 `src/main/java/com/wysjwxm/calculator/core/number/CalcNumber.java`：

```java
package com.wysjwxm.calculator.core.number;

import java.math.BigDecimal;

/**
 * 计算器数值。两条路径显式分开：
 * <ul>
 *   <li>{@link DecimalNumber} —— 四则运算路径，精确</li>
 *   <li>{@link FloatingNumber} —— 超越函数路径，存在浮点误差</li>
 * </ul>
 *
 * <p>保底不变量：结果只要落在 DecimalNumber 就永远精确；
 * 一旦沾了 FloatingNumber 即存在浮点误差。
 */
public sealed interface CalcNumber permits DecimalNumber, FloatingNumber {

    double toDouble();

    BigDecimal toDecimal();

    /** 是否落在精确路径上。 */
    boolean isExact();
}
```

- [ ] **Step 4: 创建两个实现**

创建 `src/main/java/com/wysjwxm/calculator/core/number/DecimalNumber.java`：

```java
package com.wysjwxm.calculator.core.number;

import java.math.BigDecimal;
import java.util.Objects;

public record DecimalNumber(BigDecimal value) implements CalcNumber {

    public DecimalNumber {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public double toDouble() {
        return value.doubleValue();
    }

    @Override
    public BigDecimal toDecimal() {
        return value;
    }

    @Override
    public boolean isExact() {
        return true;
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/number/FloatingNumber.java`：

```java
package com.wysjwxm.calculator.core.number;

import java.math.BigDecimal;

public record FloatingNumber(double value) implements CalcNumber {

    @Override
    public double toDouble() {
        return value;
    }

    @Override
    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(value);
    }

    @Override
    public boolean isExact() {
        return false;
    }
}
```

- [ ] **Step 5: 创建 Numbers**

创建 `src/main/java/com/wysjwxm/calculator/core/number/Numbers.java`：

```java
package com.wysjwxm.calculator.core.number;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 算术运算与类型提升。所有提升规则集中在此，不散落到求值器里。
 *
 * <p>实例类而非静态工具类，因为除法精度来自配置 —— 把 MathContext 作为
 * 构造参数注入，测试才能固定精度。
 */
public final class Numbers {

    /** 阶乘上界：171! 超出 double 范围。 */
    private static final int FACTORIAL_LIMIT = 170;

    private final MathContext divisionContext;

    public Numbers(int divisionPrecision) {
        if (divisionPrecision < 1) {
            throw new IllegalArgumentException("divisionPrecision 必须为正数: " + divisionPrecision);
        }
        this.divisionContext = new MathContext(divisionPrecision, RoundingMode.HALF_UP);
    }

    public CalcNumber of(long v) {
        return new DecimalNumber(BigDecimal.valueOf(v));
    }

    public CalcNumber of(BigDecimal v) {
        return new DecimalNumber(v);
    }

    public CalcNumber of(double v) {
        return floating(v);
    }

    public CalcNumber floating(double v) {
        return new FloatingNumber(v);
    }

    public CalcNumber add(CalcNumber a, CalcNumber b) {
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().add(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() + b.toDouble(), "加法");
    }

    public CalcNumber subtract(CalcNumber a, CalcNumber b) {
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().subtract(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() - b.toDouble(), "减法");
    }

    public CalcNumber multiply(CalcNumber a, CalcNumber b) {
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().multiply(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() * b.toDouble(), "乘法");
    }

    public CalcNumber divide(CalcNumber a, CalcNumber b) {
        if (isZero(b)) {
            throw CalcException.of(CalcErrorCode.DIVISION_BY_ZERO, "除数不能为零");
        }
        if (bothExact(a, b)) {
            // 能整除时给出精确结果，除不尽时才按配置精度截断
            BigDecimal dividend = a.toDecimal();
            BigDecimal divisor = b.toDecimal();
            try {
                return new DecimalNumber(dividend.divide(divisor));
            } catch (ArithmeticException nonTerminating) {
                return new DecimalNumber(dividend.divide(divisor, divisionContext));
            }
        }
        return floatingFinite(a.toDouble() / b.toDouble(), "除法");
    }

    public CalcNumber modulo(CalcNumber a, CalcNumber b) {
        if (isZero(b)) {
            throw CalcException.of(CalcErrorCode.DIVISION_BY_ZERO, "模运算的除数不能为零");
        }
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().remainder(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() % b.toDouble(), "取余");
    }

    public CalcNumber power(CalcNumber base, CalcNumber exponent) {
        if (base.isExact() && exponent.isExact()) {
            BigDecimal exp = exponent.toDecimal();
            // 仅非负整数指数走精确路径；负指数与分数指数降级到 double
            if (exp.stripTrailingZeros().scale() <= 0 && exp.signum() >= 0) {
                try {
                    return new DecimalNumber(base.toDecimal().pow(exp.intValueExact()));
                } catch (ArithmeticException overflow) {
                    // 指数过大导致超出可表示范围，降级到 double 由 requireFinite 兜底
                    return floatingFinite(Math.pow(base.toDouble(), exp.doubleValue()), "幂运算");
                }
            }
        }
        return floatingFinite(Math.pow(base.toDouble(), exponent.toDouble()), "幂运算");
    }

    public CalcNumber negate(CalcNumber a) {
        if (a.isExact()) {
            return new DecimalNumber(a.toDecimal().negate());
        }
        return floatingFinite(-a.toDouble(), "取负");
    }

    public CalcNumber factorial(CalcNumber a) {
        BigDecimal n = a.toDecimal();
        if (n.stripTrailingZeros().scale() > 0 || n.signum() < 0) {
            throw CalcException.of(CalcErrorCode.DOMAIN_ERROR, "阶乘只接受非负整数，实际为 " + n.toPlainString());
        }
        int value = n.intValueExact();
        if (value > FACTORIAL_LIMIT) {
            throw CalcException.of(CalcErrorCode.NON_FINITE_RESULT,
                    "阶乘上界为 " + FACTORIAL_LIMIT + "，实际为 " + value);
        }
        BigDecimal result = BigDecimal.ONE;
        for (int i = 2; i <= value; i++) {
            result = result.multiply(BigDecimal.valueOf(i));
        }
        return new DecimalNumber(result);
    }

    /** 非有限结果一律拒收，不把 Inf/NaN 透出到响应体。 */
    public static void requireFinite(double v, String what) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw CalcException.of(CalcErrorCode.NON_FINITE_RESULT, what + "结果超出可表示范围");
        }
    }

    private CalcNumber floatingFinite(double v, String what) {
        requireFinite(v, what);
        return new FloatingNumber(v);
    }

    private static boolean bothExact(CalcNumber a, CalcNumber b) {
        return a.isExact() && b.isExact();
    }

    private static boolean isZero(CalcNumber n) {
        return n.toDecimal().signum() == 0;
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

运行：`mvn -q test -Dtest=PrecisionTest`

预期：12 个测试全部 PASS。

- [ ] **Step 7: 写 Numbers 补充测试**

创建 `src/test/java/com/wysjwxm/calculator/core/number/NumbersTest.java`：

```java
package com.wysjwxm.calculator.core.number;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumbersTest {

    private final Numbers numbers = new Numbers(34);

    @Test
    void exactDivisionProducesExactResultWithoutTruncation() {
        CalcNumber result = numbers.divide(numbers.of(new BigDecimal("1")), numbers.of(new BigDecimal("8")));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("0.125"));
    }

    @Test
    void subtractionAndMultiplicationAreExact() {
        assertThat(numbers.subtract(numbers.of(new BigDecimal("0.3")), numbers.of(new BigDecimal("0.1"))))
                .isEqualTo(numbers.of(new BigDecimal("0.2")));
        assertThat(numbers.multiply(numbers.of(new BigDecimal("0.1")), numbers.of(new BigDecimal("0.2"))))
                .isEqualTo(numbers.of(new BigDecimal("0.02")));
    }

    @Test
    void moduloIsExactForDecimals() {
        CalcNumber result = numbers.modulo(numbers.of(new BigDecimal("5.5")), numbers.of(new BigDecimal("2")));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("1.5"));
    }

    @Test
    void negateOnDecimalStaysExact() {
        assertThat(numbers.negate(numbers.of(new BigDecimal("0.1"))))
                .isEqualTo(numbers.of(new BigDecimal("-0.1")));
    }

    @Test
    void nonFiniteResultThrows() {
        assertThatThrownBy(() -> numbers.multiply(numbers.floating(1e308), numbers.floating(10)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void rejectNonPositivePrecision() {
        assertThatThrownBy(() -> new Numbers(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimalNeverConvertsSilentlyThroughDouble() {
        // 用 double 无法精确表示的值走 decimal 路径仍然精确
        CalcNumber result = numbers.add(
                numbers.of(new BigDecimal("9007199254740993")), numbers.of(1L));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("9007199254740994"));
    }
}
```

- [ ] **Step 8: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/number/ \
        src/test/java/com/wysjwxm/calculator/core/number/
git commit -m "feat: 数值模型 — CalcNumber 家族与混合精度算术"
```

---

### Task 4: 词法分析

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/lexer/TokenType.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/lexer/Token.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/lexer/Lexer.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/lexer/LexerTest.java`

**Interfaces:**
- Consumes: `CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `enum TokenType`：`NUMBER IDENT PLUS MINUS STAR SLASH PERCENT CARET BANG LPAREN RPAREN COMMA EOF`
  - `record Token(TokenType type, String lexeme, int position)`
  - `Lexer`，构造器 `Lexer(String input)`，方法 `List<Token> tokenize()`（末尾恒有 `EOF`，出错抛 `PARSE_ERROR` 带 position）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/core/lexer/LexerTest.java`：

```java
package com.wysjwxm.calculator.core.lexer;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerTest {

    private List<Token> lex(String input) {
        return new Lexer(input).tokenize();
    }

    private List<TokenType> types(String input) {
        return lex(input).stream().map(Token::type).toList();
    }

    @Test
    void tokenizesSimpleExpression() {
        assertThat(types("1+2"))
                .containsExactly(TokenType.NUMBER, TokenType.PLUS, TokenType.NUMBER, TokenType.EOF);
    }

    @Test
    void recordsAbsolutePositions() {
        List<Token> tokens = lex("1 + 22");
        assertThat(tokens).extracting(Token::position).containsExactly(0, 2, 4, 7);
    }

    @Test
    void tokenizesDecimalWithoutLeadingDigit() {
        assertThat(lex(".5").get(0)).isEqualTo(new Token(TokenType.NUMBER, ".5", 0));
    }

    @Test
    void tokenizesScientificNotation() {
        assertThat(lex("1.5e-3").get(0).lexeme()).isEqualTo("1.5e-3");
        assertThat(lex("1.5E+10").get(0).lexeme()).isEqualTo("1.5E+10");
    }

    @Test
    void doesNotSwallowMinusOfScientificNotationAsOperator() {
        assertThat(types("1e-3"))
                .containsExactly(TokenType.NUMBER, TokenType.EOF);
    }

    @Test
    void tokenizesAllOperators() {
        assertThat(types("+-*/%^!"))
                .containsExactly(TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH,
                        TokenType.PERCENT, TokenType.CARET, TokenType.BANG, TokenType.EOF);
    }

    @Test
    void tokenizesIdentifiersWithUnderscoreAndDigits() {
        assertThat(lex("x_1").get(0)).isEqualTo(new Token(TokenType.IDENT, "x_1", 0));
    }

    @Test
    void tokenizesParensAndComma() {
        assertThat(types("(1,2)"))
                .containsExactly(TokenType.LPAREN, TokenType.NUMBER, TokenType.COMMA,
                        TokenType.NUMBER, TokenType.RPAREN, TokenType.EOF);
    }

    @Test
    void skipsWhitespace() {
        assertThat(types("  sin ( 30 )  "))
                .containsExactly(TokenType.IDENT, TokenType.LPAREN, TokenType.NUMBER,
                        TokenType.RPAREN, TokenType.EOF);
    }

    @Test
    void illegalCharacterThrowsWithPosition() {
        assertThatThrownBy(() -> lex("1 + @"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(4);
                });
    }

    @Test
    void unterminatedScientificNotationThrows() {
        assertThatThrownBy(() -> lex("1e+"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(0));
    }

    @Test
    void emptyInputYieldsOnlyEof() {
        assertThat(types("")).containsExactly(TokenType.EOF);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=LexerTest`

预期：编译失败，`Lexer` 不存在。

- [ ] **Step 3: 创建 TokenType 与 Token**

创建 `src/main/java/com/wysjwxm/calculator/core/lexer/TokenType.java`：

```java
package com.wysjwxm.calculator.core.lexer;

public enum TokenType {
    NUMBER,
    IDENT,
    PLUS,
    MINUS,
    STAR,
    SLASH,
    PERCENT,
    CARET,
    BANG,
    LPAREN,
    RPAREN,
    COMMA,
    EOF
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/lexer/Token.java`：

```java
package com.wysjwxm.calculator.core.lexer;

/**
 * @param position 起始字符下标（0 基），供错误定位使用
 */
public record Token(TokenType type, String lexeme, int position) {
}
```

- [ ] **Step 4: 创建 Lexer**

创建 `src/main/java/com/wysjwxm/calculator/core/lexer/Lexer.java`：

```java
package com.wysjwxm.calculator.core.lexer;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式词法分析器。每个 Token 携带绝对字符位置，供错误定位。
 */
public final class Lexer {

    private final String input;
    private int pos;

    public Lexer(String input) {
        this.input = input == null ? "" : input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            int start = pos;
            if (Character.isDigit(c) || c == '.') {
                tokens.add(readNumber(start));
            } else if (isIdentStart(c)) {
                tokens.add(readIdent(start));
            } else {
                tokens.add(readSymbol(start, c));
            }
        }
        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    private Token readNumber(int start) {
        // 整数部分
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        // 小数部分
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        // 指数部分：e/E 后面必须紧跟可选符号 + 至少一位数字，否则不吞掉 e
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int save = pos;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            } else {
                // 不是合法的科学计数法，回退（例如 "1e" 应报错，而不是拆成 NUMBER(1) IDENT(e)）
                pos = save;
                throw CalcException.at(CalcErrorCode.PARSE_ERROR, "科学计数法指数部分不完整", save);
            }
        }
        String lexeme = input.substring(start, pos);
        if (lexeme.equals(".")) {
            throw CalcException.at(CalcErrorCode.PARSE_ERROR, "孤立的小数点不是合法数字", start);
        }
        return new Token(TokenType.NUMBER, lexeme, start);
    }

    private Token readIdent(int start) {
        while (pos < input.length() && isIdentPart(input.charAt(pos))) {
            pos++;
        }
        return new Token(TokenType.IDENT, input.substring(start, pos), start);
    }

    private Token readSymbol(int start, char c) {
        pos++;
        TokenType type = switch (c) {
            case '+' -> TokenType.PLUS;
            case '-' -> TokenType.MINUS;
            case '*' -> TokenType.STAR;
            case '/' -> TokenType.SLASH;
            case '%' -> TokenType.PERCENT;
            case '^' -> TokenType.CARET;
            case '!' -> TokenType.BANG;
            case '(' -> TokenType.LPAREN;
            case ')' -> TokenType.RPAREN;
            case ',' -> TokenType.COMMA;
            default -> throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                    "无法识别的字符 '" + c + "'", start);
        };
        return new Token(type, String.valueOf(c), start);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=LexerTest`

预期：12 个测试全部 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/lexer/ \
        src/test/java/com/wysjwxm/calculator/core/lexer/
git commit -m "feat: 表达式词法分析器，Token 携带字符位置"
```

---

### Task 5: 算子表

建立算子的**唯一事实来源**。解析器与 `/functions` 清单都从这里读，杜绝两处优先级漂移。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/operator/Fixity.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/operator/Associativity.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/operator/Operator.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/operator/OperatorTable.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/operator/OperatorTableTest.java`

**Interfaces:**
- Consumes: `TokenType`（Task 4）
- Produces:
  - `enum Fixity { INFIX, PREFIX, POSTFIX }`
  - `enum Associativity { LEFT, RIGHT }`
  - `record Operator(String symbol, Fixity fixity, int precedence, Associativity associativity)`（`associativity` 对 PREFIX/POSTFIX 为 `null`）
  - `OperatorTable` 静态方法：
    - `Optional<Operator> infix(TokenType type)`
    - `Optional<Operator> prefix(TokenType type)`
    - `Optional<Operator> postfix(TokenType type)`
    - `List<Operator> all()`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/core/operator/OperatorTableTest.java`：

```java
package com.wysjwxm.calculator.core.operator;

import com.wysjwxm.calculator.core.lexer.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OperatorTable 是算子优先级的唯一事实来源。这些断言把 spec §6.3 的
 * 三条规则钉死在数据上 —— 解析器读的就是这份数据。
 */
class OperatorTableTest {

    @Test
    void precedenceMatchesSpec() {
        assertThat(OperatorTable.infix(TokenType.PLUS).orElseThrow().precedence()).isEqualTo(1);
        assertThat(OperatorTable.infix(TokenType.MINUS).orElseThrow().precedence()).isEqualTo(1);
        assertThat(OperatorTable.infix(TokenType.STAR).orElseThrow().precedence()).isEqualTo(2);
        assertThat(OperatorTable.infix(TokenType.SLASH).orElseThrow().precedence()).isEqualTo(2);
        assertThat(OperatorTable.infix(TokenType.PERCENT).orElseThrow().precedence()).isEqualTo(2);
        assertThat(OperatorTable.prefix(TokenType.PLUS).orElseThrow().precedence()).isEqualTo(3);
        assertThat(OperatorTable.prefix(TokenType.MINUS).orElseThrow().precedence()).isEqualTo(3);
        assertThat(OperatorTable.infix(TokenType.CARET).orElseThrow().precedence()).isEqualTo(4);
        assertThat(OperatorTable.postfix(TokenType.BANG).orElseThrow().precedence()).isEqualTo(5);
    }

    @Test
    void powerIsRightAssociativeAndOthersLeft() {
        assertThat(OperatorTable.infix(TokenType.CARET).orElseThrow().associativity())
                .isEqualTo(Associativity.RIGHT);
        assertThat(OperatorTable.infix(TokenType.PLUS).orElseThrow().associativity())
                .isEqualTo(Associativity.LEFT);
        assertThat(OperatorTable.infix(TokenType.STAR).orElseThrow().associativity())
                .isEqualTo(Associativity.LEFT);
    }

    @Test
    void unaryPrefixBindsLooserThanPower() {
        // spec §6.3：-2^2 == -4，即一元负号优先级(3) 低于 ^(4)
        assertThat(OperatorTable.prefix(TokenType.MINUS).orElseThrow().precedence())
                .isLessThan(OperatorTable.infix(TokenType.CARET).orElseThrow().precedence());
    }

    @Test
    void postfixBindsTightest() {
        int maxInfix = OperatorTable.all().stream()
                .filter(o -> o.fixity() == Fixity.INFIX)
                .mapToInt(Operator::precedence).max().orElseThrow();
        assertThat(OperatorTable.postfix(TokenType.BANG).orElseThrow().precedence())
                .isGreaterThan(maxInfix);
    }

    @Test
    void nonOperatorTokensResolveToEmpty() {
        assertThat(OperatorTable.infix(TokenType.NUMBER)).isEmpty();
        assertThat(OperatorTable.infix(TokenType.LPAREN)).isEmpty();
        assertThat(OperatorTable.prefix(TokenType.STAR)).isEmpty();
        assertThat(OperatorTable.postfix(TokenType.CARET)).isEmpty();
    }

    @Test
    void allExposesNineOperatorEntries() {
        // + - (infix), + - (prefix), * / %, ^, !  →  9 条
        List<Operator> all = OperatorTable.all();
        assertThat(all).hasSize(9);
        assertThat(all.stream().filter(o -> o.symbol().equals("-"))).hasSize(2);
        assertThat(all.stream().filter(o -> o.symbol().equals("+"))).hasSize(2);
    }

    @Test
    void symbolsUseMathematicalNotationNotNames() {
        // 保证清单里出现的是 + 而不是 "add" —— 见 spec §12 D3/D8
        assertThat(OperatorTable.all()).extracting(Operator::symbol)
                .containsExactlyInAnyOrder("+", "+", "-", "-", "*", "/", "%", "^", "!");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=OperatorTableTest`

预期：编译失败，`OperatorTable` 不存在。

- [ ] **Step 3: 创建枚举与 Operator**

创建 `src/main/java/com/wysjwxm/calculator/core/operator/Fixity.java`：

```java
package com.wysjwxm.calculator.core.operator;

public enum Fixity {
    INFIX,
    PREFIX,
    POSTFIX
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/operator/Associativity.java`：

```java
package com.wysjwxm.calculator.core.operator;

public enum Associativity {
    LEFT,
    RIGHT
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/operator/Operator.java`：

```java
package com.wysjwxm.calculator.core.operator;

/**
 * @param precedence    数值越大结合越紧
 * @param associativity 仅 INFIX 有意义；PREFIX / POSTFIX 为 null
 */
public record Operator(String symbol, Fixity fixity, int precedence, Associativity associativity) {
}
```

- [ ] **Step 4: 创建 OperatorTable**

创建 `src/main/java/com/wysjwxm/calculator/core/operator/OperatorTable.java`：

```java
package com.wysjwxm.calculator.core.operator;

import com.wysjwxm.calculator.core.lexer.TokenType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 算子优先级与结合性的唯一事实来源。
 *
 * <p>解析器与 /functions 能力清单都从这份数据读取，因此两者不可能漂移 ——
 * 这是刻意的设计：若把优先级硬编码进解析器的嵌套方法，同样的数值会被写两遍，
 * 而防漂移测试会变得形同虚设（清单与数据一致，但两者同时与解析器行为不符）。
 */
public final class OperatorTable {

    private static final Map<TokenType, Operator> INFIX = new EnumMap<>(TokenType.class);
    private static final Map<TokenType, Operator> PREFIX = new EnumMap<>(TokenType.class);
    private static final Map<TokenType, Operator> POSTFIX = new EnumMap<>(TokenType.class);
    private static final List<Operator> ALL;

    static {
        Operator plus = new Operator("+", Fixity.INFIX, 1, Associativity.LEFT);
        Operator minus = new Operator("-", Fixity.INFIX, 1, Associativity.LEFT);
        Operator star = new Operator("*", Fixity.INFIX, 2, Associativity.LEFT);
        Operator slash = new Operator("/", Fixity.INFIX, 2, Associativity.LEFT);
        Operator percent = new Operator("%", Fixity.INFIX, 2, Associativity.LEFT);
        Operator caret = new Operator("^", Fixity.INFIX, 4, Associativity.RIGHT);
        Operator unaryPlus = new Operator("+", Fixity.PREFIX, 3, null);
        Operator unaryMinus = new Operator("-", Fixity.PREFIX, 3, null);
        Operator bang = new Operator("!", Fixity.POSTFIX, 5, null);

        INFIX.put(TokenType.PLUS, plus);
        INFIX.put(TokenType.MINUS, minus);
        INFIX.put(TokenType.STAR, star);
        INFIX.put(TokenType.SLASH, slash);
        INFIX.put(TokenType.PERCENT, percent);
        INFIX.put(TokenType.CARET, caret);

        PREFIX.put(TokenType.PLUS, unaryPlus);
        PREFIX.put(TokenType.MINUS, unaryMinus);

        POSTFIX.put(TokenType.BANG, bang);

        ALL = List.of(plus, minus, star, slash, percent, caret, unaryPlus, unaryMinus, bang);
    }

    private OperatorTable() {
    }

    public static Optional<Operator> infix(TokenType type) {
        return Optional.ofNullable(INFIX.get(type));
    }

    public static Optional<Operator> prefix(TokenType type) {
        return Optional.ofNullable(PREFIX.get(type));
    }

    public static Optional<Operator> postfix(TokenType type) {
        return Optional.ofNullable(POSTFIX.get(type));
    }

    /** 全部算子条目，供 /functions 能力清单生成。 */
    public static List<Operator> all() {
        return ALL;
    }

    /**
     * 运算符 TokenType 是否有对应的中缀语义 —— 求值器据此分派算术实现。
     */
    public static boolean isArithmeticInfix(TokenType type) {
        return INFIX.containsKey(type);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=OperatorTableTest`

预期：7 个测试全部 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/operator/ \
        src/test/java/com/wysjwxm/calculator/core/operator/
git commit -m "feat: 算子表 — 优先级与结合性的唯一事实来源"
```

---

### Task 6: AST 与优先级爬升解析器

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/Expression.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/LiteralExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/VariableExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/UnaryExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/PostfixExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/BinaryExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ast/CallExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ExpressionPrinter.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/parser/ExpressionParser.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/parser/ExpressionParserTest.java`

**Interfaces:**
- Consumes: `Lexer`、`Token`、`TokenType`（Task 4）；`Operator`、`OperatorTable`、`Associativity`、`Fixity`（Task 5）；`CalcNumber`、`DecimalNumber`（Task 3）
- Produces:
  - `sealed interface Expression permits LiteralExpr, VariableExpr, UnaryExpr, PostfixExpr, BinaryExpr, CallExpr`
  - `record LiteralExpr(CalcNumber value)`
  - `record VariableExpr(String name, int position)`
  - `record UnaryExpr(Operator operator, Expression operand)`
  - `record PostfixExpr(Operator operator, Expression operand)`
  - `record BinaryExpr(Operator operator, Expression left, Expression right)`
  - `record CallExpr(String functionName, List<Expression> arguments, int position)`
  - `ExpressionParser`，构造器 `ExpressionParser()`，方法 `Expression parse(String expression)`（**留白未做 trim**，调用方负责）
  - `ExpressionPrinter.toInfix(Expression)` → `String`，供测试断言 AST 结构

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/core/parser/ExpressionParserTest.java`：

```java
package com.wysjwxm.calculator.core.parser;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用括号完全显式化的中缀打印来做 AST 结构断言 —— 比逐层 getter 断言更易读，
 * 也更容易看出优先级绑定是否符合预期。
 */
class ExpressionParserTest {

    private final ExpressionParser parser = new ExpressionParser();

    private String infix(String input) {
        return ExpressionPrinter.toInfix(parser.parse(input));
    }

    // ---------- 结构与优先级 ----------

    @Test
    void multiplicationBindsTighterThanAddition() {
        assertThat(infix("1+2*3")).isEqualTo("(1 + (2 * 3))");
    }

    @Test
    void subtractionIsLeftAssociative() {
        assertThat(infix("1-2-3")).isEqualTo("((1 - 2) - 3)");
    }

    @Test
    void divisionIsLeftAssociative() {
        assertThat(infix("8/4/2")).isEqualTo("((8 / 4) / 2)");
    }

    @Test
    void powerIsRightAssociative() {
        // spec §6.3：2^3^2 == 2^(3^2) == 512
        assertThat(infix("2^3^2")).isEqualTo("(2 ^ (3 ^ 2))");
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        // spec §6.3：-2^2 == -(2^2) == -4，而不是 (-2)^2 == 4
        assertThat(infix("-2^2")).isEqualTo("(-(2 ^ 2))");
    }

    @Test
    void powerBindsTighterThanUnaryOnExponentSide() {
        assertThat(infix("2^-3")).isEqualTo("(2 ^ (-3))");
    }

    @Test
    void factorialBindsTighterThanEverything() {
        // spec §6.3：3! + 1 == 7
        assertThat(infix("3!+1")).isEqualTo("((3!) + 1)");
    }

    @Test
    void factorialBindsTighterThanUnaryMinus() {
        assertThat(infix("-3!")).isEqualTo("(-(3!))");
    }

    @Test
    void parenthesesOverridePrecedence() {
        assertThat(infix("(1+2)*3")).isEqualTo("((1 + 2) * 3)");
    }

    @Test
    void unaryPlusIsParsed() {
        assertThat(infix("+5")).isEqualTo("(+5)");
    }

    @Test
    void moduloIsParsed() {
        assertThat(infix("7%3")).isEqualTo("(7 % 3)");
    }

    // ---------- 函数调用 ----------

    @Test
    void parsesZeroArgumentCall() {
        assertThat(infix("now()")).isEqualTo("now()");
    }

    @Test
    void parsesSingleArgumentCall() {
        assertThat(infix("sin(30)")).isEqualTo("sin(30)");
    }

    @Test
    void parsesMultiArgumentCall() {
        assertThat(infix("log(8,2)")).isEqualTo("log(8, 2)");
    }

    @Test
    void parsesNestedCalls() {
        assertThat(infix("sin(cos(1))")).isEqualTo("sin(cos(1))");
    }

    @Test
    void argumentExpressionsParseFully() {
        assertThat(infix("max(1+2, 3*4)")).isEqualTo("max((1 + 2), (3 * 4))");
    }

    // ---------- 变量与字面量 ----------

    @Test
    void parsesBareIdentifierAsVariable() {
        assertThat(infix("x*2")).isEqualTo("(x * 2)");
    }

    @Test
    void parsesIdentifierStartingWithUnderscore() {
        assertThat(infix("_a + 1")).isEqualTo("(_a + 1)");
    }

    @Test
    void literalKeepsDecimalExactness() {
        LiteralExpr literal = (LiteralExpr) parser.parse("0.1");
        assertThat(literal.value().isExact()).isTrue();
        assertThat(literal.value().toDecimal()).isEqualByComparingTo("0.1");
    }

    // ---------- 错误与位置 ----------

    @Test
    void missingClosingParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse("sin(30"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(7);
                });
    }

    @Test
    void unexpectedTokenReportsPosition() {
        assertThatThrownBy(() -> parser.parse("1 + * 2"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(4));
    }

    @Test
    void trailingOperatorReportsPosition() {
        assertThatThrownBy(() -> parser.parse("1 +"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(3));
    }

    @Test
    void mismatchedParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse("(1+2"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(4));
    }

    @Test
    void emptyInputReportsPositionZero() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(0));
    }

    @Test
    void missingCallClosingParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse("max(1,2"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(7));
    }

    @Test
    void trailingCommaInCallIsRejected() {
        assertThatThrownBy(() -> parser.parse("max(1,)"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(6));
    }

    @Test
    void strayClosingParenIsRejected() {
        assertThatThrownBy(() -> parser.parse("(1+2))"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(5));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=ExpressionParserTest`

预期：编译失败，`ExpressionParser` 不存在。

- [ ] **Step 3: 创建 AST 节点**

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/Expression.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

/**
 * 表达式语法树。sealed 让求值器可以用穷尽的 switch 模式匹配，
 * 日后新增节点类型时编译器会强制所有求值分支同步更新。
 */
public sealed interface Expression
        permits LiteralExpr, VariableExpr, UnaryExpr, PostfixExpr, BinaryExpr, CallExpr {
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/LiteralExpr.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

import com.wysjwxm.calculator.core.number.CalcNumber;

public record LiteralExpr(CalcNumber value) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/VariableExpr.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

/**
 * @param position 标识符在原文中的起始下标，供「未定义变量」报错定位
 */
public record VariableExpr(String name, int position) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/UnaryExpr.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

import com.wysjwxm.calculator.core.operator.Operator;

public record UnaryExpr(Operator operator, Expression operand) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/PostfixExpr.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

import com.wysjwxm.calculator.core.operator.Operator;

public record PostfixExpr(Operator operator, Expression operand) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/BinaryExpr.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

import com.wysjwxm.calculator.core.operator.Operator;

public record BinaryExpr(Operator operator, Expression left, Expression right) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ast/CallExpr.java`：

```java
package com.wysjwxm.calculator.core.parser.ast;

import java.util.List;

/**
 * @param position 函数名在原文中的起始下标，供「未知函数」报错定位
 */
public record CallExpr(String functionName, List<Expression> arguments, int position) implements Expression {

    public CallExpr {
        arguments = List.copyOf(arguments);
    }
}
```

- [ ] **Step 4: 创建 ExpressionPrinter**

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ExpressionPrinter.java`：

```java
package com.wysjwxm.calculator.core.parser;

import com.wysjwxm.calculator.core.parser.ast.BinaryExpr;
import com.wysjwxm.calculator.core.parser.ast.CallExpr;
import com.wysjwxm.calculator.core.parser.ast.Expression;
import com.wysjwxm.calculator.core.parser.ast.LiteralExpr;
import com.wysjwxm.calculator.core.parser.ast.PostfixExpr;
import com.wysjwxm.calculator.core.parser.ast.UnaryExpr;
import com.wysjwxm.calculator.core.parser.ast.VariableExpr;

import java.util.stream.Collectors;

/**
 * 把 AST 打印成完全括号化的中缀文本，仅用于测试断言与调试。
 *
 * <p>每个二元运算都加括号，因此字符串本身就唯一确定了树的结构 ——
 * 这让优先级与结合性的测试一眼可读。
 */
public final class ExpressionPrinter {

    private ExpressionPrinter() {
    }

    public static String toInfix(Expression expr) {
        return switch (expr) {
            case LiteralExpr l -> l.value().toDecimal().stripTrailingZeros().toPlainString();
            case VariableExpr v -> v.name();
            case UnaryExpr u -> "(" + u.operator().symbol() + toInfix(u.operand()) + ")";
            case PostfixExpr p -> "(" + toInfix(p.operand()) + p.operator().symbol() + ")";
            case BinaryExpr b -> "(" + toInfix(b.left()) + " " + b.operator().symbol()
                    + " " + toInfix(b.right()) + ")";
            case CallExpr c -> c.functionName() + "(" + c.arguments().stream()
                    .map(ExpressionPrinter::toInfix)
                    .collect(Collectors.joining(", ")) + ")";
        };
    }
}
```

- [ ] **Step 5: 创建 ExpressionParser**

创建 `src/main/java/com/wysjwxm/calculator/core/parser/ExpressionParser.java`：

```java
package com.wysjwxm.calculator.core.parser;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.lexer.Lexer;
import com.wysjwxm.calculator.core.lexer.Token;
import com.wysjwxm.calculator.core.lexer.TokenType;
import com.wysjwxm.calculator.core.number.DecimalNumber;
import com.wysjwxm.calculator.core.operator.Associativity;
import com.wysjwxm.calculator.core.operator.Operator;
import com.wysjwxm.calculator.core.operator.OperatorTable;
import com.wysjwxm.calculator.core.parser.ast.BinaryExpr;
import com.wysjwxm.calculator.core.parser.ast.CallExpr;
import com.wysjwxm.calculator.core.parser.ast.Expression;
import com.wysjwxm.calculator.core.parser.ast.LiteralExpr;
import com.wysjwxm.calculator.core.parser.ast.PostfixExpr;
import com.wysjwxm.calculator.core.parser.ast.UnaryExpr;
import com.wysjwxm.calculator.core.parser.ast.VariableExpr;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 优先级爬升（precedence climbing）式递归下降解析器。
 *
 * <p>优先级与结合性全部来自 {@link OperatorTable}，解析器自身不含任何硬编码的
 * 优先级数值 —— 这样 /functions 能力清单（同样由 OperatorTable 生成）不可能
 * 与实际解析行为不一致。
 */
public final class ExpressionParser {

    /** 最松的结合层级，作为入口。 */
    private static final int MIN_PRECEDENCE = 0;

    private List<Token> tokens;
    private int index;

    public Expression parse(String expression) {
        this.tokens = new Lexer(expression).tokenize();
        this.index = 0;
        Expression result = parseExpression(MIN_PRECEDENCE);
        Token trailing = peek();
        if (trailing.type() != TokenType.EOF) {
            throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                    "表达式在 '" + trailing.lexeme() + "' 处出现多余内容", trailing.position());
        }
        return result;
    }

    /**
     * 核心循环：先解析一个一元前缀或基本项，然后不断吸收优先级不低于
     * minPrecedence 的中缀与后缀算子。
     *
     * @param minPrecedence 当前上下文允许吸收的最低优先级；左结合算子递归时
     *                      传 precedence+1，右结合传 precedence，由此实现结合性
     */
    private Expression parseExpression(int minPrecedence) {
        Expression left = parseUnary();
        while (true) {
            Token token = peek();

            Optional<Operator> infix = OperatorTable.infix(token.type());
            if (infix.isPresent() && infix.get().precedence() >= minPrecedence) {
                Operator op = infix.get();
                advance();
                int nextMin = op.associativity() == Associativity.LEFT
                        ? op.precedence() + 1
                        : op.precedence();
                left = new BinaryExpr(op, left, parseExpression(nextMin));
                continue;
            }

            Optional<Operator> postfix = OperatorTable.postfix(token.type());
            if (postfix.isPresent() && postfix.get().precedence() >= minPrecedence) {
                Operator op = postfix.get();
                advance();
                left = new PostfixExpr(op, left);
                continue;
            }

            return left;
        }
    }

    private Expression parseUnary() {
        Token token = peek();
        Optional<Operator> prefix = OperatorTable.prefix(token.type());
        if (prefix.isPresent()) {
            Operator op = prefix.get();
            advance();
            // 用算子自身优先级作为下界，一元负号(3)因此不会吞掉 ^(4)，得到 -2^2 == -4
            return new UnaryExpr(op, parseExpression(op.precedence()));
        }
        return parsePrimary();
    }

    private Expression parsePrimary() {
        Token token = peek();
        return switch (token.type()) {
            case NUMBER -> {
                advance();
                yield new LiteralExpr(new DecimalNumber(new BigDecimal(token.lexeme())));
            }
            case IDENT -> {
                advance();
                if (peek().type() == TokenType.LPAREN) {
                    yield parseCall(token);
                }
                yield new VariableExpr(token.lexeme(), token.position());
            }
            case LPAREN -> {
                advance();
                Expression inner = parseExpression(MIN_PRECEDENCE);
                expect(TokenType.RPAREN, "缺少右括号");
                yield inner;
            }
            default -> throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                    "表达式不完整或出现意外符号 '" + token.lexeme() + "'", token.position());
        };
    }

    private Expression parseCall(Token nameToken) {
        expect(TokenType.LPAREN, "函数调用缺少左括号");
        List<Expression> arguments = new ArrayList<>();
        if (peek().type() != TokenType.RPAREN) {
            arguments.add(parseExpression(MIN_PRECEDENCE));
            while (peek().type() == TokenType.COMMA) {
                advance();
                arguments.add(parseExpression(MIN_PRECEDENCE));
            }
        }
        expect(TokenType.RPAREN, "函数调用 " + nameToken.lexeme() + " 缺少右括号");
        return new CallExpr(nameToken.lexeme(), arguments, nameToken.position());
    }

    private void expect(TokenType expected, String message) {
        Token token = peek();
        if (token.type() != expected) {
            throw CalcException.at(CalcErrorCode.PARSE_ERROR, message, token.position());
        }
        advance();
    }

    private Token peek() {
        return tokens.get(index);
    }

    private void advance() {
        index++;
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

运行：`mvn -q test -Dtest=ExpressionParserTest`

预期：22 个测试全部 PASS。

**若 `powerIsRightAssociative` 失败**：检查 `parseExpression` 中 `nextMin` 的计算——右结合必须传 `op.precedence()` 而非 `+1`。

**若 `unaryMinusBindsLooserThanPower` 失败**：检查 `parseUnary` 是否用 `parseExpression(op.precedence())` 而非 `parseExpression(MIN_PRECEDENCE)`。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/parser/ \
        src/test/java/com/wysjwxm/calculator/core/parser/
git commit -m "feat: AST 与优先级爬升解析器，优先级由 OperatorTable 驱动"
```

---

### Task 7: 变量与函数注册表

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/Constants.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/function/MathFunction.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/function/UnaryFunction.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/function/BinaryFunction.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/function/FunctionRegistry.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/ConstantsTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/function/FunctionRegistryTest.java`

**Interfaces:**
- Consumes: `AngleUnit`（Task 1）、`CalcNumber`、`Numbers`（Task 3）、`CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `Constants`：静态方法 `Optional<CalcNumber> lookup(String name)`、`Set<String> names()`、`boolean isReserved(String name)`
  - `interface MathFunction`：`String name()`、`int arity()`、`boolean angleSensitive()`、`CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers)`
  - `enum UnaryFunction implements MathFunction`（23 个）、`enum BinaryFunction implements MathFunction`（5 个）
  - `FunctionRegistry`：构造器 `FunctionRegistry()`，方法 `Optional<MathFunction> find(String name)`、`Set<String> names()`、`List<String> unaryNames()`、`List<String> binaryNames()`、`Set<String> reservedNames()`（**函数名 ∪ 常量名**，供变量名校验复用）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/core/ConstantsTest.java`：

```java
package com.wysjwxm.calculator.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantsTest {

    @Test
    void resolvesPiAndE() {
        assertThat(Constants.lookup("pi").orElseThrow().toDouble())
                .isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(Constants.lookup("e").orElseThrow().toDouble())
                .isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void unknownNameIsNotFound() {
        assertThat(Constants.lookup("x")).isEmpty();
    }

    @Test
    void namesAreReserved() {
        assertThat(Constants.names()).containsExactlyInAnyOrder("pi", "e");
        assertThat(Constants.isReserved("pi")).isTrue();
        assertThat(Constants.isReserved("x")).isFalse();
    }
}
```

创建 `src/test/java/com/wysjwxm/calculator/core/function/FunctionRegistryTest.java`：

```java
package com.wysjwxm.calculator.core.function;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionRegistryTest {

    private final FunctionRegistry registry = new FunctionRegistry();
    private final Numbers numbers = new Numbers(34);

    private CalcNumber apply(String name, AngleUnit unit, double... args) {
        List<CalcNumber> values = java.util.Arrays.stream(args).mapToObj(numbers::of).toList();
        return registry.find(name).orElseThrow().apply(values, unit, numbers);
    }

    @Test
    void registersExactlyTwentyThreeUnaryAndFiveBinary() {
        assertThat(registry.unaryNames()).hasSize(23);
        assertThat(registry.binaryNames()).hasSize(5);
    }

    @Test
    void doesNotRegisterRedundantFunctionSpellings() {
        // spec §12 D3：有中缀/后缀写法的运算不提供函数形式
        assertThat(registry.find("pow")).isEmpty();
        assertThat(registry.find("mod")).isEmpty();
        assertThat(registry.find("fact")).isEmpty();
    }

    @Test
    void unknownFunctionIsNotFound() {
        assertThat(registry.find("nope")).isEmpty();
    }

    @Test
    void degreeAndRadianGiveDifferentSineResults() {
        assertThat(apply("sin", AngleUnit.DEGREE, 30).toDouble()).isCloseTo(0.5,
                org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("sin", AngleUnit.RADIAN, 30).toDouble()).isCloseTo(Math.sin(30),
                org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void degreesAreIgnoredByNonTrigonometricFunctions() {
        assertThat(apply("sqrt", AngleUnit.DEGREE, 4).toDouble()).isEqualTo(2.0);
        assertThat(apply("sqrt", AngleUnit.RADIAN, 4).toDouble()).isEqualTo(2.0);
    }

    @Test
    void inverseTrigRespectsAngleUnit() {
        assertThat(apply("asin", AngleUnit.DEGREE, 0.5).toDouble()).isCloseTo(30.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(apply("asin", AngleUnit.RADIAN, 0.5).toDouble()).isCloseTo(Math.asin(0.5),
                org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void atan2RespectsAngleUnit() {
        assertThat(apply("atan2", AngleUnit.DEGREE, 1, 1).toDouble()).isCloseTo(45.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void binaryFunctionsWork() {
        assertThat(apply("hypot", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(5.0);
        assertThat(apply("max", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(4.0);
        assertThat(apply("min", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(3.0);
        assertThat(apply("log", AngleUnit.RADIAN, 8, 2).toDouble()).isCloseTo(3.0,
                org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void unaryFunctionsWork() {
        assertThat(apply("abs", AngleUnit.RADIAN, -3).toDouble()).isEqualTo(3.0);
        assertThat(apply("floor", AngleUnit.RADIAN, 2.7).toDouble()).isEqualTo(2.0);
        assertThat(apply("ceil", AngleUnit.RADIAN, 2.1).toDouble()).isEqualTo(3.0);
        assertThat(apply("round", AngleUnit.RADIAN, 2.5).toDouble()).isEqualTo(3.0);
        assertThat(apply("sign", AngleUnit.RADIAN, -9).toDouble()).isEqualTo(-1.0);
        assertThat(apply("exp", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(1.0);
        assertThat(apply("ln", AngleUnit.RADIAN, 1).toDouble()).isEqualTo(0.0);
        assertThat(apply("log10", AngleUnit.RADIAN, 100).toDouble()).isCloseTo(2.0,
                org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("log2", AngleUnit.RADIAN, 8).toDouble()).isCloseTo(3.0,
                org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("cbrt", AngleUnit.RADIAN, 27).toDouble()).isCloseTo(3.0,
                org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 定义域 ----------

    private void assertDomainError(String name, double... args) {
        assertThatThrownBy(() -> apply(name, AngleUnit.RADIAN, args))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void sqrtOfNegativeIsDomainError() {
        assertDomainError("sqrt", -1);
    }

    @Test
    void logarithmOfNonPositiveIsDomainError() {
        assertDomainError("ln", 0);
        assertDomainError("ln", -1);
        assertDomainError("log10", 0);
        assertDomainError("log2", -1);
    }

    @Test
    void logWithInvalidBaseIsDomainError() {
        assertDomainError("log", 8, 0);
        assertDomainError("log", 8, 1);
        assertDomainError("log", -1, 2);
    }

    @Test
    void asinOutOfRangeIsDomainError() {
        assertDomainError("asin", 2);
        assertDomainError("acos", -2);
    }

    @Test
    void acoshBelowOneIsDomainError() {
        assertDomainError("acosh", 0.5);
    }

    @Test
    void atanhOutOfOpenIntervalIsDomainError() {
        assertDomainError("atanh", 1);
        assertDomainError("atanh", -1);
    }

    // ---------- 元数 ----------

    @Test
    void wrongArityThrows() {
        assertThatThrownBy(() -> registry.find("sin").orElseThrow()
                .apply(List.of(numbers.of(1L), numbers.of(2L)), AngleUnit.RADIAN, numbers))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    // ---------- 保留名 ----------

    @Test
    void reservedNamesIncludeFunctionsAndConstants() {
        assertThat(registry.reservedNames()).contains("sin", "sqrt", "log", "pi", "e");
        assertThat(registry.reservedNames()).doesNotContain("x");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest='ConstantsTest,FunctionRegistryTest'`

预期：编译失败，`Constants` / `FunctionRegistry` 不存在。

- [ ] **Step 3: 创建 Constants**

创建 `src/main/java/com/wysjwxm/calculator/core/Constants.java`：

```java
package com.wysjwxm.calculator.core;

import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.FloatingNumber;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 内置保留常量。
 *
 * <p>这些名字属于语言的一部分，不是可被覆盖的缺省值 —— 用户变量不得使用它们
 * （见 spec §6.4）。若允许 pi = 3，则 sin(pi) 的含义会随写入操作静默改变。
 */
public final class Constants {

    private static final Map<String, CalcNumber> VALUES = new LinkedHashMap<>();

    static {
        VALUES.put("pi", new FloatingNumber(Math.PI));
        VALUES.put("e", new FloatingNumber(Math.E));
    }

    private Constants() {
    }

    public static Optional<CalcNumber> lookup(String name) {
        return Optional.ofNullable(VALUES.get(name));
    }

    public static Set<String> names() {
        return VALUES.keySet();
    }

    public static boolean isReserved(String name) {
        return VALUES.containsKey(name);
    }
}
```

- [ ] **Step 4: 创建 MathFunction 接口**

创建 `src/main/java/com/wysjwxm/calculator/core/function/MathFunction.java`：

```java
package com.wysjwxm.calculator.core.function;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;

import java.util.List;

public interface MathFunction {

    String name();

    int arity();

    /** 是否受角度单位影响（三角函数与反三角函数、atan2）。 */
    boolean angleSensitive();

    CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers);
}
```

- [ ] **Step 5: 创建 UnaryFunction**

创建 `src/main/java/com/wysjwxm/calculator/core/function/UnaryFunction.java`：

```java
package com.wysjwxm.calculator.core.function;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 23 个一元函数。表驱动而非 23 个类 —— 每个函数的差异只有
 * 名字、角度敏感性、定义域约束、double 实现四件事。
 *
 * <p>定义域违规一律抛 DOMAIN_ERROR，不静默返回 NaN。
 */
public enum UnaryFunction implements MathFunction {

    // 三角函数（角度敏感）
    SIN("sin", true, Domain.ANY, Math::sin),
    COS("cos", true, Domain.ANY, Math::cos),
    TAN("tan", true, Domain.ANY, Math::tan),
    ASIN("asin", true, Domain.UNIT_INTERVAL, Math::asin),
    ACOS("acos", true, Domain.UNIT_INTERVAL, Math::acos),
    ATAN("atan", true, Domain.ANY, Math::atan),

    // 双曲函数（角度无关）
    SINH("sinh", false, Domain.ANY, Math::sinh),
    COSH("cosh", false, Domain.ANY, Math::cosh),
    TANH("tanh", false, Domain.ANY, Math::tanh),
    ASINH("asinh", false, Domain.ANY, Math::asinh),
    ACOSH("acosh", false, Domain.AT_LEAST_ONE, Math::acosh),
    ATANH("atanh", false, Domain.OPEN_UNIT_INTERVAL, Math::atanh),

    // 幂与根
    SQRT("sqrt", false, Domain.NON_NEGATIVE, Math::sqrt),
    CBRT("cbrt", false, Domain.ANY, Math::cbrt),

    // 指数与对数
    ABS("abs", false, Domain.ANY, Math::abs),
    EXP("exp", false, Domain.ANY, Math::exp),
    LN("ln", false, Domain.POSITIVE, Math::log),
    LOG10("log10", false, Domain.POSITIVE, Math::log10),
    LOG2("log2", false, Domain.POSITIVE, x -> Math.log(x) / Math.log(2)),

    // 取整与符号
    FLOOR("floor", false, Domain.ANY, Math::floor),
    CEIL("ceil", false, Domain.ANY, Math::ceil),
    ROUND("round", false, Domain.ANY, x -> (double) Math.round(x)),
    SIGN("sign", false, Domain.ANY, Math::signum);

    /** 对函数参数 x 的定义域约束。 */
    enum Domain {
        ANY,
        NON_NEGATIVE,
        POSITIVE,
        UNIT_INTERVAL,          // asin / acos： -1 <= x <= 1
        AT_LEAST_ONE,           // acosh：      x >= 1
        OPEN_UNIT_INTERVAL      // atanh：      -1 < x < 1
    }

    private final String functionName;
    private final boolean angleSensitive;
    private final Domain domain;
    private final DoubleUnaryOperator implementation;

    UnaryFunction(String functionName, boolean angleSensitive, Domain domain,
                  DoubleUnaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.domain = domain;
        this.implementation = implementation;
    }

    @Override
    public String name() {
        return functionName;
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public boolean angleSensitive() {
        return angleSensitive;
    }

    @Override
    public CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers) {
        if (args.size() != 1) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    functionName + " 需要 1 个参数，实际收到 " + args.size() + " 个");
        }
        double x = args.get(0).toDouble();
        checkDomain(x);
        double input = angleSensitive ? AngleUnits.toRadians(x, angleUnit) : x;
        double raw = implementation.applyAsDouble(input);
        double output = angleSensitive && isInverseTrigonometric()
                ? AngleUnits.fromRadians(raw, angleUnit)
                : raw;
        Numbers.requireFinite(output, functionName);
        return numbers.floating(output);
    }

    private boolean isInverseTrigonometric() {
        return this == ASIN || this == ACOS || this == ATAN;
    }

    private void checkDomain(double x) {
        boolean ok = switch (domain) {
            case ANY -> true;
            case NON_NEGATIVE -> x >= 0;
            case POSITIVE -> x > 0;
            case UNIT_INTERVAL -> x >= -1 && x <= 1;
            case AT_LEAST_ONE -> x >= 1;
            case OPEN_UNIT_INTERVAL -> x > -1 && x < 1;
        };
        if (!ok) {
            throw CalcException.of(CalcErrorCode.DOMAIN_ERROR,
                    functionName + " 的定义域不允许 " + x);
        }
    }
}
```

- [ ] **Step 6: 创建 AngleUnits 辅助类**

创建 `src/main/java/com/wysjwxm/calculator/core/function/AngleUnits.java`：

```java
package com.wysjwxm.calculator.core.function;

import com.wysjwxm.calculator.core.AngleUnit;

/**
 * 角度单位换算。仅三角相关函数使用。
 */
final class AngleUnits {

    private AngleUnits() {
    }

    static double toRadians(double x, AngleUnit unit) {
        return unit == AngleUnit.DEGREE ? Math.toRadians(x) : x;
    }

    static double fromRadians(double x, AngleUnit unit) {
        return unit == AngleUnit.DEGREE ? Math.toDegrees(x) : x;
    }
}
```

- [ ] **Step 7: 创建 BinaryFunction**

创建 `src/main/java/com/wysjwxm/calculator/core/function/BinaryFunction.java`：

```java
package com.wysjwxm.calculator.core.function;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;

import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * 5 个二元函数 —— 只收录无法用中缀运算符表达的运算（见 spec §6.5）。
 * 幂与取余有 ^ 与 % 两种中缀写法，因此不在此注册。
 */
public enum BinaryFunction implements MathFunction {

    HYPOT("hypot", false, Math::hypot),
    MAX("max", false, Math::max),
    MIN("min", false, Math::min),
    ATAN2("atan2", true, Math::atan2),
    LOG("log", false, (x, base) -> Math.log(x) / Math.log(base));

    private final String functionName;
    private final boolean angleSensitive;
    private final DoubleBinaryOperator implementation;

    BinaryFunction(String functionName, boolean angleSensitive, DoubleBinaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.implementation = implementation;
    }

    @Override
    public String name() {
        return functionName;
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public boolean angleSensitive() {
        return angleSensitive;
    }

    @Override
    public CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers) {
        if (args.size() != 2) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    functionName + " 需要 2 个参数，实际收到 " + args.size() + " 个");
        }
        double x = args.get(0).toDouble();
        double y = args.get(1).toDouble();
        checkDomain(x, y);

        double raw;
        if (this == ATAN2) {
            // atan2 的返回值是角度，需要对结果做单位换算（输入 y/x 本身无量纲）
            raw = AngleUnits.fromRadians(Math.atan2(x, y), angleUnit);
        } else {
            raw = implementation.applyAsDouble(x, y);
        }
        Numbers.requireFinite(raw, functionName);
        return numbers.floating(raw);
    }

    private void checkDomain(double x, double y) {
        if (this == LOG) {
            if (x <= 0) {
                throw CalcException.of(CalcErrorCode.DOMAIN_ERROR, "log 的真数必须为正，实际为 " + x);
            }
            if (y <= 0 || y == 1) {
                throw CalcException.of(CalcErrorCode.DOMAIN_ERROR,
                        "log 的底数必须为正且不等于 1，实际为 " + y);
            }
        }
    }
}
```

- [ ] **Step 8: 创建 FunctionRegistry**

创建 `src/main/java/com/wysjwxm/calculator/core/function/FunctionRegistry.java`：

```java
package com.wysjwxm.calculator.core.function;

import com.wysjwxm.calculator.core.Constants;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 函数名 → 实现的查表，同时是「变量名禁用集合」的来源之一。
 */
public final class FunctionRegistry {

    private final Map<String, MathFunction> byName = new LinkedHashMap<>();

    public FunctionRegistry() {
        for (UnaryFunction f : UnaryFunction.values()) {
            byName.put(f.name(), f);
        }
        for (BinaryFunction f : BinaryFunction.values()) {
            byName.put(f.name(), f);
        }
    }

    public Optional<MathFunction> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public Set<String> names() {
        return byName.keySet();
    }

    public List<String> unaryNames() {
        return Arrays.stream(UnaryFunction.values()).map(UnaryFunction::name).toList();
    }

    public List<String> binaryNames() {
        return Arrays.stream(BinaryFunction.values()).map(BinaryFunction::name).toList();
    }

    /**
     * 变量名禁用集合 = 函数名 ∪ 保留常量名（见 spec §6.4）。
     * 由本类统一提供，避免校验逻辑在多个入口各写一份。
     */
    public Set<String> reservedNames() {
        Set<String> reserved = new HashSet<>(byName.keySet());
        reserved.addAll(Constants.names());
        return Set.copyOf(reserved);
    }
}
```

- [ ] **Step 9: 运行测试确认通过**

运行：`mvn -q test -Dtest='ConstantsTest,FunctionRegistryTest'`

预期：全部 PASS（ConstantsTest 3 个 + FunctionRegistryTest 15 个）。

- [ ] **Step 10: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/Constants.java \
        src/main/java/com/wysjwxm/calculator/core/function/ \
        src/test/java/com/wysjwxm/calculator/core/ConstantsTest.java \
        src/test/java/com/wysjwxm/calculator/core/function/
git commit -m "feat: 保留常量与函数注册表（23 一元 + 5 二元），含定义域校验"
```

---

### Task 8: 求值器

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/core/eval/EvaluationContext.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/eval/Evaluator.java`
- Create: `src/main/java/com/wysjwxm/calculator/core/eval/AstInspection.java`
- Test: `src/test/java/com/wysjwxm/calculator/core/eval/EvaluatorTest.java`

**Interfaces:**
- Consumes: 全部 core 组件（Task 1–7）
- Produces:
  - `@FunctionalInterface interface EvaluationContext`：`Optional<CalcNumber> lookup(String name)`，静态 `EvaluationContext of(Map<String, CalcNumber> variables)`、`EvaluationContext empty()`
  - `Evaluator`，构造器 `Evaluator(FunctionRegistry registry, Numbers numbers)`，方法 `CalcNumber evaluate(Expression expr, AngleUnit angleUnit, EvaluationContext context)`
  - `AstInspection.usesAngleSensitiveFunction(Expression expr, FunctionRegistry registry)` → `boolean`（供 Task 11 决定历史记录里 `angleUnit` 是否为 null）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/core/eval/EvaluatorTest.java`：

```java
package com.wysjwxm.calculator.core.eval;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.core.parser.ExpressionParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluatorTest {

    private final ExpressionParser parser = new ExpressionParser();
    private final Evaluator evaluator = new Evaluator(new FunctionRegistry(), new Numbers(34));

    private CalcNumber eval(String expression) {
        return evaluator.evaluate(parser.parse(expression), AngleUnit.DEGREE, EvaluationContext.empty());
    }

    private CalcNumber eval(String expression, Map<String, CalcNumber> variables) {
        return evaluator.evaluate(parser.parse(expression), AngleUnit.DEGREE,
                EvaluationContext.of(variables));
    }

    private double num(String expression) {
        return eval(expression).toDouble();
    }

    // ---------- 算术与优先级（spec §6.3 三条规则端到端验证） ----------

    @Test
    void arithmeticFollowsPrecedence() {
        assertThat(num("1+2*3")).isEqualTo(7.0);
        assertThat(num("(1+2)*3")).isEqualTo(9.0);
    }

    @Test
    void powerIsRightAssociative() {
        // 2^3^2 == 2^9 == 512，若左结合会得到 64
        assertThat(num("2^3^2")).isEqualTo(512.0);
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        // -2^2 == -4，而不是 4
        assertThat(num("-2^2")).isEqualTo(-4.0);
    }

    @Test
    void factorialBindsTighterThanAddition() {
        // 3! + 1 == 7
        assertThat(num("3!+1")).isEqualTo(7.0);
    }

    @Test
    void moduloWorks() {
        assertThat(num("7%3")).isEqualTo(1.0);
    }

    @Test
    void unaryPlusIsIdentity() {
        assertThat(num("+5")).isEqualTo(5.0);
    }

    // ---------- 精度端到端 ----------

    @Test
    void decimalArithmeticStaysExactEndToEnd() {
        CalcNumber result = eval("0.1+0.2");
        assertThat(result.isExact()).isTrue();
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("0.3"));
    }

    @Test
    void sineOfThirtyDegreesIsHalf() {
        assertThat(num("sin(30)")).isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void specAcceptanceExpression() {
        // spec §14 验收标准第 3 条
        assertThat(num("1 + 2 * sin(30) ^ 2")).isCloseTo(1.5,
                org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 变量与常量 ----------

    @Test
    void resolvesVariablesFromContext() {
        assertThat(eval("x*2", Map.of("x", new com.wysjwxm.calculator.core.number.DecimalNumber(
                new BigDecimal("5")))).toDouble()).isEqualTo(10.0);
    }

    @Test
    void resolvesBuiltInConstants() {
        assertThat(num("pi")).isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(num("e")).isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void variableShadowsNothingButContextWinsOverConstantsIsImpossible() {
        // 常量是保留名，变量容器里放不进 pi；此处确认 pi 始终解析为常量
        assertThat(num("pi + 0")).isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void undefinedVariableThrows() {
        assertThatThrownBy(() -> eval("y + 1"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_VARIABLE);
    }

    // ---------- 函数分派 ----------

    @Test
    void dispatchesToFunctions() {
        assertThat(num("sqrt(16)")).isEqualTo(4.0);
        assertThat(num("max(3,4)")).isEqualTo(4.0);
        assertThat(num("log(8,2)")).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(num("abs(-3)")).isEqualTo(3.0);
    }

    @Test
    void unknownFunctionThrows() {
        assertThatThrownBy(() -> eval("nope(1)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_FUNCTION);
    }

    @Test
    void nestedCallsEvaluate() {
        assertThat(num("sqrt(sqrt(16))")).isEqualTo(2.0);
    }

    // ---------- 角度单位 ----------

    @Test
    void radianModeChangesTrigResults() {
        CalcNumber radian = evaluator.evaluate(parser.parse("sin(30)"), AngleUnit.RADIAN,
                EvaluationContext.empty());
        assertThat(radian.toDouble()).isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 定义域与异常 ----------

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> eval("1/0"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    void domainErrorPropagates() {
        assertThatThrownBy(() -> eval("sqrt(-1)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void overflowThrowsNonFinite() {
        assertThatThrownBy(() -> eval("9^9^9"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).code())
                        .isIn(CalcErrorCode.NON_FINITE_RESULT, CalcErrorCode.DOMAIN_ERROR));
    }

    // ---------- AST 内省 ----------

    @Test
    void detectsAngleSensitiveUsage() {
        FunctionRegistry registry = new FunctionRegistry();
        assertThat(AstInspection.usesAngleSensitiveFunction(parser.parse("sin(30)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parser.parse("1+2"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parser.parse("sqrt(2)"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parser.parse("1+sin(2)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parser.parse("max(sin(1),2)"), registry)).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=EvaluatorTest`

预期：编译失败，`Evaluator` 不存在。

- [ ] **Step 3: 创建 EvaluationContext**

创建 `src/main/java/com/wysjwxm/calculator/core/eval/EvaluationContext.java`：

```java
package com.wysjwxm.calculator.core.eval;

import com.wysjwxm.calculator.core.number.CalcNumber;

import java.util.Map;
import java.util.Optional;

/**
 * 求值期的变量来源。core 通过这个接口拿到变量，从而不必认识 store 层 ——
 * 依赖方向因此保持单向。
 */
@FunctionalInterface
public interface EvaluationContext {

    Optional<CalcNumber> lookup(String name);

    static EvaluationContext of(Map<String, CalcNumber> variables) {
        Map<String, CalcNumber> snapshot = Map.copyOf(variables);
        return name -> Optional.ofNullable(snapshot.get(name));
    }

    static EvaluationContext empty() {
        return name -> Optional.empty();
    }
}
```

- [ ] **Step 4: 创建 Evaluator**

创建 `src/main/java/com/wysjwxm/calculator/core/eval/Evaluator.java`：

```java
package com.wysjwxm.calculator.core.eval;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.Constants;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.function.MathFunction;
import com.wysjwxm.calculator.core.lexer.TokenType;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.core.parser.ast.BinaryExpr;
import com.wysjwxm.calculator.core.parser.ast.CallExpr;
import com.wysjwxm.calculator.core.parser.ast.Expression;
import com.wysjwxm.calculator.core.parser.ast.LiteralExpr;
import com.wysjwxm.calculator.core.parser.ast.PostfixExpr;
import com.wysjwxm.calculator.core.parser.ast.UnaryExpr;
import com.wysjwxm.calculator.core.parser.ast.VariableExpr;

import java.util.ArrayList;
import java.util.List;

/**
 * AST → CalcNumber。
 *
 * <p>sealed 的 Expression 让这个 switch 穷尽 —— 日后新增节点类型时，
 * 编译器会强制这里同步更新，不会有节点被静默漏掉。
 */
public final class Evaluator {

    private final FunctionRegistry functions;
    private final Numbers numbers;

    public Evaluator(FunctionRegistry functions, Numbers numbers) {
        this.functions = functions;
        this.numbers = numbers;
    }

    public CalcNumber evaluate(Expression expr, AngleUnit angleUnit, EvaluationContext context) {
        return switch (expr) {
            case LiteralExpr literal -> literal.value();
            case VariableExpr variable -> resolveVariable(variable, context);
            case UnaryExpr unary -> applyUnary(unary, angleUnit, context);
            case PostfixExpr postfix -> applyPostfix(postfix, angleUnit, context);
            case BinaryExpr binary -> applyBinary(binary, angleUnit, context);
            case CallExpr call -> applyCall(call, angleUnit, context);
        };
    }

    private CalcNumber resolveVariable(VariableExpr variable, EvaluationContext context) {
        // 变量优先于常量：虽然禁用集合保证了同名不可能发生，这里仍按
        // 「显式传入 > 语言内置」的直觉顺序解析
        return context.lookup(variable.name())
                .or(() -> Constants.lookup(variable.name()))
                .orElseThrow(() -> CalcException.of(CalcErrorCode.UNKNOWN_VARIABLE,
                        "未定义的变量或常量: " + variable.name()));
    }

    private CalcNumber applyUnary(UnaryExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber operand = evaluate(expr.operand(), angleUnit, context);
        // 一元 +/- ：+ 是恒等，- 是取负
        if (expr.operator().symbol().equals("-")) {
            return numbers.negate(operand);
        }
        return operand;
    }

    private CalcNumber applyPostfix(PostfixExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber operand = evaluate(expr.operand(), angleUnit, context);
        if (expr.operator().symbol().equals("!")) {
            return numbers.factorial(operand);
        }
        throw CalcException.of(CalcErrorCode.INTERNAL_ERROR,
                "未知的后缀算子: " + expr.operator().symbol());
    }

    private CalcNumber applyBinary(BinaryExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber left = evaluate(expr.left(), angleUnit, context);
        CalcNumber right = evaluate(expr.right(), angleUnit, context);
        // 算子分派依据 OperatorTable 记录的中缀符号，避免与 TokenType 二次映射
        String symbol = expr.operator().symbol();
        return switch (symbol) {
            case "+" -> numbers.add(left, right);
            case "-" -> numbers.subtract(left, right);
            case "*" -> numbers.multiply(left, right);
            case "/" -> numbers.divide(left, right);
            case "%" -> numbers.modulo(left, right);
            case "^" -> numbers.power(left, right);
            default -> throw CalcException.of(CalcErrorCode.INTERNAL_ERROR, "未知的中缀算子: " + symbol);
        };
    }

    private CalcNumber applyCall(CallExpr call, AngleUnit angleUnit, EvaluationContext context) {
        MathFunction function = functions.find(call.functionName())
                .orElseThrow(() -> CalcException.at(CalcErrorCode.UNKNOWN_FUNCTION,
                        "未知的函数: " + call.functionName(), call.position()));
        if (call.arguments().size() != function.arity()) {
            throw CalcException.at(CalcErrorCode.INVALID_REQUEST,
                    function.name() + " 需要 " + function.arity() + " 个参数，实际收到 "
                            + call.arguments().size() + " 个", call.position());
        }
        List<CalcNumber> args = new ArrayList<>(call.arguments().size());
        for (Expression argument : call.arguments()) {
            args.add(evaluate(argument, angleUnit, context));
        }
        return function.apply(args, angleUnit, numbers);
    }
}
```

- [ ] **Step 5: 创建 AstInspection**

创建 `src/main/java/com/wysjwxm/calculator/core/eval/AstInspection.java`：

```java
package com.wysjwxm.calculator.core.eval;

import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.parser.ast.BinaryExpr;
import com.wysjwxm.calculator.core.parser.ast.CallExpr;
import com.wysjwxm.calculator.core.parser.ast.Expression;
import com.wysjwxm.calculator.core.parser.ast.LiteralExpr;
import com.wysjwxm.calculator.core.parser.ast.PostfixExpr;
import com.wysjwxm.calculator.core.parser.ast.UnaryExpr;
import com.wysjwxm.calculator.core.parser.ast.VariableExpr;

/**
 * AST 静态内省。目前只用于判断历史记录里的 angleUnit 是否有意义
 * —— spec §7.3 规定非三角记录该字段为 null。
 */
public final class AstInspection {

    private AstInspection() {
    }

    /** 表达式中是否用到了受角度单位影响的函数。 */
    public static boolean usesAngleSensitiveFunction(Expression expr, FunctionRegistry registry) {
        return switch (expr) {
            case LiteralExpr ignored -> false;
            case VariableExpr ignored -> false;
            case UnaryExpr u -> usesAngleSensitiveFunction(u.operand(), registry);
            case PostfixExpr p -> usesAngleSensitiveFunction(p.operand(), registry);
            case BinaryExpr b -> usesAngleSensitiveFunction(b.left(), registry)
                    || usesAngleSensitiveFunction(b.right(), registry);
            case CallExpr c -> registry.find(c.functionName())
                    .map(f -> f.angleSensitive())
                    .orElse(false)
                    || c.arguments().stream()
                            .anyMatch(arg -> usesAngleSensitiveFunction(arg, registry));
        };
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

运行：`mvn -q test -Dtest=EvaluatorTest`

预期：全部 PASS。

**若 `overflowThrowsNonFinite` 因 `9^9^9` 走精确路径抛 `ArithmeticException` 而失败**：检查 `Numbers.power` 中 `pow(intValueExact())` 是否被 `try/catch ArithmeticException` 包住并降级到 double 路径。`9^9^9` 的指数 `9^9 = 387420489` 能被 `intValueExact` 接受，于是 `BigDecimal.pow(387420489)` 会抛 `ArithmeticException`（结果超出可表示范围），必须被捕获并降级。

- [ ] **Step 7: 运行全部 core 测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/core/eval/ \
        src/test/java/com/wysjwxm/calculator/core/eval/
git commit -m "feat: 求值器与 AST 内省"
```

---

### Task 9: 变量存储

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/store/VariableRecord.java`
- Create: `src/main/java/com/wysjwxm/calculator/store/VariableStore.java`
- Create: `src/main/java/com/wysjwxm/calculator/store/InMemoryVariableStore.java`
- Test: `src/test/java/com/wysjwxm/calculator/store/InMemoryVariableStoreTest.java`

**Interfaces:**
- Consumes: `CalcNumber`（Task 3）
- Produces:
  - `record VariableRecord(String name, CalcNumber value, Instant createdAt, Instant updatedAt)`
  - `interface VariableStore`：`VariableRecord put(String name, CalcNumber value)`、`Optional<VariableRecord> find(String name)`、`List<VariableRecord> findAll()`（按 name 升序）、`boolean delete(String name)`、`int size()`
  - `InMemoryVariableStore implements VariableStore`，无参构造器，标注 `@Repository`（**注意**：`store` 包可以依赖 Spring，只有 `core` 不行）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/store/InMemoryVariableStoreTest.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.DecimalNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVariableStoreTest {

    private final InMemoryVariableStore store = new InMemoryVariableStore();
    private final Numbers numbers = new Numbers(34);

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    @Test
    void putThenFind() {
        store.put("x", num("5"));
        assertThat(store.find("x").orElseThrow().value().toDecimal()).isEqualByComparingTo("5");
    }

    @Test
    void unknownNameIsEmpty() {
        assertThat(store.find("nope")).isEmpty();
    }

    @Test
    void putOverwritesAndKeepsCreatedAt() {
        VariableRecord first = store.put("x", num("5"));
        VariableRecord second = store.put("x", num("6"));
        assertThat(second.value().toDecimal()).isEqualByComparingTo("6");
        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void findAllIsSortedByName() {
        store.put("zeta", num("1"));
        store.put("alpha", num("2"));
        store.put("mid", num("3"));
        assertThat(store.findAll()).extracting(VariableRecord::name)
                .containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void deleteReportsWhetherAnythingWasRemoved() {
        store.put("x", num("5"));
        assertThat(store.delete("x")).isTrue();
        assertThat(store.delete("x")).isFalse();
        assertThat(store.find("x")).isEmpty();
    }

    @Test
    void concurrentWritesToDistinctKeysAllSurvive() throws Exception {
        int threads = 8;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        store.put("k" + id + "_" + i, num(String.valueOf(i)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(store.size()).isEqualTo(threads * perThread);
    }

    @Test
    void concurrentOverwritesOfSameKeyLeaveExactlyOneValue() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        store.put("shared", num(String.valueOf(id)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(store.size()).isEqualTo(1);
        assertThat(store.find("shared")).isPresent();
    }

    @Test
    void findAllIsDefensivelyCopiedAgainstLaterMutation() {
        store.put("x", num("5"));
        List<VariableRecord> snapshot = store.findAll();
        assertThat(snapshot).hasSize(1);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=InMemoryVariableStoreTest`

预期：编译失败，`InMemoryVariableStore` 不存在。

- [ ] **Step 3: 创建记录与接口**

创建 `src/main/java/com/wysjwxm/calculator/store/VariableRecord.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.core.number.CalcNumber;

import java.time.Instant;

public record VariableRecord(String name, CalcNumber value, Instant createdAt, Instant updatedAt) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/store/VariableStore.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.core.number.CalcNumber;

import java.util.List;
import java.util.Optional;

/**
 * 变量存储抽象。当前唯一实现是内存版（题目禁止外部存储），
 * 但保留接口让「未来可替换持久化实现」成为真实成立的陈述。
 */
public interface VariableStore {

    /** 幂等 upsert：已存在则整体替换，createdAt 保持不变。 */
    VariableRecord put(String name, CalcNumber value);

    Optional<VariableRecord> find(String name);

    /** 按 name 字典序升序返回全部。 */
    List<VariableRecord> findAll();

    boolean delete(String name);

    int size();
}
```

- [ ] **Step 4: 创建 InMemoryVariableStore**

创建 `src/main/java/com/wysjwxm/calculator/store/InMemoryVariableStore.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.core.number.CalcNumber;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 变量的内存实现。
 *
 * <p>用 ConcurrentHashMap 而非加锁：变量是「按 name 独立」的数据，
 * 单键操作天然原子，无锁读让高频的表达式求值不必等待。
 */
@Repository
public class InMemoryVariableStore implements VariableStore {

    private final ConcurrentMap<String, VariableRecord> variables = new ConcurrentHashMap<>();

    @Override
    public VariableRecord put(String name, CalcNumber value) {
        Instant now = Instant.now();
        // compute 让「读旧 createdAt + 写新记录」成为单键原子操作
        return variables.compute(name, (key, existing) -> new VariableRecord(
                key,
                value,
                existing == null ? now : existing.createdAt(),
                now));
    }

    @Override
    public Optional<VariableRecord> find(String name) {
        return Optional.ofNullable(variables.get(name));
    }

    @Override
    public List<VariableRecord> findAll() {
        return variables.values().stream()
                .sorted(Comparator.comparing(VariableRecord::name))
                .toList();
    }

    @Override
    public boolean delete(String name) {
        return variables.remove(name) != null;
    }

    @Override
    public int size() {
        return variables.size();
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=InMemoryVariableStoreTest`

预期：8 个测试全部 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/store/VariableRecord.java \
        src/main/java/com/wysjwxm/calculator/store/VariableStore.java \
        src/main/java/com/wysjwxm/calculator/store/InMemoryVariableStore.java \
        src/test/java/com/wysjwxm/calculator/store/InMemoryVariableStoreTest.java
git commit -m "feat: 变量存储 — ConcurrentHashMap 无锁读的内存实现"
```

---

### Task 10: 历史存储

实现带容量上限的 FIFO 历史。**淘汰策略是 FIFO 而非 LRU**，理由见 spec §8。本任务是全项目唯一需要写锁的地方。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/store/HistoryRecord.java`
- Create: `src/main/java/com/wysjwxm/calculator/store/PageResult.java`
- Create: `src/main/java/com/wysjwxm/calculator/store/CalculationHistoryStore.java`
- Create: `src/main/java/com/wysjwxm/calculator/store/InMemoryCalculationHistoryStore.java`
- Test: `src/test/java/com/wysjwxm/calculator/store/InMemoryCalculationHistoryStoreTest.java`

**Interfaces:**
- Consumes: `CalcNumber`（Task 3）、`AngleUnit`（Task 1）
- Produces:
  - `record HistoryRecord(long id, String expression, CalcNumber result, AngleUnit angleUnit, double elapsedMs, Instant createdAt)`
  - `record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext)`
  - `interface CalculationHistoryStore`：`HistoryRecord append(String expression, CalcNumber result, AngleUnit angleUnit, double elapsedMs)`、`Optional<HistoryRecord> find(long id)`、`PageResult<HistoryRecord> findPage(int page, int size)`、`int clear()`、`int size()`
  - `InMemoryCalculationHistoryStore implements CalculationHistoryStore`，构造器 `InMemoryCalculationHistoryStore(CalculatorProperties properties)`，标注 `@Repository`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/store/InMemoryCalculationHistoryStoreTest.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCalculationHistoryStoreTest {

    private final Numbers numbers = new Numbers(34);

    private CalculatorProperties props(int capacity) {
        return new CalculatorProperties(AngleUnit.DEGREE, 1000, 34, capacity);
    }

    private HistoryRecord append(CalculationHistoryStore store, String expr) {
        return store.append(expr, numbers.of(1L), AngleUnit.DEGREE, 0.5);
    }

    @Test
    void appendAssignsMonotonicIdsStartingAtOne() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        assertThat(append(store, "a").id()).isEqualTo(1L);
        assertThat(append(store, "b").id()).isEqualTo(2L);
        assertThat(append(store, "c").id()).isEqualTo(3L);
    }

    @Test
    void findByIdReturnsRecord() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        HistoryRecord appended = append(store, "sin(30)");
        assertThat(store.find(appended.id())).contains(appended);
    }

    @Test
    void findUnknownIdIsEmpty() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        assertThat(store.find(999L)).isEmpty();
    }

    @Test
    void pageIsNewestFirst() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        append(store, "first");
        append(store, "second");
        append(store, "third");

        PageResult<HistoryRecord> page = store.findPage(0, 10);
        assertThat(page.items()).extracting(HistoryRecord::expression)
                .containsExactly("third", "second", "first");
    }

    @Test
    void pagingSlicesCorrectly() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        for (int i = 1; i <= 5; i++) {
            append(store, "e" + i);
        }
        PageResult<HistoryRecord> firstPage = store.findPage(0, 2);
        assertThat(firstPage.items()).extracting(HistoryRecord::expression).containsExactly("e5", "e4");
        assertThat(firstPage.page()).isEqualTo(0);
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();

        assertThat(store.findPage(2, 2).items()).extracting(HistoryRecord::expression)
                .containsExactly("e1");
        assertThat(store.findPage(2, 2).hasNext()).isFalse();
    }

    @Test
    void pageBeyondEndReturnsEmptyItems() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        append(store, "only");
        PageResult<HistoryRecord> page = store.findPage(5, 10);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void emptyStorePagesCleanly() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        PageResult<HistoryRecord> page = store.findPage(0, 10);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    // ---------- FIFO 容量淘汰 ----------

    @Test
    void capacityEvictsOldestFirst() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(3));
        append(store, "e1");
        append(store, "e2");
        append(store, "e3");
        append(store, "e4");

        assertThat(store.size()).isEqualTo(3);
        assertThat(store.findPage(0, 10).items()).extracting(HistoryRecord::expression)
                .containsExactly("e4", "e3", "e2");
        // 最旧的 e1 已被淘汰
        assertThat(store.find(1L)).isEmpty();
        // 淘汰不重置 id 序列
        assertThat(store.findPage(0, 1).items().get(0).id()).isEqualTo(4L);
    }

    @Test
    void zeroCapacityMeansUnbounded() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(0));
        for (int i = 0; i < 50; i++) {
            append(store, "e" + i);
        }
        assertThat(store.size()).isEqualTo(50);
    }

    @Test
    void negativeCapacityMeansUnbounded() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(-1));
        for (int i = 0; i < 50; i++) {
            append(store, "e" + i);
        }
        assertThat(store.size()).isEqualTo(50);
    }

    @Test
    void clearRemovesEverythingAndReportsCount() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        append(store, "a");
        append(store, "b");
        assertThat(store.clear()).isEqualTo(2);
        assertThat(store.size()).isZero();
        assertThat(store.findPage(0, 10).totalElements()).isZero();
    }

    @Test
    void clearOnEmptyStoreReturnsZero() {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(100));
        assertThat(store.clear()).isZero();
    }

    // ---------- 并发 ----------

    @Test
    void concurrentAppendsNoneLostWhenUnbounded() throws Exception {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(0));
        int threads = 8;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        append(store, "x");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(store.size()).isEqualTo(threads * perThread);
        // id 必须唯一且连续 —— 证明 id 分配没有竞态
        assertThat(store.findPage(0, store.size()).items())
                .extracting(HistoryRecord::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void concurrentAppendsRespectCapacityExactly() throws Exception {
        int capacity = 500;
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(capacity));
        int threads = 8;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        append(store, "x");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        // 「追加 + 淘汰」必须原子，否则并发下 size 会瞬时超过 capacity
        assertThat(store.size()).isEqualTo(capacity);
    }

    @Test
    void concurrentReadsAndWritesDoNotFail() throws Exception {
        CalculationHistoryStore store = new InMemoryCalculationHistoryStore(props(200));
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final boolean writer = t % 2 == 0;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 1000; i++) {
                        if (writer) {
                            append(store, "w");
                        } else {
                            store.findPage(0, 10);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(store.size()).isLessThanOrEqualTo(200);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

运行：`mvn -q test -Dtest=InMemoryCalculationHistoryStoreTest`

预期：编译失败，`InMemoryCalculationHistoryStore` 不存在。

- [ ] **Step 3: 创建记录与接口**

创建 `src/main/java/com/wysjwxm/calculator/store/HistoryRecord.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;

import java.time.Instant;

/**
 * @param angleUnit 仅当表达式用到了受角度单位影响的函数时才有值，否则为 null
 */
public record HistoryRecord(long id, String expression, CalcNumber result, AngleUnit angleUnit,
                            double elapsedMs, Instant createdAt) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/store/PageResult.java`：

```java
package com.wysjwxm.calculator.store;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements,
                            int totalPages, boolean hasNext) {

    public PageResult {
        items = List.copyOf(items);
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/store/CalculationHistoryStore.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;

import java.util.Optional;

public interface CalculationHistoryStore {

    HistoryRecord append(String expression, CalcNumber result, AngleUnit angleUnit, double elapsedMs);

    Optional<HistoryRecord> find(long id);

    /** 按 id 倒序（最新在前）分页。 */
    PageResult<HistoryRecord> findPage(int page, int size);

    int clear();

    int size();
}
```

- [ ] **Step 4: 创建 InMemoryCalculationHistoryStore**

创建 `src/main/java/com/wysjwxm/calculator/store/InMemoryCalculationHistoryStore.java`：

```java
package com.wysjwxm.calculator.store;

import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 历史的内存实现，带容量上限。
 *
 * <p><b>并发策略</b>：ArrayDeque + ReentrantReadWriteLock。用锁而非并发集合，
 * 是因为「追加 + 淘汰最旧」必须是一个原子步骤 —— ConcurrentLinkedDeque 无法
 * 保证淘汰后 size 精确等于容量。
 *
 * <p><b>淘汰策略是 FIFO，不是 LRU</b>（见 spec §8）：历史只追加、不被复用，
 * 记录的价值随时间单调递减（最新最有用），FIFO 与这个语义天然吻合。而 LRU
 * 需要在每次读时更新访问元数据，会把读操作退化为写操作，与下面「读锁可并发」
 * 的设计直接冲突 —— 而分页查询正是最热的路径。
 */
@Repository
public class InMemoryCalculationHistoryStore implements CalculationHistoryStore {

    private final Deque<HistoryRecord> records = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong sequence = new AtomicLong(0);
    /** <= 0 表示不限制容量。 */
    private final int capacity;

    public InMemoryCalculationHistoryStore(CalculatorProperties properties) {
        this.capacity = properties.historyCapacity();
    }

    @Override
    public HistoryRecord append(String expression, CalcNumber result, AngleUnit angleUnit, double elapsedMs) {
        HistoryRecord record = new HistoryRecord(
                sequence.incrementAndGet(), expression, result, angleUnit, elapsedMs, Instant.now());
        lock.writeLock().lock();
        try {
            records.addFirst(record);
            if (capacity > 0) {
                while (records.size() > capacity) {
                    records.removeLast();
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
        return record;
    }

    @Override
    public Optional<HistoryRecord> find(long id) {
        lock.readLock().lock();
        try {
            return records.stream().filter(r -> r.id() == id).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PageResult<HistoryRecord> findPage(int page, int size) {
        lock.readLock().lock();
        try {
            long total = records.size();
            List<HistoryRecord> snapshot = new ArrayList<>(records);
            int from = page * size;
            List<HistoryRecord> items = from >= snapshot.size()
                    ? List.of()
                    : List.copyOf(snapshot.subList(from, Math.min(from + size, snapshot.size())));
            int totalPages = size > 0 ? (int) ((total + size - 1) / size) : 0;
            boolean hasNext = (long) (page + 1) * size < total;
            return new PageResult<>(items, page, size, total, totalPages, hasNext);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int clear() {
        lock.writeLock().lock();
        try {
            int removed = records.size();
            records.clear();
            return removed;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return records.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=InMemoryCalculationHistoryStoreTest`

预期：14 个测试全部 PASS。

**若 `concurrentAppendsRespectCapacityExactly` 失败**：说明「追加 + 淘汰」没有放在同一个写锁临界区内。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/store/HistoryRecord.java \
        src/main/java/com/wysjwxm/calculator/store/PageResult.java \
        src/main/java/com/wysjwxm/calculator/store/CalculationHistoryStore.java \
        src/main/java/com/wysjwxm/calculator/store/InMemoryCalculationHistoryStore.java \
        src/test/java/com/wysjwxm/calculator/store/InMemoryCalculationHistoryStoreTest.java
git commit -m "feat: 历史存储 — 带容量上限的 FIFO 淘汰与读写锁并发"
```

---

### Task 11: 服务层

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/service/VariableService.java`
- Create: `src/main/java/com/wysjwxm/calculator/service/HistoryService.java`
- Create: `src/main/java/com/wysjwxm/calculator/service/CalculationService.java`
- Test: `src/test/java/com/wysjwxm/calculator/service/VariableServiceTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/service/CalculationServiceTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/service/HistoryServiceTest.java`

**Interfaces:**
- Consumes: `VariableStore`（Task 9）、`CalculationHistoryStore`、`PageResult`（Task 10）、全部 core 组件（Task 1–8）、`CalculatorProperties`（Task 1）
- Produces:
  - `VariableService`：`VariableRecord put(String name, CalcNumber value)`、`VariableRecord get(String name)`、`List<VariableRecord> list()`、`void delete(String name)`、**`void validateVariableName(String name)`**（供 `CalculationService` 复用，保证两个入口同一套校验）
  - `HistoryService`：`PageResult<HistoryRecord> page(int page, int size)`、`HistoryRecord get(long id)`、`int clear()`
  - `CalculationService`：`CalculationOutcome calculate(String expression, AngleUnit angleUnit, Map<String, CalcNumber> requestVariables)`
  - `record CalculationOutcome(HistoryRecord record)` — 携带落库后的记录，`record.expression()` / `record.result()` 等直接可用

- [ ] **Step 1: 写变量服务的失败测试**

创建 `src/test/java/com/wysjwxm/calculator/service/VariableServiceTest.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.DecimalNumber;
import com.wysjwxm.calculator.store.InMemoryVariableStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableServiceTest {

    private final VariableService service = new VariableService(new InMemoryVariableStore());

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    @Test
    void putAndGet() {
        service.put("x", num("5"));
        assertThat(service.get("x").value().toDecimal()).isEqualByComparingTo("5");
    }

    @Test
    void getUnknownThrowsNotFound() {
        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.VARIABLE_NOT_FOUND);
    }

    @Test
    void putIsIdempotentUpsert() {
        service.put("x", num("5"));
        service.put("x", num("6"));
        assertThat(service.list()).hasSize(1);
        assertThat(service.get("x").value().toDecimal()).isEqualByComparingTo("6");
    }

    @Test
    void deleteUnknownThrowsNotFound() {
        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.VARIABLE_NOT_FOUND);
    }

    // ---------- 保留名校验（spec §6.4） ----------

    @Test
    void rejectsReservedConstantNames() {
        assertThatThrownBy(() -> service.put("pi", num("3")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
                    assertThat(ce.getMessage()).contains("pi");
                });
        assertThatThrownBy(() -> service.put("e", num("3")))
                .isInstanceOf(CalcException.class);
    }

    @Test
    void rejectsFunctionNames() {
        assertThatThrownBy(() -> service.put("sin", num("3")))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> service.put("log", num("3")))
                .isInstanceOf(CalcException.class);
    }

    @Test
    void acceptsOrdinaryNames() {
        assertThatCode(() -> service.validateVariableName("x")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateVariableName("x_1")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateVariableName("_tmp")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateVariableName("aVeryLongButLegalName")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMalformedNames() {
        assertThatThrownBy(() -> service.validateVariableName("1abc")).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> service.validateVariableName("a-b")).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> service.validateVariableName("a b")).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> service.validateVariableName("")).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> service.validateVariableName(null)).isInstanceOf(CalcException.class);
    }

    @Test
    void rejectsOverlongNames() {
        String tooLong = "x".repeat(65);
        assertThatThrownBy(() -> service.validateVariableName(tooLong)).isInstanceOf(CalcException.class);
        assertThatCode(() -> service.validateVariableName("x".repeat(64))).doesNotThrowAnyException();
    }

    @Test
    void validateVariableNameReturnsInvalidRequestForReserved() {
        assertThatThrownBy(() -> service.validateVariableName("pi"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=VariableServiceTest`

预期：编译失败，`VariableService` 不存在。

- [ ] **Step 3: 创建 VariableService**

创建 `src/main/java/com/wysjwxm/calculator/service/VariableService.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.store.VariableRecord;
import com.wysjwxm.calculator.store.VariableStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class VariableService {

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_NAME_LENGTH = 64;

    private final VariableStore store;
    private final Set<String> reservedNames;

    public VariableService(VariableStore store, FunctionRegistry functionRegistry) {
        this.store = store;
        this.reservedNames = functionRegistry.reservedNames();
    }

    public VariableRecord put(String name, CalcNumber value) {
        validateVariableName(name);
        return store.put(name, value);
    }

    public VariableRecord get(String name) {
        return store.find(name).orElseThrow(() -> CalcException.of(
                CalcErrorCode.VARIABLE_NOT_FOUND, "变量不存在: " + name));
    }

    public List<VariableRecord> list() {
        return store.findAll();
    }

    public void delete(String name) {
        if (!store.delete(name)) {
            throw CalcException.of(CalcErrorCode.VARIABLE_NOT_FOUND, "变量不存在: " + name);
        }
    }

    /**
     * 校验变量名。**存储写入与请求级临时变量两个入口都走这一个方法**，
     * 保证同一表达式在不同入口下对 pi 等保留名的解释一致（spec §6.4）。
     */
    public void validateVariableName(String name) {
        if (name == null || name.isEmpty()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "变量名不能为空");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "变量名长度不得超过 " + MAX_NAME_LENGTH + "，实际为 " + name.length());
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "变量名必须以字母或下划线开头、仅含字母数字下划线: " + name);
        }
        if (reservedNames.contains(name)) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "变量名 " + name + " 是保留名（函数名或内置常量），不可使用");
        }
    }
}
```

- [ ] **Step 4: 运行确认通过**

运行：`mvn -q test -Dtest=VariableServiceTest`

预期：10 个测试 PASS。

- [ ] **Step 5: 写历史服务的失败测试**

创建 `src/test/java/com/wysjwxm/calculator/service/HistoryServiceTest.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.store.InMemoryCalculationHistoryStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryServiceTest {

    private final InMemoryCalculationHistoryStore store =
            new InMemoryCalculationHistoryStore(new CalculatorProperties(AngleUnit.DEGREE, 1000, 34, 100));
    private final HistoryService service = new HistoryService(store);
    private final Numbers numbers = new Numbers(34);

    @Test
    void pageRejectsNegativePage() {
        assertThatThrownBy(() -> service.page(-1, 10))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void pageRejectsSizeOutOfRange() {
        assertThatThrownBy(() -> service.page(0, 0)).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> service.page(0, 101)).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> service.page(0, -5)).isInstanceOf(CalcException.class);
    }

    @Test
    void pageAcceptsBoundarySizes() {
        assertThat(service.page(0, 1).size()).isEqualTo(1);
        assertThat(service.page(0, 100).size()).isEqualTo(100);
    }

    @Test
    void getUnknownThrowsNotFound() {
        assertThatThrownBy(() -> service.get(42L))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.HISTORY_NOT_FOUND);
    }

    @Test
    void getReturnsRecord() {
        var record = store.append("1+1", numbers.of(2L), null, 0.1);
        assertThat(service.get(record.id())).isEqualTo(record);
    }

    @Test
    void clearReportsCount() {
        store.append("a", numbers.of(1L), null, 0.1);
        store.append("b", numbers.of(1L), null, 0.1);
        assertThat(service.clear()).isEqualTo(2);
    }
}
```

- [ ] **Step 6: 运行确认失败**

运行：`mvn -q test -Dtest=HistoryServiceTest`

预期：编译失败，`HistoryService` 不存在。

- [ ] **Step 7: 创建 HistoryService**

创建 `src/main/java/com/wysjwxm/calculator/service/HistoryService.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.store.CalculationHistoryStore;
import com.wysjwxm.calculator.store.HistoryRecord;
import com.wysjwxm.calculator.store.PageResult;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CalculationHistoryStore store;

    public HistoryService(CalculationHistoryStore store) {
        this.store = store;
    }

    public PageResult<HistoryRecord> page(int page, int size) {
        if (page < 0) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "page 不能为负数: " + page);
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "size 必须在 " + MIN_PAGE_SIZE + " 到 " + MAX_PAGE_SIZE + " 之间，实际为 " + size);
        }
        return store.findPage(page, size);
    }

    public HistoryRecord get(long id) {
        return store.find(id).orElseThrow(() -> CalcException.of(
                CalcErrorCode.HISTORY_NOT_FOUND, "历史记录不存在: " + id));
    }

    public int clear() {
        return store.clear();
    }
}
```

- [ ] **Step 8: 运行确认通过**

运行：`mvn -q test -Dtest=HistoryServiceTest`

预期：6 个测试 PASS。

- [ ] **Step 9: 写计算服务的失败测试**

创建 `src/test/java/com/wysjwxm/calculator/service/CalculationServiceTest.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.DecimalNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.store.InMemoryCalculationHistoryStore;
import com.wysjwxm.calculator.store.InMemoryVariableStore;
import com.wysjwxm.calculator.store.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculationServiceTest {

    private final InMemoryCalculationHistoryStore historyStore =
            new InMemoryCalculationHistoryStore(new CalculatorProperties(AngleUnit.DEGREE, 1000, 34, 100));
    private final VariableService variableService = new VariableService(new InMemoryVariableStore(), new FunctionRegistry());
    private final CalculationService service =
            new CalculationService(new FunctionRegistry(), new Numbers(34), historyStore, variableService,
                    new CalculatorProperties(AngleUnit.DEGREE, 1000, 34, 100));

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    private CalculationOutcome calc(String expr) {
        return service.calculate(expr, null, Map.of());
    }

    @Test
    void evaluatesAndRecordsHistory() {
        CalculationOutcome outcome = calc("1+2");
        assertThat(outcome.record().result().toDecimal()).isEqualByComparingTo("3");
        assertThat(outcome.record().expression()).isEqualTo("1+2");
        assertThat(historyStore.size()).isEqualTo(1);
    }

    @Test
    void historyRecordsElapsedTime() {
        assertThat(calc("1+2").record().elapsedMs()).isGreaterThanOrEqualTo(0.0);
    }

    // ---------- 角度单位 ----------

    @Test
    void defaultsToConfiguredAngleUnitWhenRequestOmitsIt() {
        CalculationOutcome outcome = service.calculate("sin(30)", null, Map.of());
        assertThat(outcome.record().result().toDouble())
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(outcome.record().angleUnit()).isEqualTo(AngleUnit.DEGREE);
    }

    @Test
    void requestAngleUnitOverridesConfiguredDefault() {
        CalculationOutcome outcome = service.calculate("sin(30)", AngleUnit.RADIAN, Map.of());
        assertThat(outcome.record().result().toDouble())
                .isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
        assertThat(outcome.record().angleUnit()).isEqualTo(AngleUnit.RADIAN);
    }

    @Test
    void angleUnitIsNullForNonTrigonometricExpressions() {
        // spec §7.3：非三角记录该字段为 null
        assertThat(calc("1+2").record().angleUnit()).isNull();
        assertThat(calc("sqrt(16)").record().angleUnit()).isNull();
        assertThat(calc("1+2").record().angleUnit()).isNull();
    }

    // ---------- 变量 ----------

    @Test
    void resolvesStoredVariables() {
        variableService.put("x", num("5"));
        assertThat(calc("x*2").record().result().toDecimal()).isEqualByComparingTo("10");
    }

    @Test
    void requestVariablesOverrideStoredOnes() {
        variableService.put("x", num("5"));
        CalculationOutcome outcome = service.calculate("x*2", null, Map.of("x", num("7")));
        assertThat(outcome.record().result().toDecimal()).isEqualByComparingTo("14");
    }

    @Test
    void requestVariablesDoNotPersist() {
        service.calculate("x*2", null, Map.of("x", num("7")));
        assertThat(variableService.list()).isEmpty();
    }

    @Test
    void requestVariablesRejectReservedNames() {
        // spec §7.1：请求级变量同样受禁用集合约束，不为「临时、不落库」开口子
        assertThatThrownBy(() -> service.calculate("sin(pi)", null, Map.of("pi", num("3"))))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
                    assertThat(ce.getMessage()).contains("pi");
                });
    }

    @Test
    void requestVariablesRejectFunctionNames() {
        assertThatThrownBy(() -> service.calculate("1+1", null, Map.of("sin", num("3"))))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void constantsRemainIntactDespiteFailedOverrideAttempt() {
        assertThatThrownBy(() -> service.calculate("sin(pi)", null, Map.of("pi", num("3"))))
                .isInstanceOf(CalcException.class);
        // 失败的请求不应写入历史
        assertThat(historyStore.size()).isZero();
        // pi 仍是常量
        assertThat(service.calculate("pi", null, Map.of()).record().result().toDouble())
                .isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
    }

    // ---------- 表达式校验 ----------

    @Test
    void rejectsNullExpression() {
        assertThatThrownBy(() -> service.calculate(null, null, Map.of()))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsBlankExpression() {
        assertThatThrownBy(() -> service.calculate("   ", null, Map.of()))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsOverlongExpression() {
        String tooLong = "1+".repeat(600) + "1";
        assertThatThrownBy(() -> service.calculate(tooLong, null, Map.of()))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void trimsExpressionBeforeEvaluating() {
        assertThat(calc("  1+2  ").record().result().toDecimal()).isEqualByComparingTo("3");
    }

    @Test
    void failedEvaluationDoesNotWriteHistory() {
        assertThatThrownBy(() -> calc("1/0")).isInstanceOf(CalcException.class);
        assertThat(historyStore.size()).isZero();
    }

    @Test
    void parseErrorCarriesPosition() {
        assertThatThrownBy(() -> calc("1+"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(2));
    }

    @Test
    void historyIdsIncrease() {
        HistoryRecord first = calc("1+1").record();
        HistoryRecord second = calc("2+2").record();
        assertThat(second.id()).isGreaterThan(first.id());
    }
}
```

- [ ] **Step 10: 运行确认失败**

运行：`mvn -q test -Dtest=CalculationServiceTest`

预期：编译失败，`CalculationService` 不存在。

- [ ] **Step 11: 创建 CalculationService 与 CalculationOutcome**

创建 `src/main/java/com/wysjwxm/calculator/service/CalculationOutcome.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.store.HistoryRecord;

/**
 * 一次成功求值的结果。携带落库后的历史记录，因此接口层需要的
 * 结果值、耗时、记录 id 都在其中。
 */
public record CalculationOutcome(HistoryRecord record) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/service/CalculationService.java`：

```java
package com.wysjwxm.calculator.service;

import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import com.wysjwxm.calculator.core.eval.AstInspection;
import com.wysjwxm.calculator.core.eval.EvaluationContext;
import com.wysjwxm.calculator.core.eval.Evaluator;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.core.parser.ExpressionParser;
import com.wysjwxm.calculator.core.parser.ast.Expression;
import com.wysjwxm.calculator.store.CalculationHistoryStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CalculationService {

    private final FunctionRegistry functions;
    private final ExpressionParser parser = new ExpressionParser();
    private final Evaluator evaluator;
    private final CalculationHistoryStore historyStore;
    private final VariableService variableService;
    private final CalculatorProperties properties;

    public CalculationService(FunctionRegistry functions, Numbers numbers,
                              CalculationHistoryStore historyStore,
                              VariableService variableService,
                              CalculatorProperties properties) {
        this.functions = functions;
        this.evaluator = new Evaluator(functions, numbers);
        this.historyStore = historyStore;
        this.variableService = variableService;
        this.properties = properties;
    }

    public CalculationOutcome calculate(String expression, AngleUnit angleUnit,
                                        Map<String, CalcNumber> requestVariables) {
        String trimmed = validateExpression(expression);
        Map<String, CalcNumber> variables = validatedRequestVariables(requestVariables);

        AngleUnit effectiveUnit = angleUnit != null ? angleUnit : properties.defaultAngleUnit();

        long startedAt = System.nanoTime();
        Expression ast = parser.parse(trimmed);
        EvaluationContext context = variables.isEmpty()
                ? EvaluationContext.empty()
                : EvaluationContext.of(variables);
        CalcNumber result = evaluator.evaluate(ast, effectiveUnit, context);
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        // angleUnit 仅在表达式真的用到了角度相关函数时才有记录价值（spec §7.3）
        AngleUnit recordedUnit = AstInspection.usesAngleSensitiveFunction(ast, functions)
                ? effectiveUnit
                : null;

        return new CalculationOutcome(
                historyStore.append(trimmed, result, recordedUnit, elapsedMs));
    }

    private String validateExpression(String expression) {
        if (expression == null) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "expression 不能为空");
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "expression 不能为空白");
        }
        int max = properties.maxExpressionLength();
        if (trimmed.length() > max) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "expression 长度不得超过 " + max + "，实际为 " + trimmed.length());
        }
        return trimmed;
    }

    /**
     * 请求级变量走与存储写入完全相同的校验 —— 不用「临时、不落库」当豁免理由。
     */
    private Map<String, CalcNumber> validatedRequestVariables(Map<String, CalcNumber> requestVariables) {
        if (requestVariables == null || requestVariables.isEmpty()) {
            return Map.of();
        }
        Map<String, CalcNumber> validated = new HashMap<>();
        for (Map.Entry<String, CalcNumber> entry : requestVariables.entrySet()) {
            variableService.validateVariableName(entry.getKey());
            validated.put(entry.getKey(), entry.getValue());
        }
        return validated;
    }
}
```

- [ ] **Step 12: 运行确认通过**

运行：`mvn -q test -Dtest=CalculationServiceTest`

预期：全部 PASS。

- [ ] **Step 13: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 14: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/service/ \
        src/test/java/com/wysjwxm/calculator/service/
git commit -m "feat: 服务层 — 计算编排、历史分页、变量保留名校验"
```

---

### Task 12: 错误响应与全局异常处理

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/api/error/ErrorResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/error/ErrorStatusMapper.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/error/GlobalExceptionHandler.java`
- Test: `src/test/java/com/wysjwxm/calculator/api/error/ErrorStatusMapperTest.java`

**Interfaces:**
- Consumes: `CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `record ErrorResponse(String code, String message, Integer position, Instant timestamp, String path)`
  - `ErrorStatusMapper.toStatus(CalcErrorCode code)` → `HttpStatus`
  - `GlobalExceptionHandler`：`@RestControllerAdvice`，处理 `CalcException`、`NoHandlerFoundException`、`HttpRequestMethodNotSupportedException`、`HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException`、`Exception`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/api/error/ErrorStatusMapperTest.java`：

```java
package com.wysjwxm.calculator.api.error;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码 → HTTP 状态的映射必须覆盖全部枚举值。
 * 这条测试是防止「新增错误码却忘了给状态」的守门人 ——
 * 没有它，漏映射的错误会在运行时才以 500 的形式暴露。
 */
class ErrorStatusMapperTest {

    @Test
    void everyErrorCodeMapsToAStatus() {
        for (CalcErrorCode code : CalcErrorCode.values()) {
            assertThat(ErrorStatusMapper.toStatus(code))
                    .as("错误码 %s 缺少 HTTP 状态映射", code)
                    .isNotNull();
        }
    }

    @Test
    void parseErrorsAreBadRequest() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.PARSE_ERROR)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.INVALID_REQUEST)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.UNKNOWN_FUNCTION)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void notFoundCodesMapTo404() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.VARIABLE_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.HISTORY_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.NO_HANDLER)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void semanticErrorsMapTo422() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.UNKNOWN_VARIABLE)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.DIVISION_BY_ZERO)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.DOMAIN_ERROR)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.NON_FINITE_RESULT)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void methodNotAllowedMapsTo405() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.METHOD_NOT_ALLOWED)).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void internalErrorMapsTo500() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.INTERNAL_ERROR)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=ErrorStatusMapperTest`

预期：编译失败，`ErrorStatusMapper` 不存在。

- [ ] **Step 3: 创建 ErrorResponse**

创建 `src/main/java/com/wysjwxm/calculator/api/error/ErrorResponse.java`：

```java
package com.wysjwxm.calculator.api.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * @param position 仅语法类错误有值，其余为 null；用 JsonInclude 让无位置的
 *                 响应里不出现该字段，避免调用方误判为 0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Integer position,
                            Instant timestamp, String path) {
}
```

- [ ] **Step 4: 创建 ErrorStatusMapper**

创建 `src/main/java/com/wysjwxm/calculator/api/error/ErrorStatusMapper.java`：

```java
package com.wysjwxm.calculator.api.error;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 错误码 → HTTP 状态的唯一映射点。
 *
 * <p>core 层的 {@link CalcErrorCode} 刻意不携带 HTTP 状态，以保持对传输协议
 * 无感知；映射集中在这里，由 ErrorStatusMapperTest 保证不漏。
 */
public final class ErrorStatusMapper {

    private ErrorStatusMapper() {
    }

    public static HttpStatus toStatus(CalcErrorCode code) {
        return switch (code) {
            case PARSE_ERROR, INVALID_REQUEST, UNKNOWN_FUNCTION -> HttpStatus.BAD_REQUEST;
            case VARIABLE_NOT_FOUND, HISTORY_NOT_FOUND, NO_HANDLER -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNKNOWN_VARIABLE, DIVISION_BY_ZERO, DOMAIN_ERROR, NON_FINITE_RESULT ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
```

- [ ] **Step 5: 创建 GlobalExceptionHandler**

创建 `src/main/java/com/wysjwxm/calculator/api/error/GlobalExceptionHandler.java`：

```java
package com.wysjwxm.calculator.api.error;

import com.wysjwxm.calculator.core.error.CalcErrorCode;
import com.wysjwxm.calculator.core.error.CalcException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;

/**
 * 全局异常处理。所有错误响应走同一个结构（spec §7.6）。
 *
 * <p>不依赖 Spring 默认错误页 —— application.yaml 已关闭 whitelabel。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CalcException.class)
    public ResponseEntity<ErrorResponse> handleCalc(CalcException ex, HttpServletRequest request) {
        return build(ex.code(), ex.getMessage(), ex.position(), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex,
                                                         HttpServletRequest request) {
        return build(CalcErrorCode.NO_HANDLER,
                "路径不存在: " + request.getRequestURI(), null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(CalcErrorCode.METHOD_NOT_ALLOWED,
                "该路径不支持 " + ex.getMethod() + " 方法", null, request);
    }

    /** 请求体不是合法 JSON，或字段类型无法绑定。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(CalcErrorCode.INVALID_REQUEST, "请求体不是合法的 JSON 或字段类型不匹配", null, request);
    }

    /** 路径变量或查询参数类型不符，例如 /history/abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(CalcErrorCode.INVALID_REQUEST,
                "参数 " + ex.getName() + " 的取值不合法: " + ex.getValue(), null, request);
    }

    /** 兜底。日志留全栈，但不把堆栈外泄给调用方。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期的异常, path={}", request.getRequestURI(), ex);
        return build(CalcErrorCode.INTERNAL_ERROR, "服务内部错误", null, request);
    }

    private ResponseEntity<ErrorResponse> build(CalcErrorCode code, String message,
                                                Integer position, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code.name(), message, position,
                Instant.now(), request.getRequestURI());
        return ResponseEntity.status(ErrorStatusMapper.toStatus(code)).body(body);
    }
}
```

- [ ] **Step 6: 配置 404 抛出异常**

修改 `src/main/resources/application.yaml`，在 `spring` 下加入：

```yaml
spring:
  application:
    name: scientific-calculator
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false
```

（其余段落保持不变。）这两项是让 `NoHandlerFoundException` 生效、并关掉静态资源兜底的必要配置。

- [ ] **Step 7: 运行确认通过**

运行：`mvn -q test -Dtest=ErrorStatusMapperTest`

预期：6 个测试 PASS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/api/error/ \
        src/main/resources/application.yaml \
        src/test/java/com/wysjwxm/calculator/api/error/
git commit -m "feat: 统一错误响应与全局异常处理，含错误码状态映射覆盖率测试"
```

---

### Task 13: HTTP 接口

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/CalcNumberSerializer.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/CalculateRequest.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/CalculateResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/FunctionsResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/HistoryItemResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/HistoryPageResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/PutVariableRequest.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/VariableResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/VariableListResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/ClearHistoryResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/dto/HealthResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/CalculatorController.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/HistoryController.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/VariableController.java`
- Create: `src/main/java/com/wysjwxm/calculator/api/MetaController.java`
- Test: `src/test/java/com/wysjwxm/calculator/api/CalculatorControllerTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/api/HistoryControllerTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/api/VariableControllerTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/api/MetaControllerTest.java`

**Interfaces:**
- Consumes: `CalculationService`、`HistoryService`、`VariableService`（Task 11）、`ProfileResult`/`HistoryRecord`/`VariableRecord`（Task 9/10）、`OperatorTable`、`FunctionRegistry`、`Constants`（Task 5/7）、`CalculatorProperties`（Task 1）、`GlobalExceptionHandler`（Task 12）
- Produces: 10 个 HTTP 端点（见 spec §3.1）

- [ ] **Step 1: 创建 CalcNumberSerializer**

创建 `src/main/java/com/wysjwxm/calculator/api/dto/CalcNumberSerializer.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.DecimalNumber;

import java.io.IOException;

/**
 * 把 CalcNumber 序列化为 JSON 数字：精确路径写 BigDecimal（Jackson 会
 * 输出 0.3 而非 0.30000000000000004），浮点路径写 double。
 */
public class CalcNumberSerializer extends JsonSerializer<CalcNumber> {

    @Override
    public void serialize(CalcNumber value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value instanceof DecimalNumber decimal) {
            gen.writeNumber(decimal.value().stripTrailingZeros());
        } else {
            gen.writeNumber(value.toDouble());
        }
    }
}
```

- [ ] **Step 2: 创建全部 DTO**

创建 `src/main/java/com/wysjwxm/calculator/api/dto/CalculateRequest.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.wysjwxm.calculator.core.AngleUnit;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param variables 请求级临时变量，仅本次求值生效、不落库；
 *                  键同样受保留名约束
 */
public record CalculateRequest(String expression, AngleUnit angleUnit,
                               Map<String, BigDecimal> variables) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/CalculateResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;

public record CalculateResponse(
        String expression,
        @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber result,
        String resultType,
        AngleUnit angleUnit,
        long historyId,
        double elapsedMs) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/FunctionsResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.operator.Associativity;
import com.wysjwxm.calculator.core.operator.Fixity;
import com.wysjwxm.calculator.core.operator.Operator;

import java.util.List;
import java.util.Set;

/**
 * 服务能力清单，如实描述接受的表达式语法（spec §7.2）。
 * operators 由 OperatorTable 直接生成，因此不可能与实际解析行为漂移。
 */
public record FunctionsResponse(
        Set<String> constants,
        List<String> unaryFunctions,
        List<String> binaryFunctions,
        List<OperatorEntry> operators,
        List<AngleUnit> angleUnits,
        AngleUnit defaultAngleUnit) {

    /**
     * @param associativity 仅 INFIX 有值，PREFIX/POSTFIX 为 null
     */
    public record OperatorEntry(String symbol, Fixity fixity, int precedence,
                                Associativity associativity) {

        public static OperatorEntry from(Operator operator) {
            return new OperatorEntry(operator.symbol(), operator.fixity(),
                    operator.precedence(), operator.associativity());
        }
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/HistoryItemResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.store.HistoryRecord;

import java.time.Instant;

public record HistoryItemResponse(
        long id,
        String expression,
        @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber result,
        String resultType,
        AngleUnit angleUnit,
        double elapsedMs,
        Instant createdAt) {

    public static HistoryItemResponse from(HistoryRecord record) {
        return new HistoryItemResponse(
                record.id(),
                record.expression(),
                record.result(),
                record.result().isExact() ? "DECIMAL" : "FLOATING",
                record.angleUnit(),
                record.elapsedMs(),
                record.createdAt());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/HistoryPageResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.wysjwxm.calculator.store.PageResult;

import java.util.List;

public record HistoryPageResponse(List<HistoryItemResponse> items, int page, int size,
                                  long totalElements, int totalPages, boolean hasNext) {

    public static HistoryPageResponse from(PageResult<com.wysjwxm.calculator.store.HistoryRecord> page) {
        return new HistoryPageResponse(
                page.items().stream().map(HistoryItemResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages(), page.hasNext());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/PutVariableRequest.java`：

```java
package com.wysjwxm.calculator.api.dto;

import java.math.BigDecimal;

public record PutVariableRequest(BigDecimal value) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/VariableResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.store.VariableRecord;

import java.time.Instant;

public record VariableResponse(String name,
                               @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber value,
                               Instant createdAt,
                               Instant updatedAt) {

    public static VariableResponse from(VariableRecord record) {
        return new VariableResponse(record.name(), record.value(),
                record.createdAt(), record.updatedAt());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/VariableListResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

import java.util.List;

public record VariableListResponse(List<VariableResponse> items, int total) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/ClearHistoryResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

public record ClearHistoryResponse(int deleted) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/dto/HealthResponse.java`：

```java
package com.wysjwxm.calculator.api.dto;

public record HealthResponse(String status, long uptimeMs) {
}
```

- [ ] **Step 3: 创建 4 个 Controller**

创建 `src/main/java/com/wysjwxm/calculator/api/CalculatorController.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.dto.CalculateRequest;
import com.wysjwxm.calculator.api.dto.CalculateResponse;
import com.wysjwxm.calculator.api.dto.FunctionsResponse;
import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.Constants;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.number.CalcNumber;
import com.wysjwxm.calculator.core.number.DecimalNumber;
import com.wysjwxm.calculator.core.operator.OperatorTable;
import com.wysjwxm.calculator.service.CalculationOutcome;
import com.wysjwxm.calculator.service.CalculationService;
import com.wysjwxm.calculator.store.HistoryRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/calculator")
public class CalculatorController {

    private final CalculationService calculationService;
    private final FunctionRegistry functionRegistry;
    private final CalculatorProperties properties;

    public CalculatorController(CalculationService calculationService,
                                FunctionRegistry functionRegistry,
                                CalculatorProperties properties) {
        this.calculationService = calculationService;
        this.functionRegistry = functionRegistry;
        this.properties = properties;
    }

    @PostMapping("/calculate")
    public CalculateResponse calculate(@RequestBody CalculateRequest request) {
        CalculationOutcome outcome = calculationService.calculate(
                request.expression(), request.angleUnit(), toCalcNumbers(request.variables()));
        HistoryRecord record = outcome.record();
        return new CalculateResponse(
                record.expression(),
                record.result(),
                record.result().isExact() ? "DECIMAL" : "FLOATING",
                record.angleUnit(),
                record.id(),
                record.elapsedMs());
    }

    @GetMapping("/functions")
    public FunctionsResponse functions() {
        return new FunctionsResponse(
                Constants.names(),
                functionRegistry.unaryNames(),
                functionRegistry.binaryNames(),
                OperatorTable.all().stream().map(FunctionsResponse.OperatorEntry::from).toList(),
                List.of(AngleUnit.values()),
                properties.defaultAngleUnit());
    }

    private Map<String, CalcNumber> toCalcNumbers(Map<String, java.math.BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, CalcNumber> converted = new LinkedHashMap<>();
        raw.forEach((name, value) -> converted.put(name, new DecimalNumber(value)));
        return converted;
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/HistoryController.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.dto.ClearHistoryResponse;
import com.wysjwxm.calculator.api.dto.HistoryItemResponse;
import com.wysjwxm.calculator.api.dto.HistoryPageResponse;
import com.wysjwxm.calculator.service.HistoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public HistoryPageResponse page(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return HistoryPageResponse.from(historyService.page(page, size));
    }

    @GetMapping("/{id}")
    public HistoryItemResponse get(@PathVariable long id) {
        return HistoryItemResponse.from(historyService.get(id));
    }

    @DeleteMapping
    public ClearHistoryResponse clear() {
        return new ClearHistoryResponse(historyService.clear());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/VariableController.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.dto.PutVariableRequest;
import com.wysjwxm.calculator.api.dto.VariableListResponse;
import com.wysjwxm.calculator.api.dto.VariableResponse;
import com.wysjwxm.calculator.core.number.DecimalNumber;
import com.wysjwxm.calculator.service.VariableService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/variables")
public class VariableController {

    private final VariableService variableService;

    public VariableController(VariableService variableService) {
        this.variableService = variableService;
    }

    /** 幂等 upsert —— 覆盖已存在变量同样返回 200，保持 PUT 语义。 */
    @PutMapping("/{name}")
    public VariableResponse put(@PathVariable String name, @RequestBody PutVariableRequest request) {
        BigDecimal value = request == null ? null : request.value();
        return VariableResponse.from(variableService.put(name, requireNumber(value)));
    }

    @GetMapping
    public VariableListResponse list() {
        var records = variableService.list();
        return new VariableListResponse(
                records.stream().map(VariableResponse::from).toList(), records.size());
    }

    @GetMapping("/{name}")
    public VariableResponse get(@PathVariable String name) {
        return VariableResponse.from(variableService.get(name));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        variableService.delete(name);
    }

    private DecimalNumber requireNumber(BigDecimal value) {
        if (value == null) {
            throw com.wysjwxm.calculator.core.error.CalcException.of(
                    com.wysjwxm.calculator.core.error.CalcErrorCode.INVALID_REQUEST,
                    "value 不能为空");
        }
        return new DecimalNumber(value);
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/api/MetaController.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private final Instant startedAt = Instant.now();

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Duration.between(startedAt, Instant.now()).toMillis());
    }
}
```

- [ ] **Step 4: 写 CalculatorController 测试**

创建 `src/test/java/com/wysjwxm/calculator/api/CalculatorControllerTest.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.error.GlobalExceptionHandler;
import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.service.CalculationService;
import com.wysjwxm.calculator.service.VariableService;
import com.wysjwxm.calculator.store.InMemoryCalculationHistoryStore;
import com.wysjwxm.calculator.store.InMemoryVariableStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用 standaloneSetup 而非 @WebMvcTest：避免为每个测试加载 Spring 上下文，
 * 同时把 GlobalExceptionHandler 显式挂上，错误码路径才被真实覆盖。
 */
class CalculatorControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FunctionRegistry registry = new FunctionRegistry();
        CalculatorProperties props = new CalculatorProperties(AngleUnit.DEGREE, 1000, 34, 100);
        InMemoryCalculationHistoryStore history = new InMemoryCalculationHistoryStore(props);
        VariableService variableService = new VariableService(new InMemoryVariableStore(), registry);
        CalculationService service = new CalculationService(
                registry, new Numbers(34), history, variableService, props);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculatorController(service, registry, props))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void calculatesExpression() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"1+2*3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(7))
                .andExpect(jsonPath("$.resultType").value("DECIMAL"))
                .andExpect(jsonPath("$.historyId").isNumber())
                .andExpect(jsonPath("$.elapsedMs").isNumber());
    }

    @Test
    void specAcceptanceExpressionReturnsOnePointFive() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"1 + 2 * sin(30) ^ 2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1.5))
                .andExpect(jsonPath("$.angleUnit").value("DEGREE"));
    }

    @Test
    void decimalPrecisionSurvivesSerialization() throws Exception {
        // 0.1+0.2 必须序列化成 0.3，而不是 0.30000000000000004
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"0.1+0.2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(0.3));
    }

    @Test
    void angleUnitIsNullForNonTrigExpression() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"1+2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").doesNotExist());
    }

    @Test
    void radianAngleUnitIsHonoured() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"sin(30)\",\"angleUnit\":\"RADIAN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").value("RADIAN"));
    }

    // ---------- 错误码 ----------

    @Test
    void parseErrorReturns400WithPosition() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"1+\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.path").value("/api/v1/calculator/calculate"));
    }

    @Test
    void divisionByZeroReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"1/0\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DIVISION_BY_ZERO"))
                .andExpect(jsonPath("$.position").doesNotExist());
    }

    @Test
    void domainErrorReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"sqrt(-1)\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_ERROR"));
    }

    @Test
    void unknownFunctionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"nope(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FUNCTION"));
    }

    @Test
    void unknownVariableReturns422() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"y+1\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_VARIABLE"));
    }

    @Test
    void missingExpressionReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reservedNameInRequestVariablesReturns400() throws Exception {
        // spec §7.1：请求级变量不得使用 pi
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"sin(pi)\",\"variables\":{\"pi\":3}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pi")));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- 能力清单 ----------

    @Test
    void functionsManifestListsOperatorsWithSymbols() throws Exception {
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constants").isArray())
                .andExpect(jsonPath("$.unaryFunctions.length()").value(23))
                .andExpect(jsonPath("$.binaryFunctions.length()").value(5))
                .andExpect(jsonPath("$.operators.length()").value(9))
                .andExpect(jsonPath("$.defaultAngleUnit").value("DEGREE"));
    }

    @Test
    void functionsManifestDoesNotAdvertiseRedundantSpellings() throws Exception {
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binaryFunctions",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("pow"))))
                .andExpect(jsonPath("$.binaryFunctions",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("mod"))))
                .andExpect(jsonPath("$.unaryFunctions",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("fact"))));
    }

    @Test
    void manifestPrecedenceMatchesOperatorTable() throws Exception {
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operators[?(@.symbol=='^')].precedence")
                        .value(org.hamcrest.Matchers.contains(4)))
                .andExpect(jsonPath("$.operators[?(@.symbol=='^')].associativity")
                        .value(org.hamcrest.Matchers.contains("RIGHT")))
                .andExpect(jsonPath("$.operators[?(@.symbol=='!')].fixity")
                        .value(org.hamcrest.Matchers.contains("POSTFIX")));
    }
}
```

- [ ] **Step 5: 运行确认通过**

运行：`mvn -q test -Dtest=CalculatorControllerTest`

预期：全部 PASS。

**若 `malformedJsonReturns400` 失败并返回 500**：`HttpMessageNotReadableException` 未被 `standaloneSetup` 的 advise 捕获，检查 `GlobalExceptionHandler` 中该 handler 是否声明为 `@ExceptionHandler(HttpMessageNotReadableException.class)`。

- [ ] **Step 6: 写 HistoryController 测试**

创建 `src/test/java/com/wysjwxm/calculator/api/HistoryControllerTest.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.error.GlobalExceptionHandler;
import com.wysjwxm.calculator.config.CalculatorProperties;
import com.wysjwxm.calculator.core.AngleUnit;
import com.wysjwxm.calculator.core.number.Numbers;
import com.wysjwxm.calculator.service.HistoryService;
import com.wysjwxm.calculator.store.InMemoryCalculationHistoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoryControllerTest {

    private InMemoryCalculationHistoryStore store;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = new InMemoryCalculationHistoryStore(
                new CalculatorProperties(AngleUnit.DEGREE, 1000, 34, 100));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HistoryController(new HistoryService(store)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsNewestFirst() throws Exception {
        Numbers numbers = new Numbers(34);
        store.append("first", numbers.of(1L), null, 0.1);
        store.append("second", numbers.of(2L), null, 0.2);

        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].expression").value("second"))
                .andExpect(jsonPath("$.items[1].expression").value("first"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void returnsSingleRecord() throws Exception {
        Numbers numbers = new Numbers(34);
        var record = store.append("1+1", numbers.of(2L), null, 0.1);
        mockMvc.perform(get("/api/v1/history/" + record.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression").value("1+1"))
                .andExpect(jsonPath("$.result").value(2))
                .andExpect(jsonPath("$.resultType").value("DECIMAL"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/history/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HISTORY_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/history?size=500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/history?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(get("/api/v1/history/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void clearsHistory() throws Exception {
        Numbers numbers = new Numbers(34);
        store.append("a", numbers.of(1L), null, 0.1);
        store.append("b", numbers.of(1L), null, 0.1);

        mockMvc.perform(delete("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));
    }
}
```

- [ ] **Step 7: 写 VariableController 测试**

创建 `src/test/java/com/wysjwxm/calculator/api/VariableControllerTest.java`：

```java
package com.wysjwxm.calculator.api;

import com.wysjwxm.calculator.api.error.GlobalExceptionHandler;
import com.wysjwxm.calculator.core.function.FunctionRegistry;
import com.wysjwxm.calculator.service.VariableService;
import com.wysjwxm.calculator.store.InMemoryVariableStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VariableControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new VariableController(
                        new VariableService(new InMemoryVariableStore(), new FunctionRegistry())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void putVariable(String name, String body) throws Exception {
        mockMvc.perform(put("/api/v1/variables/" + name)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void putThenGet() throws Exception {
        putVariable("x", "{\"value\":5}");
        mockMvc.perform(get("/api/v1/variables/x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("x"))
                .andExpect(jsonPath("$.value").value(5))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    void putIsIdempotentAndOverwrites() throws Exception {
        putVariable("x", "{\"value\":5}");
        putVariable("x", "{\"value\":6}");
        mockMvc.perform(get("/api/v1/variables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].value").value(6));
    }

    @Test
    void listIsSortedByName() throws Exception {
        putVariable("zeta", "{\"value\":1}");
        putVariable("alpha", "{\"value\":2}");
        mockMvc.perform(get("/api/v1/variables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("alpha"))
                .andExpect(jsonPath("$.items[1].name").value("zeta"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void decimalValueRoundTripsExactly() throws Exception {
        putVariable("x", "{\"value\":0.1}");
        mockMvc.perform(get("/api/v1/variables/x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value(0.1));
    }

    @Test
    void deleteReturns204() throws Exception {
        putVariable("x", "{\"value\":5}");
        mockMvc.perform(delete("/api/v1/variables/x"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        mockMvc.perform(delete("/api/v1/variables/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIABLE_NOT_FOUND"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/variables/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VARIABLE_NOT_FOUND"));
    }

    // ---------- 保留名（spec §6.4） ----------

    @Test
    void reservedConstantNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/variables/pi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pi")));
    }

    @Test
    void functionNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/variables/sin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/variables/1abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void missingValueReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/variables/x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
```

- [ ] **Step 8: 写 MetaController 测试**

创建 `src/test/java/com/wysjwxm/calculator/api/MetaControllerTest.java`：

```java
package com.wysjwxm.calculator.api;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetaControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MetaController())
            .build();

    @Test
    void healthReportsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.uptimeMs").isNumber());
    }
}
```

- [ ] **Step 9: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 10: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/api/ src/test/java/com/wysjwxm/calculator/api/
git commit -m "feat: HTTP 接口层 — 求值、能力清单、历史、变量、健康检查共 10 个端点"
```

---

### Task 14: 端到端测试与打包验证

**Files:**
- Test: `src/test/java/com/wysjwxm/calculator/CalculatorEndToEndTest.java`
- Modify: `src/test/java/com/wysjwxm/calculator/ScientificCalculatorApplicationTests.java`

**Interfaces:**
- Consumes: 全部组件
- Produces: 全链路验证

- [ ] **Step 1: 扩展上下文测试**

把 `src/test/java/com/wysjwxm/calculator/ScientificCalculatorApplicationTests.java` 替换为：

```java
package com.wysjwxm.calculator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScientificCalculatorApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

（内容不变。保留它是为了确认所有 Bean 能装配 —— 尤其是 `CalculatorProperties` 的校验与三个 `@Repository`/`@Service` 的构造器注入。）

- [ ] **Step 2: 写端到端测试**

创建 `src/test/java/com/wysjwxm/calculator/CalculatorEndToEndTest.java`：

```java
package com.wysjwxm.calculator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 HTTP 栈的全链路验证：算 → 查历史 → 定义变量 → 用变量再算。
 * 用 RANDOM_PORT 避免与开发机上可能在跑的 8080 冲突。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalculatorEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void fullWorkflow() {
        // 1. 健康检查
        ResponseEntity<String> health = rest.getForEntity(url("/api/v1/health"), String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");

        // 2. 能力清单
        ResponseEntity<String> functions = rest.getForEntity(
                url("/api/v1/calculator/functions"), String.class);
        assertThat(functions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(functions.getBody()).contains("\"sin\"");

        // 3. 表达式求值（spec §14 验收标准第 3 条）
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1 + 2 * sin(30) ^ 2\"}");
        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calc.getBody()).contains("\"result\":1.5");

        // 4. 精确性（spec §14 验收标准第 4 条）
        ResponseEntity<String> exact = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"0.1+0.2\"}");
        assertThat(exact.getBody()).contains("\"result\":0.3");

        // 5. 定义变量
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> put = rest.exchange(url("/api/v1/variables/x"), HttpMethod.PUT,
                new HttpEntity<>("{\"value\":5}", headers), String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 6. 用变量再算
        ResponseEntity<String> withVariable = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"x * 2 + 1\"}");
        assertThat(withVariable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withVariable.getBody()).contains("\"result\":11");

        // 7. 查历史（上述 3 次成功计算应都已落库）
        ResponseEntity<String> history = rest.getForEntity(
                url("/api/v1/history?page=0&size=10"), String.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).contains("\"totalElements\":3");

        // 8. 保留名被拒（spec §14 验收标准第 5 条）
        ResponseEntity<String> reserved = rest.exchange(url("/api/v1/variables/pi"), HttpMethod.PUT,
                new HttpEntity<>("{\"value\":3}", headers), String.class);
        assertThat(reserved.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reserved.getBody()).contains("INVALID_REQUEST");

        // 9. 错误码
        ResponseEntity<String> divideByZero = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1/0\"}");
        assertThat(divideByZero.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(divideByZero.getBody()).contains("DIVISION_BY_ZERO");

        // 10. 未知路径
        ResponseEntity<String> notFound = rest.getForEntity(url("/api/v1/nope"), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 11. 清空历史
        ResponseEntity<String> cleared = rest.exchange(url("/api/v1/history"), HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody()).contains("\"deleted\":3");
    }
}
```

- [ ] **Step 3: 运行端到端测试**

运行：`mvn -q test -Dtest=CalculatorEndToEndTest`

预期：PASS。

**若第 7 步 `totalElements` 不是 3**：说明某个计算请求未落历史或落了两次，检查 `CalculationService` 是否只在求值成功后 `append`。

**若第 10 步返回 500 而非 404**：检查 `application.yaml` 中 `spring.mvc.throw-exception-if-no-handler-found` 与 `spring.web.resources.add-mappings` 是否都已设置（Task 12 Step 6）。

- [ ] **Step 4: 运行全部测试**

运行：`mvn -q clean test`

预期：BUILD SUCCESS，全部测试通过。

- [ ] **Step 5: 打包并验证 runnable jar**

运行：

```bash
mvn -q clean package
ls -la target/scientific-calculator-0.0.1-SNAPSHOT.jar
```

预期：jar 存在且体积明显大于几 KB（说明 `repackage` 已把依赖打进 fat jar）。

- [ ] **Step 6: 手动冒烟测试**

在一个终端启动：

```bash
java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar
```

在另一个终端验证（spec §14 验收标准第 2、3、4 条）：

```bash
curl -s localhost:8080/api/v1/health
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
  -H 'Content-Type: application/json' \
  -d '{"expression":"1 + 2 * sin(30) ^ 2"}'
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
  -H 'Content-Type: application/json' \
  -d '{"expression":"0.1+0.2"}'
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
  -H 'Content-Type: application/json' \
  -d '{"expression":"-2^2"}'
curl -s -X PUT localhost:8080/api/v1/variables/pi \
  -H 'Content-Type: application/json' -d '{"value":3}'
curl -s localhost:8080/api/v1/calculator/functions
```

预期依次为：`{"status":"UP",...}`、`"result":1.5`、`"result":0.3`、`"result":-4`、
400 + `INVALID_REQUEST`、完整能力清单。

确认后 Ctrl-C 停止服务。

- [ ] **Step 7: 提交**

```bash
git add src/test/
git commit -m "test: 端到端全链路测试与 runnable jar 验证"
```

---

### Task 15: 交付文档

**Files:**
- Create: `README.md`
- Create: `docs/01-需求分析.md`
- Create: `docs/02-架构设计.md`
- Create: `docs/03-AI协作记录.md`

**Interfaces:**
- Consumes: spec（`docs/superpowers/specs/2026-09-12-scientific-calculator-design.md`）与全部已实现代码
- Produces: 交付文档

- [ ] **Step 1: 写 README.md**

内容须包含：项目简介、技术栈、构建命令（`mvn clean package`）、启动命令（`java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar`）、10 个端点的表格（方法 / 路径 / 说明）、每个端点一条可直接复制的 `curl` 示例、表达式语法速查（含优先级规则 `-2^2 = -4`、`2^3^2 = 512`）、保留名规则（变量名不得使用函数名与 `pi`/`e`）。

- [ ] **Step 2: 写 docs/01-需求分析.md**

从 spec §1、§3、§6、§7 提炼：原始需求、需求拆解过程、功能边界（做/不做及理由）、用例清单、验收标准。重点体现**边界是自行划定并论证的**，而非照抄。

- [ ] **Step 3: 写 docs/02-架构设计.md**

以 spec 为源头整理润色：分层架构与依赖方向铁律、数值模型的取舍、表达式语言规范、并发与存储策略（含 FIFO vs LRU 的论证）、错误码表，以及 **§12 设计决策记录（D1–D9）—— 含被否决方案及否决理由**。

- [ ] **Step 4: 写 docs/03-AI协作记录.md**

按阶段（需求分析 / 架构设计 / 编码 / 测试）列表记录。表格形如：

```markdown
| 阶段 | 步骤 | AI 产出 | 我的校验、优化、修正 |
|---|---|---|---|
| 需求分析 | 拆分功能边界 | ... | （待填写） |
```

**文档顶部必须显式说明**：「AI 产出」栏由 AI 如实填写；「我的校验、优化、修正」栏由本人填写，不由 AI 代写。交付时该栏保留为待填模板。

- [ ] **Step 5: 提交**

```bash
git add README.md docs/01-需求分析.md docs/02-架构设计.md docs/03-AI协作记录.md
git commit -m "docs: 交付 README 与三份过程文档"
```

- [ ] **Step 6: 最终验收**

对照 spec §14 逐条核对 8 项验收标准，全部达成后运行：

```bash
mvn -q clean package && ls -la target/*.jar
```

---

## 自检记录

**1. Spec 覆盖检查**

| spec 章节 | 对应任务 |
|---|---|
| §2 约束（Java17/SB3.5/单一依赖） | Task 1 |
| §3.1 做（10 个端点） | Task 13 |
| §3.2 不做 | 全域（不出现在任何任务中） |
| §4 分层与依赖方向 | Task 1–13 的包划分 |
| §5 数值模型 | Task 3 |
| §6.1 词法 | Task 4 |
| §6.2–6.3 语法与优先级 | Task 5、6 |
| §6.4 保留常量与禁用集合 | Task 7、9、11（VariableService 统一校验） |
| §6.5 函数集与定义域 | Task 7 |
| §7.1 求值接口 | Task 13 |
| §7.2 能力清单 | Task 13（由 OperatorTable 生成） |
| §7.3 历史 | Task 10、11、12、13 |
| §7.4 变量 | Task 9、11、13 |
| §7.5 健康检查 | Task 13 |
| §7.6 错误码（12 个） | Task 2、12 |
| §8 并发与存储（FIFO） | Task 9、10 |
| §9 配置项 | Task 1 |
| §10 测试策略 | 每个任务的测试步骤 |
| §11 交付物 | Task 15 |
| §12 决策记录 D1–D9 | Task 15 Step 3 要求写入 02-架构设计.md |
| §13 风险 | Task 15 |
| §14 验收标准 | Task 14 |

无未覆盖项。

**2. 占位符扫描**：无 TBD / TODO / 「类似 Task N」/ 无代码的代码步骤。

**3. 类型一致性核对**：

- `CalcException.of(code, message)` / `.at(code, message, position)` — Task 2 定义，Task 3 起一致使用
- `Numbers` 为实例类，构造参数 `int divisionPrecision` — Task 3 定义，Task 8/9/11 一致
- `Operator` 为 `(symbol, fixity, precedence, associativity)` — Task 5 定义，Task 6 解析器、Task 13 清单生成一致
- `EvaluationContext.lookup(String)` 返回 `Optional<CalcNumber>` — Task 8 定义，Task 11 调用一致
- `VariableStore.put/find/findAll/delete/size` — Task 9 定义，Task 11 一致
- `CalculationHistoryStore.append/find/findPage/clear/size` — Task 10 定义，Task 11/13 一致
- `VariableService.validateVariableName(String)` — Task 11 定义，Task 11 的 `CalculationService` 复用（**这是保留名校验统一生效的关键接缝**）
- `CalculationOutcome(HistoryRecord record)` — Task 11 定义与使用一致
- `HistoryRecord` 含 `elapsedMs()` — Task 10 定义，Task 11/13 一致

**4. 已识别的实现期风险**

| 风险 | 位置 | 处理 |
|---|---|---|
| `spring-boot-starter-parent:3.5.6` 可能不存在 | Task 1 Step 1 | 先查 Maven Central，取实际存在的 3.5.x 补丁版 |
| `jakarta.validation` 是否随 web starter 传递引入 | Task 1 Step 8 | 已给出降级方案（手工校验 + IllegalArgumentException） |
| `9^9^9` 走精确路径时 `BigDecimal.pow` 抛异常 | Task 8 Step 6 | 已在 `Numbers.power` 中 catch 并降级到 double |
| `HttpMessageNotReadableException` 未被 advise 捕获 | Task 13 Step 5 | 已给出排查提示 |
| 404 未走 `NoHandlerFoundException` | Task 14 Step 3 | 已给出 `application.yaml` 两项配置 |
