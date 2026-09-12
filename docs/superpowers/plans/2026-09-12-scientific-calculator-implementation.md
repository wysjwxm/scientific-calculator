# 科学计算器后端服务 实现计划（DDD 版）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 DDD 四层架构实现一套纯内存、零外部依赖的科学计算器 HTTP 后端服务，打包为 `java -jar` 可直接启动的 runnable jar。

**Architecture:** DDD 四层单向依赖 —— `interfaces → application → domain ← infrastructure`。`domain` 承载全部业务规则且零框架依赖（不 import `org.springframework.*`），因此可被最纯粹地单测；`interfaces` 同时承担防腐层职责（JSON ↔ 领域对象）；聚合根在领域层声明接口、在基础设施层提供内存实现。关键不变量（变量名不得为保留名）由值对象 `VariableName` 的构造器保证，而非由调用纪律保证。

**Tech Stack:** Java 17、Spring Boot 3.5.x、Maven、JUnit 5（由 `spring-boot-starter-test` 提供）、Jackson（由 `spring-boot-starter-web` 传递引入）

**Spec:** `docs/superpowers/specs/2026-09-12-scientific-calculator-design.md`（v3）

## Global Constraints

以下为 spec 的项目级要求，**每个任务都隐含包含本节**：

- Java 版本：**17**；可使用 record、sealed interface、switch 模式匹配
- Spring Boot parent：**3.5.x**（原脚手架 4.1.1 必须降级，见 spec §12 D9）
- **新增依赖只允许一个**：`spring-boot-starter-web`。**不得**引入 Lombok、Guava、Commons、actuator、validation starter 或任何其他第三方库
- **零外部调用**：不发起任何对外网络请求，不接入任何外部服务、数据库、缓存
- **`domain` 包禁止 import 任何 `org.springframework.*`**，也禁止 import `application` / `infrastructure` / `interfaces`
- **禁止反向依赖**：`application` 不 import `infrastructure` 与 `interfaces`；`infrastructure` 不 import `application` 与 `interfaces`
- 所有 Spring Bean 的装配集中在 `infrastructure/config/CalculatorConfiguration`（领域类不能加注解，装配点保持唯一）
- HTTP 前缀：`/api/v1`
- 包根：`com.wysjwxm.calculator`
- 错误码取值（12 个，不可增删改名）：`PARSE_ERROR` `INVALID_REQUEST` `UNKNOWN_FUNCTION` `VARIABLE_NOT_FOUND` `HISTORY_NOT_FOUND` `NO_HANDLER` `METHOD_NOT_ALLOWED` `UNKNOWN_VARIABLE` `DIVISION_BY_ZERO` `DOMAIN_ERROR` `NON_FINITE_RESULT` `INTERNAL_ERROR`
- 算子优先级（**唯一事实来源是 `OperatorTable`**）：`+ -` = 1、`* / %` = 2、一元 `+ -` = 3、`^` = 4、`!` = 5；`^` 右结合，其余左结合
- 保留常量：`pi` = `3.141592653589793`、`e` = `2.718281828459045`
- **函数取名方法名为 `functionName()`，不能叫 `name()`**（枚举无法覆写 `Enum.name()`，见 spec §4.3）
- **`ExpressionParser` 必须无状态**（游标封在每次调用的局部对象里，见 spec §4.3）
- 变量名禁用集合 = **函数名 ∪ 保留常量名**，由 `VariableName` 构造器统一拒绝
- 一元函数 23 个：`sin cos tan asin acos atan sinh cosh tanh asinh acosh atanh sqrt cbrt abs exp ln log10 log2 floor ceil round sign`
- 二元函数 5 个：`hypot max min atan2 log`
- 不提供 `pow`/`mod` 函数（用 `^`/`%` 运算符），不提供 `fact` 函数（用 `!` 后缀）
- 提交信息用中文，格式 `type: 描述`

---

## 文件结构

```
src/main/java/com/wysjwxm/calculator/
├── ScientificCalculatorApplication.java      [已存在，不改]
├── domain/                                    ← 零框架依赖
│   ├── AngleUnit.java
│   ├── MathematicalConstant.java
│   ├── error/
│   │   ├── CalcErrorCode.java                 12 个错误码（不含 HTTP 状态）
│   │   └── CalcException.java                 领域异常，携带 code + position
│   └── model/
│       ├── number/
│       │   ├── CalcNumber.java                sealed interface
│       │   ├── DecimalNumber.java
│       │   ├── FloatingNumber.java
│       │   └── Numbers.java                   领域服务（算术与类型提升）
│       ├── expression/
│       │   ├── ExpressionText.java            值对象：原文，构造即校验
│       │   ├── Expression.java                sealed interface
│       │   ├── LiteralExpr.java  VariableExpr.java  UnaryExpr.java
│       │   ├── PostfixExpr.java  BinaryExpr.java    CallExpr.java
│       │   ├── Fixity.java  Associativity.java  Operator.java
│       │   ├── OperatorTable.java             优先级唯一事实来源
│       │   ├── parse/
│       │   │   ├── TokenType.java  Token.java  Lexer.java
│       │   │   └── ExpressionParser.java      无状态，优先级爬升
│       │   └── eval/
│       │       ├── EvaluationContext.java
│       │       ├── ExpressionEvaluator.java
│       │       └── AstInspection.java
│       ├── function/
│       │   ├── MathFunction.java
│       │   ├── UnaryFunction.java             23 个
│       │   ├── BinaryFunction.java            5 个
│       │   ├── AngleUnits.java                包内辅助
│       │   ├── FunctionRegistry.java
│       │   └── ReservedNames.java             值对象，standard() 静态可求
│       ├── variable/
│       │   ├── VariableName.java              **构造即校验，无旁路**
│       │   ├── Variable.java                  实体
│       │   └── VariableSet.java               聚合根接口
│       └── calculation/
│           ├── Calculation.java               实体
│           ├── PageResult.java
│           └── CalculationHistory.java        聚合根接口
├── application/                               纯 Java，零框架注解
│   ├── CalculationPolicy.java
│   ├── CalculationCommand.java
│   ├── CalculationUseCase.java
│   ├── VariableUseCase.java
│   └── HistoryUseCase.java
├── infrastructure/
│   ├── InMemoryVariableSet.java
│   ├── InMemoryCalculationHistory.java
│   └── config/
│       ├── CalculatorProperties.java
│       └── CalculatorConfiguration.java       全部 Bean 装配点
└── interfaces/                                防腐层
    ├── CalculatorController.java  HistoryController.java
    ├── VariableController.java    MetaController.java
    ├── dto/                       11 个 record + CalcNumberSerializer
    └── error/                     ErrorResponse, ErrorStatusMapper,
                                   GlobalExceptionHandler
```

**依赖方向铁律**：若某个任务发现 `domain` 需要 import `application` / `infrastructure` / `interfaces` 或任何 `org.springframework.*`，说明设计有问题，**停下来报告**，不要为了让代码通过而加依赖。

**测试辅助类**（放 test 源码，不进 main）：`ExpressionPrinter`（AST → 完全括号化中缀文本，供断言）。

---

## 任务总览

| # | 任务 | 层 | 产出 |
|---|---|---|---|
| 1 | 构建基线 | — | pom 降级、Web 依赖、`AngleUnit`、`CalculatorProperties` |
| 2 | 领域错误词汇表 | domain | `CalcErrorCode`、`CalcException` |
| 3 | 数值值对象 | domain | `CalcNumber` 家族、`Numbers` |
| 4 | 表达式原文 + 词法 | domain | `ExpressionText`、`Lexer` |
| 5 | 算子值对象 | domain | `OperatorTable` |
| 6 | AST 与解析器 | domain | `ExpressionParser`（无状态） |
| 7 | 函数值对象与保留名 | domain | `FunctionRegistry`、`ReservedNames` |
| 8 | 变量值对象（构造即校验） | domain | `VariableName`、`Variable` |
| 9 | 求值器 | domain | `ExpressionEvaluator` |
| 10 | 计算历史聚合根 | domain + infra | `CalculationHistory` + 内存实现 |
| 11 | 变量聚合根 | domain + infra | `VariableSet` + 内存实现 |
| 12 | 应用层用例与装配 | application + infra | 三个 UseCase、`CalculatorConfiguration` |
| 13 | 接口层错误处理 | interfaces | `GlobalExceptionHandler`、`ErrorStatusMapper` |
| 14 | 接口层 HTTP | interfaces | 4 个 Controller + DTO |
| 15 | 端到端测试与打包 | 全链路 | runnable jar 验证 |
| 16 | 交付文档 | — | README、三份文档 |

---

### Task 1: 构建基线

把脚手架从 Spring Boot 4.1.1 降到 3.5.x，加入 Web 依赖，建立角度制枚举与配置绑定。

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Create: `src/main/java/com/wysjwxm/calculator/domain/AngleUnit.java`
- Create: `src/main/java/com/wysjwxm/calculator/infrastructure/config/CalculatorProperties.java`
- Test: `src/test/java/com/wysjwxm/calculator/infrastructure/config/CalculatorPropertiesTest.java`

**Interfaces:**
- Consumes: 无（本任务是起点）
- Produces:
  - `enum AngleUnit { DEGREE, RADIAN }`（`domain` 包）
  - `record CalculatorProperties(AngleUnit defaultAngleUnit, int maxExpressionLength, int divisionPrecision, int historyCapacity)`，访问器同名；附加方法 `boolean historyUnbounded()`

- [ ] **Step 1: 确认可用的 Spring Boot 3.5.x 版本**

运行：

```bash
curl -s "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml" \
  | grep -oE '<version>3\.5\.[0-9]+</version>' | tail -3
```

记下最新补丁版本号（下文以 `3.5.6` 为例）。**若该命令无输出或网络不可用，跳过此步，直接用 `3.5.6`；后续 `mvn` 命令若报版本不存在会自动暴露，届时换成实际存在的版本即可。**

- [ ] **Step 2: 修改 pom.xml**

把 `<parent>` 的 `<version>` 改为上一步确认的版本，并把 `spring-boot-starter` 替换为 `spring-boot-starter-web`：

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

- [ ] **Step 3: 验证依赖解析**

运行：`mvn -q -DskipTests dependency:resolve`

预期：BUILD SUCCESS。若报 `spring-boot-starter-parent:<版本>` 不存在，回到 Step 1 换成真实版本。

- [ ] **Step 4: 修改 application.yaml**

写入完整内容：

```yaml
spring:
  application:
    name: scientific-calculator
  mvc:
    throw-exception-if-no-handler-found: true
  web:
    resources:
      add-mappings: false

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

**后续如果 404 返回 500 而非 404**，回来检查 `spring.mvc.throw-exception-if-no-handler-found` 与 `spring.web.resources.add-mappings` 两项是否都在。

- [ ] **Step 5: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/infrastructure/config/CalculatorPropertiesTest.java`：

```java
package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.domain.AngleUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProps.class);

    @EnableConfigurationProperties(CalculatorProperties.class)
    static class EnableProps { }

    @Test
    void bindsValuesFromProperties() {
        runner.withPropertyValues(
                "calculator.default-angle-unit=RADIAN",
                "calculator.history-capacity=50",
                "calculator.max-expression-length=200",
                "calculator.division-precision=16")
             .run(ctx -> {
                 CalculatorProperties props = ctx.getBean(CalculatorProperties.class);
                 assertThat(props.defaultAngleUnit()).isEqualTo(AngleUnit.RADIAN);
                 assertThat(props.historyCapacity()).isEqualTo(50);
                 assertThat(props.maxExpressionLength()).isEqualTo(200);
                 assertThat(props.divisionPrecision()).isEqualTo(16);
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

    @Test
    void rejectsUnknownAngleUnit() {
        runner.withPropertyValues("calculator.default-angle-unit=GRADIANS")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void historyCapacityZeroMeansUnbounded() {
        runner.withPropertyValues("calculator.history-capacity=0")
              .run(ctx -> {
                  CalculatorProperties props = ctx.getBean(CalculatorProperties.class);
                  assertThat(props.historyUnbounded()).isTrue();
              });
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

运行：`mvn -q test -Dtest=CalculatorPropertiesTest`

预期：编译失败，`CalculatorProperties` / `AngleUnit` 不存在。

- [ ] **Step 7: 创建 AngleUnit**

创建 `src/main/java/com/wysjwxm/calculator/domain/AngleUnit.java`：

```java
package com.wysjwxm.calculator.domain;

/**
 * 三角函数的角度单位。仅对 sin/cos/tan/asin/acos/atan/atan2 生效，其余函数忽略。
 */
public enum AngleUnit {
    DEGREE,
    RADIAN
}
```

- [ ] **Step 8: 创建 CalculatorProperties**

创建 `src/main/java/com/wysjwxm/calculator/infrastructure/config/CalculatorProperties.java`：

```java
package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.domain.AngleUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 配置绑定类。属基础设施关切（它认识 Spring），因此放在 infrastructure 而非 domain。
 *
 * <p>它不直接注入到 application 层 —— 由同包的 CalculatorConfiguration 翻译成
 * 下游能用的 bean（Numbers / CalculationPolicy 等），避免 application 反向依赖
 * infrastructure。
 */
@Validated
@ConfigurationProperties(prefix = "calculator")
public record CalculatorProperties(
        @NotNull AngleUnit defaultAngleUnit,
        @Min(1) int maxExpressionLength,
        @Min(1) int divisionPrecision,
        int historyCapacity
) {
    /** historyCapacity 允许为 0 或负数，语义为「不限制容量」，因此不加 @Min 约束。 */
    public boolean historyUnbounded() {
        return historyCapacity <= 0;
    }
}
```

**注**：`jakarta.validation` 由 `spring-boot-starter-web` 的依赖链传递引入，无需额外声明。**若 Step 9 编译报找不到 `jakarta.validation.constraints`**，改为去掉这两个注解，在 record 的紧凑构造器中手工校验并抛 `IllegalArgumentException`（效果相同，都是启动期快速失败）。

- [ ] **Step 9: 运行测试确认通过**

运行：`mvn -q test -Dtest=CalculatorPropertiesTest`

预期：5 个测试全部 PASS。

- [ ] **Step 10: 运行全部测试**

运行：`mvn -q test`

预期：BUILD SUCCESS，含原有的 `ScientificCalculatorApplicationTests.contextLoads`。

- [ ] **Step 11: 提交**

```bash
git add pom.xml src/main/resources/application.yaml \
        src/main/java/com/wysjwxm/calculator/domain/AngleUnit.java \
        src/main/java/com/wysjwxm/calculator/infrastructure/config/CalculatorProperties.java \
        src/test/java/com/wysjwxm/calculator/infrastructure/config/CalculatorPropertiesTest.java
git commit -m "feat: 构建基线 — 降级至 Spring Boot 3.5.x、引入 web starter、新增配置绑定"
```

---

### Task 2: 领域错误词汇表

建立全项目统一的错误码与领域异常，**不含任何 HTTP 概念**——映射留给 Task 13。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/error/CalcErrorCode.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/error/CalcException.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/error/CalcExceptionTest.java`

**Interfaces:**
- Consumes: 无
- Produces:
  - `CalcErrorCode` 枚举，12 个常量（见 Global Constraints）
  - `CalcException extends RuntimeException`，方法 `CalcErrorCode code()`、`Integer position()`（无位置时为 `null`）
  - 静态工厂 `CalcException.of(CalcErrorCode code, String message)`、`CalcException.at(CalcErrorCode code, String message, int position)`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/error/CalcExceptionTest.java`：

```java
package com.wysjwxm.calculator.domain.error;

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

创建 `src/main/java/com/wysjwxm/calculator/domain/error/CalcErrorCode.java`：

```java
package com.wysjwxm.calculator.domain.error;

/**
 * 全项目统一的错误码词汇表。
 *
 * <p>刻意不携带 HTTP 状态码 —— 传输协议是接口层的关切，领域异常不该知道 HTTP
 * 的存在。映射由 interfaces 层的 ErrorStatusMapper 集中承担（见 spec §7.6）。
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

创建 `src/main/java/com/wysjwxm/calculator/domain/error/CalcException.java`：

```java
package com.wysjwxm.calculator.domain.error;

/**
 * 领域异常。携带错误码与可选的字符位置（仅语法类错误有位置）。
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
git add src/main/java/com/wysjwxm/calculator/domain/error/ \
        src/test/java/com/wysjwxm/calculator/domain/error/
git commit -m "feat: 领域错误码词汇表与异常基类"
```

---

### Task 3: 数值值对象

混合数值策略：四则运算走 `BigDecimal` 保精确，超越函数走 `double`。**`0.1 + 0.2` 必须精确等于 `0.3`。**

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/number/CalcNumber.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/number/DecimalNumber.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/number/FloatingNumber.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/number/Numbers.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/number/PrecisionTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/number/NumbersTest.java`

**Interfaces:**
- Consumes: `CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `sealed interface CalcNumber permits DecimalNumber, FloatingNumber`：`double toDouble()`、`BigDecimal toDecimal()`、`boolean isExact()`
  - `record DecimalNumber(BigDecimal value)`、`record FloatingNumber(double value)`
  - `Numbers`（领域服务，**实例类**）：构造器 `Numbers(int divisionPrecision)`；方法 `of(long)`、`of(BigDecimal)`、`floating(double)`、`add`、`subtract`、`multiply`、`divide`、`modulo`、`power`、`negate`、`factorial`（均接受并返回 `CalcNumber`）；静态 `void requireFinite(double v, String what)`

- [ ] **Step 1: 写精度失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/number/PrecisionTest.java`：

```java
package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本设计数值模型存在的理由：精确路径必须真的精确。
 * 这些断言用 equals 而非 delta 比较 —— delta 比较就等于放弃了精度保证。
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
        assertThat(result.toDouble()).isCloseTo(Math.sqrt(2),
                org.assertj.core.data.Offset.offset(1e-12));
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

- [ ] **Step 3: 创建 CalcNumber 与两个实现**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/number/CalcNumber.java`：

```java
package com.wysjwxm.calculator.domain.model.number;

import java.math.BigDecimal;

/**
 * 计算器数值。两条路径显式分开：
 * <ul>
 *   <li>{@link DecimalNumber} —— 四则运算路径，精确</li>
 *   <li>{@link FloatingNumber} —— 超越函数路径，存在浮点误差</li>
 * </ul>
 *
 * <p>保底不变量：结果只要落在 DecimalNumber 就永远精确；一旦沾了 FloatingNumber
 * 即存在浮点误差。此不变量在测试中固定。
 */
public sealed interface CalcNumber permits DecimalNumber, FloatingNumber {

    double toDouble();

    BigDecimal toDecimal();

    /** 是否落在精确路径上。 */
    boolean isExact();
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/number/DecimalNumber.java`：

```java
package com.wysjwxm.calculator.domain.model.number;

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

创建 `src/main/java/com/wysjwxm/calculator/domain/model/number/FloatingNumber.java`：

```java
package com.wysjwxm.calculator.domain.model.number;

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

- [ ] **Step 4: 创建 Numbers**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/number/Numbers.java`：

```java
package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 算术运算与类型提升的领域服务。所有提升规则集中在此，不散落到求值器里。
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
            BigDecimal dividend = a.toDecimal();
            BigDecimal divisor = b.toDecimal();
            try {
                // 能整除时给出精确结果，除不尽时才按配置精度截断
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
                    // 指数过大导致结果超出可表示范围（如 9^9^9），降级到 double 由 requireFinite 兜底
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
            throw CalcException.of(CalcErrorCode.DOMAIN_ERROR,
                    "阶乘只接受非负整数，实际为 " + n.toPlainString());
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

- [ ] **Step 5: 运行精度测试确认通过**

运行：`mvn -q test -Dtest=PrecisionTest`

预期：12 个测试全部 PASS。

**若 `powerWithNonNegativeIntegerExponentStaysExact` 失败**：检查 `stripTrailingZeros().scale() <= 0` 这个判据 —— 整数 `BigDecimal` 的 scale 应 ≤ 0。

- [ ] **Step 6: 写 Numbers 补充测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/number/NumbersTest.java`：

```java
package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
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
        // double 无法精确表示 9007199254740993，走 decimal 路径仍然精确
        CalcNumber result = numbers.add(
                numbers.of(new BigDecimal("9007199254740993")), numbers.of(1L));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("9007199254740994"));
    }

    @Test
    void hugePowerFallsBackToFloatingWithoutThrowingArithmeticException() {
        // 9^9 = 387420489 可装进 int，但 BigDecimal.pow 会因结果过大而抛 ArithmeticException
        CalcNumber nine = numbers.of(9L);
        CalcNumber exponent = numbers.power(nine, nine);
        assertThatThrownBy(() -> numbers.power(nine, exponent))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }
}
```

- [ ] **Step 7: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

**若 `hugePowerFallsBackToFloatingWithoutThrowingArithmeticException` 抛出的是 `ArithmeticException` 而非 `CalcException`**：说明 `Numbers.power` 的 `catch (ArithmeticException)` 没有正确降级 —— 检查降级路径是否走了 `floatingFinite`。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/number/ \
        src/test/java/com/wysjwxm/calculator/domain/model/number/
git commit -m "feat: 数值值对象 — CalcNumber 家族与混合精度算术领域服务"
```

---

### Task 4: 表达式原文值对象与词法分析

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/ExpressionText.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/TokenType.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/Token.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/Lexer.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/expression/ExpressionTextTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/expression/parse/LexerTest.java`

**Interfaces:**
- Consumes: `CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `record ExpressionText(String value)`，紧凑构造器校验：非 null、非空白、去首尾空白
  - `enum TokenType { NUMBER IDENT PLUS MINUS STAR SLASH PERCENT CARET BANG LPAREN RPAREN COMMA EOF }`
  - `record Token(TokenType type, String lexeme, int position)`
  - `Lexer(String input)` 方法 `List<Token> tokenize()`（末尾恒有 `EOF`，出错抛 `PARSE_ERROR` 带 position）

- [ ] **Step 1: 写 ExpressionText 失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/expression/ExpressionTextTest.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExpressionText 只校验语言层面的不变量（非空、非空白、去首尾空白）。
 * 长度上限是资源保护策略，由 CalculationUseCase 依据 CalculationPolicy 强制
 * —— 见 spec §4.3。
 */
class ExpressionTextTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new ExpressionText("  1+2  ").value()).isEqualTo("1+2");
    }

    @Test
    void keepsInnerWhitespace() {
        assertThat(new ExpressionText(" 1 + 2 ").value()).isEqualTo("1 + 2");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new ExpressionText(null))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new ExpressionText("   "))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> new ExpressionText(""))
                .isInstanceOf(CalcException.class);
    }

    @Test
    void veryLongExpressionIsAcceptedByTheValueObject() {
        // 长度不属值对象职责；这里断言它不因此报错
        String longExpression = "1+".repeat(5000) + "1";
        assertThat(new ExpressionText(longExpression).value()).hasSize(longExpression.length());
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=ExpressionTextTest`

预期：编译失败，`ExpressionText` 不存在。

- [ ] **Step 3: 创建 ExpressionText**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/ExpressionText.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

/**
 * 表达式原文的值对象：调用方提交的原始文本，仅去除首尾空白，不做规范化改写。
 *
 * <p>只承载**语言层面的不变量**。长度上限是资源保护策略（且来自可配置项），
 * 不属于这里的职责 —— 把它塞进构造器会让语言值对象依赖运行时配置。长度由
 * CalculationUseCase 依据 CalculationPolicy 强制。
 */
public record ExpressionText(String value) {

    public ExpressionText {
        if (value == null) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "expression 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "expression 不能为空白");
        }
        value = trimmed;
    }
}
```

- [ ] **Step 4: 运行确认通过**

运行：`mvn -q test -Dtest=ExpressionTextTest`

预期：6 个测试 PASS。

- [ ] **Step 5: 写词法失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/expression/parse/LexerTest.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
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
        assertThat(lex("1 + 22")).extracting(Token::position).containsExactly(0, 2, 4, 7);
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
    void doesNotSplitMinusOutOfScientificNotation() {
        assertThat(types("1e-3")).containsExactly(TokenType.NUMBER, TokenType.EOF);
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
    void loneDotIsRejected() {
        assertThatThrownBy(() -> lex("."))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(0));
    }

    @Test
    void emptyInputYieldsOnlyEof() {
        assertThat(types("")).containsExactly(TokenType.EOF);
    }
}
```

- [ ] **Step 6: 运行确认失败**

运行：`mvn -q test -Dtest=LexerTest`

预期：编译失败，`Lexer` 不存在。

- [ ] **Step 7: 创建 TokenType、Token、Lexer**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/TokenType.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.parse;

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

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/Token.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.parse;

/**
 * @param position 起始字符下标（0 基），供错误定位使用
 */
public record Token(TokenType type, String lexeme, int position) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/Lexer.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

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
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        // 指数部分：e/E 后必须紧跟可选符号 + 至少一位数字，否则报错
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int exponentStart = pos;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            } else {
                throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                        "科学计数法指数部分不完整", exponentStart);
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

- [ ] **Step 8: 运行全部测试**

运行：`mvn -q test -Dtest='ExpressionTextTest,LexerTest'`

预期：19 个测试全部 PASS。

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/expression/ \
        src/test/java/com/wysjwxm/calculator/domain/model/expression/
git commit -m "feat: 表达式原文值对象与词法分析器"
```

---

### Task 5: 算子值对象与算子表

建立算子优先级的**唯一事实来源**。解析器与 `/functions` 清单都从这里读，杜绝两处漂移。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/Fixity.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/Associativity.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/Operator.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/OperatorTable.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/expression/OperatorTableTest.java`

**Interfaces:**
- Consumes: `TokenType`（Task 4）
- Produces:
  - `enum Fixity { INFIX, PREFIX, POSTFIX }`、`enum Associativity { LEFT, RIGHT }`
  - `record Operator(String symbol, Fixity fixity, int precedence, Associativity associativity)`（`associativity` 对 PREFIX/POSTFIX 为 `null`）
  - `OperatorTable` 静态方法：`Optional<Operator> infix(TokenType)`、`Optional<Operator> prefix(TokenType)`、`Optional<Operator> postfix(TokenType)`、`List<Operator> all()`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/expression/OperatorTableTest.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.model.expression.parse.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OperatorTable 是算子优先级的唯一事实来源。这些断言把 spec §6.3 的三条规则
 * 钉死在数据上 —— 解析器读的就是这份数据。
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
        List<Operator> all = OperatorTable.all();
        assertThat(all).hasSize(9);
        assertThat(all.stream().filter(o -> o.symbol().equals("-"))).hasSize(2);
        assertThat(all.stream().filter(o -> o.symbol().equals("+"))).hasSize(2);
    }

    @Test
    void symbolsUseMathematicalNotationNotNames() {
        // 清单里出现的是 + 而不是 "add" —— 见 spec §12 D3/D8
        assertThat(OperatorTable.all()).extracting(Operator::symbol)
                .containsExactlyInAnyOrder("+", "+", "-", "-", "*", "/", "%", "^", "!");
    }

    @Test
    void prefixAndPostfixOperatorsHaveNoAssociativity() {
        assertThat(OperatorTable.prefix(TokenType.MINUS).orElseThrow().associativity()).isNull();
        assertThat(OperatorTable.postfix(TokenType.BANG).orElseThrow().associativity()).isNull();
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=OperatorTableTest`

预期：编译失败，`OperatorTable` 不存在。

- [ ] **Step 3: 创建枚举与 Operator**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/Fixity.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

public enum Fixity {
    INFIX,
    PREFIX,
    POSTFIX
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/Associativity.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

public enum Associativity {
    LEFT,
    RIGHT
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/Operator.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

/**
 * @param precedence    数值越大结合越紧
 * @param associativity 仅 INFIX 有意义；PREFIX / POSTFIX 为 null
 */
public record Operator(String symbol, Fixity fixity, int precedence, Associativity associativity) {
}
```

- [ ] **Step 4: 创建 OperatorTable**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/OperatorTable.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.model.expression.parse.TokenType;

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
}
```

- [ ] **Step 5: 运行确认通过**

运行：`mvn -q test -Dtest=OperatorTableTest`

预期：8 个测试 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/expression/Fixity.java \
        src/main/java/com/wysjwxm/calculator/domain/model/expression/Associativity.java \
        src/main/java/com/wysjwxm/calculator/domain/model/expression/Operator.java \
        src/main/java/com/wysjwxm/calculator/domain/model/expression/OperatorTable.java \
        src/test/java/com/wysjwxm/calculator/domain/model/expression/OperatorTableTest.java
git commit -m "feat: 算子值对象与算子表 — 优先级与结合性的唯一事实来源"
```

---

### Task 6: 表达式 AST 与解析器

**注意**：解析器必须**无状态** —— 游标封在每次调用创建的局部对象里，否则注册为 Spring 单例后会被并发请求互相踩踏（spec §4.3）。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/Expression.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/LiteralExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/VariableExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/UnaryExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/PostfixExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/BinaryExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/CallExpr.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/ExpressionParser.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/expression/ExpressionPrinter.java`（**测试辅助，放 test 源码**）
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/expression/parse/ExpressionParserTest.java`

**Interfaces:**
- Consumes: `Lexer`、`Token`、`TokenType`（Task 4）；`Operator`、`OperatorTable`、`Associativity`（Task 5）；`CalcNumber`、`DecimalNumber`（Task 3）；`ExpressionText`（Task 4）
- Produces:
  - `sealed interface Expression permits LiteralExpr, VariableExpr, UnaryExpr, PostfixExpr, BinaryExpr, CallExpr`
  - `record LiteralExpr(CalcNumber value)`、`record VariableExpr(String name, int position)`、`record UnaryExpr(Operator operator, Expression operand)`、`record PostfixExpr(Operator operator, Expression operand)`、`record BinaryExpr(Operator operator, Expression left, Expression right)`、`record CallExpr(String functionName, List<Expression> arguments, int position)`
  - `ExpressionParser`（**无状态**）：构造器 `ExpressionParser()`，方法 `Expression parse(ExpressionText text)`

- [ ] **Step 1: 写测试辅助 ExpressionPrinter**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/expression/ExpressionPrinter.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import java.util.stream.Collectors;

/**
 * 把 AST 打印成完全括号化的中缀文本，**仅用于测试断言与调试**，因此放在 test 源码。
 *
 * <p>每个二元运算都加括号，字符串本身就唯一确定了树的结构 —— 这让优先级与
 * 结合性的测试一眼可读，比逐层 getter 断言更不易看错。
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

- [ ] **Step 2: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/expression/parse/ExpressionParserTest.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.ExpressionPrinter;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionParserTest {

    private final ExpressionParser parser = new ExpressionParser();

    private String infix(String input) {
        return ExpressionPrinter.toInfix(parser.parse(new ExpressionText(input)));
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
    void factorialBindsTighterThanAddition() {
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
        LiteralExpr literal = (LiteralExpr) parser.parse(new ExpressionText("0.1"));
        assertThat(literal.value().isExact()).isTrue();
        assertThat(literal.value().toDecimal()).isEqualByComparingTo("0.1");
    }

    @Test
    void expressionTextIsTrimmedBeforeParsing() {
        assertThat(infix("  1+2  ")).isEqualTo("(1 + 2)");
    }

    // ---------- 错误与位置 ----------

    @Test
    void missingClosingParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("sin(30")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(7);
                });
    }

    @Test
    void unexpectedTokenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1 + * 2")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(4));
    }

    @Test
    void trailingOperatorReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1 +")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(3));
    }

    @Test
    void mismatchedParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("(1+2")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(4));
    }

    @Test
    void strayClosingParenIsRejected() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("(1+2))")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(5));
    }

    @Test
    void missingCallClosingParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("max(1,2")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(7));
    }

    @Test
    void trailingCommaInCallIsRejected() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("max(1,)")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(6));
    }

    // ---------- 无状态（并发安全） ----------

    @Test
    void parserIsStatelessAndSafeForConcurrentUse() throws Exception {
        // 同一个解析器实例被多线程共享 —— Spring 单例场景。若游标是实例字段，
        // 这里的断言会因线程互相踩踏而失败。
        int threads = 8;
        int perThread = 500;
        List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                .mapToObj(t -> (Callable<Boolean>) () -> {
                    for (int i = 0; i < perThread; i++) {
                        if (!infix("1+2*3").equals("(1 + (2 * 3))")) {
                            return false;
                        }
                        if (!infix("2^3^2").equals("(2 ^ (3 ^ 2))")) {
                            return false;
                        }
                        if (!infix("sin(30)+1").equals("(sin(30) + 1)")) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS))
                        .as("并发解析结果被污染 —— 解析器可能不是无状态的")
                        .isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
```

- [ ] **Step 3: 运行确认失败**

运行：`mvn -q test -Dtest=ExpressionParserTest`

预期：编译失败，`ExpressionParser` 不存在。

- [ ] **Step 4: 创建 AST 节点**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/Expression.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

/**
 * 表达式语法树。sealed 让求值器可以用穷尽的 switch 模式匹配，
 * 日后新增节点类型时编译器会强制所有求值分支同步更新。
 */
public sealed interface Expression
        permits LiteralExpr, VariableExpr, UnaryExpr, PostfixExpr, BinaryExpr, CallExpr {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/LiteralExpr.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;

public record LiteralExpr(CalcNumber value) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/VariableExpr.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

/**
 * @param position 标识符在原文中的起始下标，供「未定义变量」报错定位
 */
public record VariableExpr(String name, int position) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/UnaryExpr.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

public record UnaryExpr(Operator operator, Expression operand) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/PostfixExpr.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

public record PostfixExpr(Operator operator, Expression operand) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/BinaryExpr.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

public record BinaryExpr(Operator operator, Expression left, Expression right) implements Expression {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/CallExpr.java`：

```java
package com.wysjwxm.calculator.domain.model.expression;

import java.util.List;

/**
 * @param position 函数名在原文中的起始下标，供「未知函数」报错定位
 */
public record CallExpr(String functionName, List<Expression> arguments, int position)
        implements Expression {

    public CallExpr {
        arguments = List.copyOf(arguments);
    }
}
```

- [ ] **Step 5: 创建 ExpressionParser**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/parse/ExpressionParser.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.Associativity;
import com.wysjwxm.calculator.domain.model.expression.BinaryExpr;
import com.wysjwxm.calculator.domain.model.expression.CallExpr;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import com.wysjwxm.calculator.domain.model.expression.Operator;
import com.wysjwxm.calculator.domain.model.expression.OperatorTable;
import com.wysjwxm.calculator.domain.model.expression.PostfixExpr;
import com.wysjwxm.calculator.domain.model.expression.UnaryExpr;
import com.wysjwxm.calculator.domain.model.expression.VariableExpr;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 优先级爬升（precedence climbing）式递归下降解析器。
 *
 * <p><b>无状态。</b>解析所需的游标（tokens + index）封在每次调用创建的
 * {@link ParseRun} 里，因此本类可以被多线程安全共享，注册为 Spring 单例无风险。
 * 若把游标做成实例字段，并发请求会互相踩踏。
 *
 * <p>优先级与结合性全部来自 {@link OperatorTable}，解析器自身不含任何硬编码的
 * 优先级数值 —— 这样 /functions 能力清单（同样由 OperatorTable 生成）不可能
 * 与实际解析行为不一致。
 */
public final class ExpressionParser {

    public Expression parse(ExpressionText text) {
        return new ParseRun(text.value()).parseRoot();
    }

    /** 单次解析的运行状态。每次 parse 创建一个，不跨调用共享。 */
    private static final class ParseRun {

        /** 最松的结合层级，作为入口。 */
        private static final int MIN_PRECEDENCE = 0;

        private final List<Token> tokens;
        private int index;

        ParseRun(String input) {
            this.tokens = new Lexer(input).tokenize();
        }

        Expression parseRoot() {
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
                // 用算子自身优先级作为下界：一元负号(3)因此不会吞掉 ^(4)，得到 -2^2 == -4
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
}
```

- [ ] **Step 6: 运行测试确认通过**

运行：`mvn -q test -Dtest=ExpressionParserTest`

预期：全部 PASS（27 个）。

**若 `powerIsRightAssociative` 失败**：检查 `parseExpression` 中 `nextMin` 的计算 —— 右结合必须传 `op.precedence()` 而非 `+1`。

**若 `unaryMinusBindsLooserThanPower` 失败**：检查 `parseUnary` 是否用 `parseExpression(op.precedence())` 而非 `parseExpression(MIN_PRECEDENCE)`。

**若 `parserIsStatelessAndSafeForConcurrentUse` 失败**：说明 `tokens` / `index` 泄漏到了 `ExpressionParser` 实例字段上，把它们移回 `ParseRun`。

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/expression/ \
        src/test/java/com/wysjwxm/calculator/domain/model/expression/
git commit -m "feat: 表达式 AST 与无状态优先级爬升解析器，含并发安全测试"
```

---

### Task 7: 函数值对象与保留名

**注意**：接口方法必须叫 `functionName()` —— 枚举无法覆写 `Enum.name()`（spec §4.3）。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/MathematicalConstant.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/function/MathFunction.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/function/UnaryFunction.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/function/BinaryFunction.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/function/AngleUnits.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/function/FunctionRegistry.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/function/ReservedNames.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/MathematicalConstantTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/function/FunctionRegistryTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/function/ReservedNamesTest.java`

**Interfaces:**
- Consumes: `AngleUnit`（Task 1）、`CalcNumber`、`Numbers`（Task 3）、`CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `enum MathematicalConstant { PI, E }`：`String symbol()`、`CalcNumber value()`、静态 `Optional<CalcNumber> lookup(String)`、静态 `Set<String> names()`
  - `interface MathFunction`：`String functionName()`、`int arity()`、`boolean angleSensitive()`、`CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers)`
  - `enum UnaryFunction implements MathFunction`（23 个）、`enum BinaryFunction implements MathFunction`（5 个）
  - `FunctionRegistry`：`Optional<MathFunction> find(String)`、`List<String> unaryNames()`、`List<String> binaryNames()`
  - `ReservedNames`：`static ReservedNames standard()`、`boolean contains(String)`、`Set<String> values()`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/MathematicalConstantTest.java`：

```java
package com.wysjwxm.calculator.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MathematicalConstantTest {

    @Test
    void resolvesPiAndE() {
        assertThat(MathematicalConstant.lookup("pi").orElseThrow().toDouble())
                .isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(MathematicalConstant.lookup("e").orElseThrow().toDouble())
                .isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void unknownNameIsNotFound() {
        assertThat(MathematicalConstant.lookup("x")).isEmpty();
    }

    @Test
    void namesAreExactlyPiAndE() {
        assertThat(MathematicalConstant.names()).containsExactlyInAnyOrder("pi", "e");
    }

    @Test
    void symbolIsTheLowerCaseNameNotTheEnumIdentifier() {
        // Enum.name() 会返回 "PI"；语言里的常量名是小写 "pi"
        assertThat(MathematicalConstant.PI.symbol()).isEqualTo("pi");
        assertThat(MathematicalConstant.E.symbol()).isEqualTo("e");
    }
}
```

创建 `src/test/java/com/wysjwxm/calculator/domain/model/function/ReservedNamesTest.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedNamesTest {

    @Test
    void containsAllFunctionNamesAndConstants() {
        ReservedNames reserved = ReservedNames.standard();
        assertThat(reserved.contains("sin")).isTrue();
        assertThat(reserved.contains("sqrt")).isTrue();
        assertThat(reserved.contains("hypot")).isTrue();
        assertThat(reserved.contains("pi")).isTrue();
        assertThat(reserved.contains("e")).isTrue();
    }

    @Test
    void doesNotContainOrdinaryNames() {
        assertThat(ReservedNames.standard().contains("x")).isFalse();
        assertThat(ReservedNames.standard().contains("x_1")).isFalse();
        assertThat(ReservedNames.standard().contains("foo")).isFalse();
    }

    @Test
    void doesNotAdvertiseRedundantFunctionSpellings() {
        // 有中缀/后缀写法的运算不注册函数形式（spec §12 D3）
        ReservedNames reserved = ReservedNames.standard();
        assertThat(reserved.contains("pow")).isFalse();
        assertThat(reserved.contains("mod")).isFalse();
        assertThat(reserved.contains("fact")).isFalse();
    }

    @Test
    void sizeIsTwentyEightFunctionsPlusTwoConstants() {
        assertThat(ReservedNames.standard().values()).hasSize(23 + 5 + 2);
    }

    @Test
    void standardIsAStableSingleton() {
        assertThat(ReservedNames.standard()).isSameAs(ReservedNames.standard());
    }
}
```

创建 `src/test/java/com/wysjwxm/calculator/domain/model/function/FunctionRegistryTest.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionRegistryTest {

    private final FunctionRegistry registry = new FunctionRegistry();
    private final Numbers numbers = new Numbers(34);

    private CalcNumber apply(String name, AngleUnit unit, double... args) {
        List<CalcNumber> values = Arrays.stream(args).mapToObj(numbers::floating).toList();
        return registry.find(name).orElseThrow().apply(values, unit, numbers);
    }

    @Test
    void registersExactlyTwentyThreeUnaryAndFiveBinary() {
        assertThat(registry.unaryNames()).hasSize(23);
        assertThat(registry.binaryNames()).hasSize(5);
    }

    @Test
    void functionNameIsTheLanguageNameNotTheEnumIdentifier() {
        assertThat(registry.find("log10").orElseThrow().functionName()).isEqualTo("log10");
        assertThat(registry.find("log2").orElseThrow().functionName()).isEqualTo("log2");
        assertThat(registry.find("atan2").orElseThrow().functionName()).isEqualTo("atan2");
    }

    @Test
    void doesNotRegisterRedundantFunctionSpellings() {
        assertThat(registry.find("pow")).isEmpty();
        assertThat(registry.find("mod")).isEmpty();
        assertThat(registry.find("fact")).isEmpty();
    }

    @Test
    void unknownFunctionIsNotFound() {
        assertThat(registry.find("nope")).isEmpty();
    }

    // ---------- 角度单位 ----------

    @Test
    void degreeAndRadianGiveDifferentSineResults() {
        assertThat(apply("sin", AngleUnit.DEGREE, 30).toDouble())
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("sin", AngleUnit.RADIAN, 30).toDouble())
                .isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void degreesAreIgnoredByNonTrigonometricFunctions() {
        assertThat(apply("sqrt", AngleUnit.DEGREE, 4).toDouble()).isEqualTo(2.0);
        assertThat(apply("sqrt", AngleUnit.RADIAN, 4).toDouble()).isEqualTo(2.0);
    }

    @Test
    void inverseTrigRespectsAngleUnit() {
        assertThat(apply("asin", AngleUnit.DEGREE, 0.5).toDouble())
                .isCloseTo(30.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(apply("asin", AngleUnit.RADIAN, 0.5).toDouble())
                .isCloseTo(Math.asin(0.5), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void atan2RespectsAngleUnit() {
        assertThat(apply("atan2", AngleUnit.DEGREE, 1, 1).toDouble())
                .isCloseTo(45.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    // ---------- 各函数 ----------

    @Test
    void binaryFunctionsWork() {
        assertThat(apply("hypot", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(5.0);
        assertThat(apply("max", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(4.0);
        assertThat(apply("min", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(3.0);
        assertThat(apply("log", AngleUnit.RADIAN, 8, 2).toDouble())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
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
        assertThat(apply("log10", AngleUnit.RADIAN, 100).toDouble())
                .isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("log2", AngleUnit.RADIAN, 8).toDouble())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("cbrt", AngleUnit.RADIAN, 27).toDouble())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("cos", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(1.0);
        assertThat(apply("tanh", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(0.0);
        assertThat(apply("asinh", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(0.0);
        assertThat(apply("cosh", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(1.0);
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
    void asinAndAcosOutOfRangeAreDomainErrors() {
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

    @Test
    void atanhBoundariesAreAccepted() {
        assertThat(apply("atanh", AngleUnit.RADIAN, 0.5).toDouble())
                .isCloseTo(Math.atanh(0.5), org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 元数 ----------

    @Test
    void unaryFunctionRejectsWrongArity() {
        assertThatThrownBy(() -> registry.find("sin").orElseThrow()
                .apply(List.of(numbers.of(1L), numbers.of(2L)), AngleUnit.RADIAN, numbers))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void binaryFunctionRejectsWrongArity() {
        assertThatThrownBy(() -> registry.find("hypot").orElseThrow()
                .apply(List.of(numbers.of(1L)), AngleUnit.RADIAN, numbers))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void angleSensitivityFlagsAreCorrect() {
        assertThat(registry.find("sin").orElseThrow().angleSensitive()).isTrue();
        assertThat(registry.find("atan2").orElseThrow().angleSensitive()).isTrue();
        assertThat(registry.find("sqrt").orElseThrow().angleSensitive()).isFalse();
        assertThat(registry.find("log").orElseThrow().angleSensitive()).isFalse();
        assertThat(registry.find("sinh").orElseThrow().angleSensitive()).isFalse();
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest='MathematicalConstantTest,ReservedNamesTest,FunctionRegistryTest'`

预期：编译失败，相关类不存在。

- [ ] **Step 3: 创建 MathematicalConstant**

创建 `src/main/java/com/wysjwxm/calculator/domain/MathematicalConstant.java`：

```java
package com.wysjwxm.calculator.domain;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.FloatingNumber;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 内置保留常量。
 *
 * <p>这些名字属于语言的一部分，不是可被覆盖的缺省值 —— 用户变量不得使用它们
 * （见 spec §6.4）。若允许 pi = 3，则 sin(pi) 的含义会随写入操作静默改变。
 *
 * <p>用枚举而非 Map：常量集是编译期固定的，枚举让这一点显式，也让
 * {@link #names()} 可以静态求值，无需任何运行时容器。
 */
public enum MathematicalConstant {

    PI("pi", Math.PI),
    E("e", Math.E);

    private final String symbol;
    private final double rawValue;

    MathematicalConstant(String symbol, double rawValue) {
        this.symbol = symbol;
        this.rawValue = rawValue;
    }

    /** 语言中的常量名（小写）。注意不能用 {@code name()} —— 那会返回枚举标识符 "PI"。 */
    public String symbol() {
        return symbol;
    }

    public CalcNumber value() {
        return new FloatingNumber(rawValue);
    }

    public static Optional<CalcNumber> lookup(String name) {
        for (MathematicalConstant constant : values()) {
            if (constant.symbol.equals(name)) {
                return Optional.of(constant.value());
            }
        }
        return Optional.empty();
    }

    public static Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        Arrays.stream(values()).forEach(c -> names.add(c.symbol));
        return Set.copyOf(names);
    }
}
```

- [ ] **Step 4: 创建 MathFunction 接口**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/function/MathFunction.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.List;

/**
 * 领域函数。
 *
 * <p>取值方法刻意叫 {@code functionName()} 而非 {@code name()}：实现类是枚举，
 * 而 {@link Enum#name()} 是 final 的，无法覆写 —— 声明 {@code String name()}
 * 会编译失败，即便绕过也会返回枚举标识符（如 "LOG10"）而非语言中的函数名
 * （"log10"）。见 spec §4.3。
 */
public interface MathFunction {

    String functionName();

    int arity();

    /** 是否受角度单位影响（三角函数、反三角函数、atan2）。 */
    boolean angleSensitive();

    CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers);
}
```

- [ ] **Step 5: 创建 AngleUnits 与 UnaryFunction**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/function/AngleUnits.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;

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

创建 `src/main/java/com/wysjwxm/calculator/domain/model/function/UnaryFunction.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 23 个一元函数。表驱动而非 23 个类 —— 每个函数的差异只有四件事：
 * 语言名、是否受角度影响、定义域约束、double 实现。
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

    // 取绝对值、指数与对数
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
        UNIT_INTERVAL,        // asin / acos： -1 <= x <= 1
        AT_LEAST_ONE,         // acosh：      x >= 1
        OPEN_UNIT_INTERVAL    // atanh：      -1 < x < 1
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
    public String functionName() {
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

- [ ] **Step 6: 创建 BinaryFunction**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/function/BinaryFunction.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * 5 个二元函数 —— 只收录无法用中缀运算符表达的运算（spec §6.5）。
 * 幂与取余已有 ^ 与 % 两种中缀写法，因此不在此注册。
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

    BinaryFunction(String functionName, boolean angleSensitive,
                   DoubleBinaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.implementation = implementation;
    }

    @Override
    public String functionName() {
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
            // atan2 的返回值是角度，需要按单位换算结果；输入 y/x 本身无量纲
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

- [ ] **Step 7: 创建 FunctionRegistry 与 ReservedNames**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/function/FunctionRegistry.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 函数名 → 实现的查表。
 */
public final class FunctionRegistry {

    private final Map<String, MathFunction> byName = new LinkedHashMap<>();

    public FunctionRegistry() {
        for (UnaryFunction f : UnaryFunction.values()) {
            byName.put(f.functionName(), f);
        }
        for (BinaryFunction f : BinaryFunction.values()) {
            byName.put(f.functionName(), f);
        }
    }

    public Optional<MathFunction> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<String> unaryNames() {
        return Arrays.stream(UnaryFunction.values()).map(UnaryFunction::functionName).toList();
    }

    public List<String> binaryNames() {
        return Arrays.stream(BinaryFunction.values()).map(BinaryFunction::functionName).toList();
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/function/ReservedNames.java`：

```java
package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.MathematicalConstant;

import java.util.HashSet;
import java.util.Set;

/**
 * 保留名集合 = 函数名 ∪ 保留常量名（spec §6.4）。
 *
 * <p>注意它**不依赖 FunctionRegistry 实例**：函数集与常量集都是编译期固定的
 * 枚举，因此集合可以静态求值。这一点是 VariableName 能把校验完全放进构造器
 * 的前提 —— 见 spec §12 D13。
 */
public record ReservedNames(Set<String> values) {

    private static final ReservedNames STANDARD = new ReservedNames(compute());

    public ReservedNames {
        values = Set.copyOf(values);
    }

    public static ReservedNames standard() {
        return STANDARD;
    }

    public boolean contains(String name) {
        return values.contains(name);
    }

    private static Set<String> compute() {
        Set<String> names = new HashSet<>();
        for (UnaryFunction function : UnaryFunction.values()) {
            names.add(function.functionName());
        }
        for (BinaryFunction function : BinaryFunction.values()) {
            names.add(function.functionName());
        }
        names.addAll(MathematicalConstant.names());
        return names;
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

运行：`mvn -q test -Dtest='MathematicalConstantTest,ReservedNamesTest,FunctionRegistryTest'`

预期：全部 PASS。

**若 `ReservedNames` 抛出 `ExceptionInInitializerError`**：检查静态初始化顺序 —— `STANDARD` 依赖 `compute()`，而 `compute()` 读两个枚举，不应形成环。

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/MathematicalConstant.java \
        src/main/java/com/wysjwxm/calculator/domain/model/function/ \
        src/test/java/com/wysjwxm/calculator/domain/MathematicalConstantTest.java \
        src/test/java/com/wysjwxm/calculator/domain/model/function/
git commit -m "feat: 保留常量、函数值对象（23 一元 + 5 二元）与保留名集合"
```

---

### Task 8: 变量值对象（构造即校验）

**这是 DDD 落地最关键的一环**：保留名不变量由 `VariableName` 的构造器保证，不存在「某个入口忘了校验」的可能。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/variable/VariableName.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/variable/Variable.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/variable/VariableNameTest.java`

**Interfaces:**
- Consumes: `ReservedNames`（Task 7）、`CalcNumber`（Task 3）、`CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `record VariableName(String value)` —— 紧凑构造器校验：非 null、非空、长度 ≤ 64、匹配 `[A-Za-z_][A-Za-z0-9_]*`、**不在 `ReservedNames.standard()` 内**。全部失败抛 `INVALID_REQUEST`
  - `record Variable(VariableName name, CalcNumber value, Instant createdAt, Instant updatedAt)`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/variable/VariableNameTest.java`：

```java
package com.wysjwxm.calculator.domain.model.variable;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VariableName 是值对象，校验全在构造器里 —— 不合格的名字造不出对象。
 * 这些断言即 spec §6.4 保留名规则的可执行形式。
 */
class VariableNameTest {

    private void assertInvalidRequest(String name) {
        assertThatThrownBy(() -> new VariableName(name))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    // ---------- 合法名 ----------

    @Test
    void acceptsOrdinaryNames() {
        assertThatCode(() -> new VariableName("x")).doesNotThrowAnyException();
        assertThatCode(() -> new VariableName("x_1")).doesNotThrowAnyException();
        assertThatCode(() -> new VariableName("_tmp")).doesNotThrowAnyException();
        assertThatCode(() -> new VariableName("aVeryLongButLegalName")).doesNotThrowAnyException();
        assertThatCode(() -> new VariableName("X")).doesNotThrowAnyException();
    }

    @Test
    void preservesTheValue() {
        assertThat(new VariableName("x_1").value()).isEqualTo("x_1");
    }

    @Test
    void equalityIsByValue() {
        assertThat(new VariableName("x")).isEqualTo(new VariableName("x"));
        assertThat(new VariableName("x")).hasSameHashCodeAs(new VariableName("x"));
        assertThat(new VariableName("x")).isNotEqualTo(new VariableName("y"));
    }

    // ---------- 保留常量名 ----------

    @Test
    void rejectsReservedConstantNames() {
        assertInvalidRequest("pi");
        assertInvalidRequest("e");
    }

    @Test
    void reservedConstantRejectionMessageNamesTheOffender() {
        assertThatThrownBy(() -> new VariableName("pi"))
                .isInstanceOf(CalcException.class)
                .hasMessageContaining("pi");
    }

    // ---------- 函数名 ----------

    @Test
    void rejectsFunctionNames() {
        assertInvalidRequest("sin");
        assertInvalidRequest("sqrt");
        assertInvalidRequest("log");
        assertInvalidRequest("hypot");
    }

    @Test
    void acceptsRedundantSpellingsThatAreNotEvenFunctions() {
        // pow / mod / fact 从未注册为函数，因此不是保留名 —— 它们是合法的变量名
        assertThatCode(() -> new VariableName("pow")).doesNotThrowAnyException();
        assertThatCode(() -> new VariableName("mod")).doesNotThrowAnyException();
        assertThatCode(() -> new VariableName("fact")).doesNotThrowAnyException();
    }

    // ---------- 格式 ----------

    @Test
    void rejectsNamesNotStartingWithLetterOrUnderscore() {
        assertInvalidRequest("1abc");
        assertInvalidRequest("9_");
    }

    @Test
    void rejectsNamesWithIllegalCharacters() {
        assertInvalidRequest("a-b");
        assertInvalidRequest("a b");
        assertInvalidRequest("a.b");
        assertInvalidRequest("a+b");
        assertInvalidRequest("变量");
    }

    @Test
    void rejectsEmptyAndNull() {
        assertInvalidRequest("");
        assertInvalidRequest(null);
    }

    // ---------- 长度 ----------

    @Test
    void rejectsOverlongNames() {
        assertInvalidRequest("x".repeat(65));
    }

    @Test
    void acceptsNameAtExactlyTheLengthLimit() {
        assertThatCode(() -> new VariableName("x".repeat(64))).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=VariableNameTest`

预期：编译失败，`VariableName` 不存在。

- [ ] **Step 3: 创建 VariableName**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/variable/VariableName.java`：

```java
package com.wysjwxm.calculator.domain.model.variable;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.function.ReservedNames;

import java.util.regex.Pattern;

/**
 * 变量名的值对象。**构造即校验，无旁路。**
 *
 * <p>保留名规则若只放在应用服务的方法里，它就只是「记得调用才生效」的纪律 ——
 * 漏调一次，{"pi": 3} 就能绕过。做成值对象后，名字在构造时即完成校验，
 * 下游拿到的键必然合法 —— 不合格的名字根本造不出对象。不变量由类型系统保证，
 * 而非由调用者的自觉保证。
 *
 * <p>校验之所以能完全放在构造器里（不需要领域服务注入保留名集合），是因为
 * 保留名可通过 {@link ReservedNames#standard()} 静态求得 —— 函数集与常量集
 * 都是编译期固定的枚举。见 spec §12 D13。
 */
public record VariableName(String value) {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final int MAX_LENGTH = 64;

    public VariableName {
        if (value == null || value.isEmpty()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "变量名不能为空");
        }
        if (value.length() > MAX_LENGTH) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "变量名长度不得超过 " + MAX_LENGTH + "，实际为 " + value.length());
        }
        if (!PATTERN.matcher(value).matches()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "变量名必须以字母或下划线开头、仅含字母数字下划线: " + value);
        }
        if (ReservedNames.standard().contains(value)) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "变量名 " + value + " 是保留名（函数名或内置常量），不可使用");
        }
    }
}
```

- [ ] **Step 4: 创建 Variable**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/variable/Variable.java`：

```java
package com.wysjwxm.calculator.domain.model.variable;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.time.Instant;

/**
 * 变量实体。身份即 {@link VariableName}（名字变则不是同一个变量），
 * 带有创建与更新的生命周期。
 */
public record Variable(VariableName name, CalcNumber value, Instant createdAt, Instant updatedAt) {
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=VariableNameTest`

预期：14 个测试全部 PASS。

**若 `acceptsRedundantSpellingsThatAreNotEvenFunctions` 失败**：说明 `ReservedNames` 误把 `pow`/`mod`/`fact` 收进了集合 —— 它们从未注册为函数。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/variable/ \
        src/test/java/com/wysjwxm/calculator/domain/model/variable/
git commit -m "feat: 变量值对象与实体 — 保留名校验由类型系统保证"
```

---

### Task 9: 求值器

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/EvaluationContext.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/ExpressionEvaluator.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/AstInspection.java`
- Test: `src/test/java/com/wysjwxm/calculator/domain/model/expression/eval/ExpressionEvaluatorTest.java`

**Interfaces:**
- Consumes: 全部上游领域组件
- Produces:
  - `@FunctionalInterface interface EvaluationContext`：`Optional<CalcNumber> lookup(String name)`；静态 `EvaluationContext of(Map<String, CalcNumber>)`、`EvaluationContext empty()`
  - `ExpressionEvaluator(FunctionRegistry registry, Numbers numbers)`，方法 `CalcNumber evaluate(Expression expr, AngleUnit angleUnit, EvaluationContext context)`，访问器 `FunctionRegistry functionRegistry()`
  - `AstInspection.usesAngleSensitiveFunction(Expression expr, FunctionRegistry registry)` → `boolean`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/domain/model/expression/eval/ExpressionEvaluatorTest.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionEvaluatorTest {

    private final ExpressionParser parser = new ExpressionParser();
    private final FunctionRegistry registry = new FunctionRegistry();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(registry, new Numbers(34));

    private Expression parse(String input) {
        return parser.parse(new ExpressionText(input));
    }

    private CalcNumber eval(String input) {
        return evaluator.evaluate(parse(input), AngleUnit.DEGREE, EvaluationContext.empty());
    }

    private CalcNumber eval(String input, Map<String, CalcNumber> variables) {
        return evaluator.evaluate(parse(input), AngleUnit.DEGREE, EvaluationContext.of(variables));
    }

    private double num(String input) {
        return eval(input).toDouble();
    }

    // ---------- 优先级（spec §6.3 三条规则端到端验证） ----------

    @Test
    void arithmeticFollowsPrecedence() {
        assertThat(num("1+2*3")).isEqualTo(7.0);
        assertThat(num("(1+2)*3")).isEqualTo(9.0);
    }

    @Test
    void powerIsRightAssociative() {
        // 2^3^2 == 2^9 == 512；若左结合会得到 64
        assertThat(num("2^3^2")).isEqualTo(512.0);
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        assertThat(num("-2^2")).isEqualTo(-4.0);
    }

    @Test
    void factorialBindsTighterThanAddition() {
        assertThat(num("3!+1")).isEqualTo(7.0);
    }

    @Test
    void moduloAndUnaryPlusWork() {
        assertThat(num("7%3")).isEqualTo(1.0);
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
    void specAcceptanceExpression() {
        // spec §14 验收标准第 3 条
        assertThat(num("1 + 2 * sin(30) ^ 2"))
                .isCloseTo(1.5, org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 变量与常量 ----------

    @Test
    void resolvesVariablesFromContext() {
        Numbers numbers = new Numbers(34);
        assertThat(eval("x*2", Map.of("x", numbers.of(new BigDecimal("5")))).toDouble())
                .isEqualTo(10.0);
    }

    @Test
    void resolvesBuiltInConstants() {
        assertThat(num("pi")).isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(num("e")).isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void undefinedVariableThrows() {
        assertThatThrownBy(() -> eval("y + 1"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_VARIABLE);
    }

    @Test
    void variableNameThatLooksLikeAFunctionIsReportedAsUnknownVariable() {
        // sin(sin) 里的内层 sin 是裸标识符。它必须是 UNKNOWN_VARIABLE（求值期的
        // 未定义），而不是 INVALID_REQUEST —— 求值器按字符串查表，不做名字构造。
        assertThatThrownBy(() -> eval("sin(sin)"))
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
        assertThat(num("sqrt(sqrt(16))")).isEqualTo(2.0);
    }

    @Test
    void unknownFunctionThrows() {
        assertThatThrownBy(() -> eval("nope(1)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_FUNCTION);
    }

    @Test
    void wrongArityThrowsInvalidRequest() {
        assertThatThrownBy(() -> eval("sin(1,2)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    // ---------- 角度单位 ----------

    @Test
    void radianModeChangesTrigResults() {
        CalcNumber radian = evaluator.evaluate(parse("sin(30)"), AngleUnit.RADIAN,
                EvaluationContext.empty());
        assertThat(radian.toDouble())
                .isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 错误传播 ----------

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
    void overflowingPowerIsRejectedNotSilentlyInfinite() {
        assertThatThrownBy(() -> eval("9^9^9"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).code())
                        .isIn(CalcErrorCode.NON_FINITE_RESULT, CalcErrorCode.DOMAIN_ERROR));
    }

    // ---------- AST 内省 ----------

    @Test
    void detectsAngleSensitiveUsage() {
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("sin(30)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("1+2"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("sqrt(2)"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("1+sin(2)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("max(sin(1),2)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("-sin(1)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("sin(1)!"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("hypot(1,2)"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("atan2(1,2)"), registry)).isTrue();
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=ExpressionEvaluatorTest`

预期：编译失败，`ExpressionEvaluator` 不存在。

- [ ] **Step 3: 创建 EvaluationContext**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/EvaluationContext.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.Map;
import java.util.Optional;

/**
 * 求值期的变量来源。领域通过这个接口拿到变量，从而不必认识聚合根 ——
 * 依赖方向因此保持单向。
 *
 * <p>按**原始字符串**查找而非 {@code VariableName}：表达式里的标识符是任意
 * 词法单元，求值器不应因为 "sin(sin)" 里的内层 sin 而构造一个名字值对象
 * （那会抛出 INVALID_REQUEST，而正确的语义是 UNKNOWN_VARIABLE）。集合里的
 * 键在写入时已经过 VariableName 校验，因此这里按字符串查到的东西，
 * 必然是合法定义过的。
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

- [ ] **Step 4: 创建 ExpressionEvaluator**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/ExpressionEvaluator.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.MathematicalConstant;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.BinaryExpr;
import com.wysjwxm.calculator.domain.model.expression.CallExpr;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import com.wysjwxm.calculator.domain.model.expression.PostfixExpr;
import com.wysjwxm.calculator.domain.model.expression.UnaryExpr;
import com.wysjwxm.calculator.domain.model.expression.VariableExpr;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.function.MathFunction;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式 → CalcNumber 的领域服务。
 *
 * <p>sealed 的 Expression 让这个 switch 穷尽 —— 日后新增节点类型时编译器会
 * 强制这里同步更新，不会有节点被静默漏掉。
 */
public final class ExpressionEvaluator {

    private final FunctionRegistry functions;
    private final Numbers numbers;

    public ExpressionEvaluator(FunctionRegistry functions, Numbers numbers) {
        this.functions = functions;
        this.numbers = numbers;
    }

    /** 供用例做 AST 内省（判断是否用到角度敏感函数）。 */
    public FunctionRegistry functionRegistry() {
        return functions;
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
        // 先查用户变量，未命中则查保留常量（spec §6.4）。因两个集合互斥，
        // 这个顺序不影响结果，但保持与规范文字一致。
        return context.lookup(variable.name())
                .or(() -> MathematicalConstant.lookup(variable.name()))
                .orElseThrow(() -> CalcException.of(CalcErrorCode.UNKNOWN_VARIABLE,
                        "未定义的变量或常量: " + variable.name()));
    }

    private CalcNumber applyUnary(UnaryExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber operand = evaluate(expr.operand(), angleUnit, context);
        // 前缀 + 是恒等，- 是取负
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
        // 分派依据 OperatorTable 记录的中缀符号，避免与 TokenType 二次映射
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
                    function.functionName() + " 需要 " + function.arity() + " 个参数，实际收到 "
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

创建 `src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/AstInspection.java`：

```java
package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.model.expression.BinaryExpr;
import com.wysjwxm.calculator.domain.model.expression.CallExpr;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import com.wysjwxm.calculator.domain.model.expression.PostfixExpr;
import com.wysjwxm.calculator.domain.model.expression.UnaryExpr;
import com.wysjwxm.calculator.domain.model.expression.VariableExpr;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;

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

运行：`mvn -q test -Dtest=ExpressionEvaluatorTest`

预期：全部 PASS（18 个）。

**若 `variableNameThatLooksLikeAFunctionIsReportedAsUnknownVariable` 得到 `INVALID_REQUEST`**：说明求值器在查找时构造了 `VariableName` —— 改为按字符串查表（见 `EvaluationContext` 的注释）。

- [ ] **Step 7: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/expression/eval/ \
        src/test/java/com/wysjwxm/calculator/domain/model/expression/eval/
git commit -m "feat: 求值器领域服务与 AST 内省"
```

---

### Task 10: 计算历史聚合根

聚合根接口在领域层声明，内存实现在基础设施层。**淘汰策略是 FIFO 而非 LRU**（spec §8）。本任务是全项目唯一需要写锁的地方。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/calculation/Calculation.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/calculation/PageResult.java`
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/calculation/CalculationHistory.java`
- Create: `src/main/java/com/wysjwxm/calculator/infrastructure/InMemoryCalculationHistory.java`
- Test: `src/test/java/com/wysjwxm/calculator/infrastructure/InMemoryCalculationHistoryTest.java`

**Interfaces:**
- Consumes: `CalcNumber`（Task 3）、`AngleUnit`（Task 1）、`ExpressionText`（Task 4）
- Produces:
  - `record Calculation(long id, ExpressionText expression, CalcNumber result, AngleUnit angleUnit, double elapsedMs, Instant createdAt)`
  - `record PageResult<T>(List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext)`
  - `interface CalculationHistory`：`Calculation append(ExpressionText, CalcNumber, AngleUnit, double elapsedMs)`、`Optional<Calculation> find(long id)`、`PageResult<Calculation> page(int page, int size)`、`int clear()`、`int size()`
  - `InMemoryCalculationHistory implements CalculationHistory`，构造器 `InMemoryCalculationHistory(int capacity)`

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/infrastructure/InMemoryCalculationHistoryTest.java`：

```java
package com.wysjwxm.calculator.infrastructure;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.calculation.CalculationHistory;
import com.wysjwxm.calculator.domain.model.calculation.PageResult;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCalculationHistoryTest {

    private final Numbers numbers = new Numbers(34);

    private Calculation append(CalculationHistory history, String expr) {
        return history.append(new ExpressionText(expr), numbers.of(1L), AngleUnit.DEGREE, 0.5);
    }

    private CalculationHistory historyOf(int capacity) {
        return new InMemoryCalculationHistory(capacity);
    }

    // ---------- 基本行为 ----------

    @Test
    void appendAssignsMonotonicIdsStartingAtOne() {
        CalculationHistory history = historyOf(100);
        assertThat(append(history, "1+1").id()).isEqualTo(1L);
        assertThat(append(history, "2+2").id()).isEqualTo(2L);
        assertThat(append(history, "3+3").id()).isEqualTo(3L);
    }

    @Test
    void findByIdReturnsRecord() {
        CalculationHistory history = historyOf(100);
        Calculation appended = append(history, "sin(30)");
        assertThat(history.find(appended.id())).contains(appended);
    }

    @Test
    void findUnknownIdIsEmpty() {
        assertThat(historyOf(100).find(999L)).isEmpty();
    }

    @Test
    void storesExpressionTextAndAngleUnitVerbatim() {
        CalculationHistory history = historyOf(100);
        Calculation record = history.append(new ExpressionText("  sin(30)  "),
                numbers.of(1L), AngleUnit.RADIAN, 1.25);
        assertThat(record.expression().value()).isEqualTo("sin(30)");
        assertThat(record.angleUnit()).isEqualTo(AngleUnit.RADIAN);
        assertThat(record.elapsedMs()).isEqualTo(1.25);
    }

    // ---------- 分页 ----------

    @Test
    void pageIsNewestFirst() {
        CalculationHistory history = historyOf(100);
        append(history, "first");
        append(history, "second");
        append(history, "third");

        assertThat(history.page(0, 10).items())
                .extracting(c -> c.expression().value())
                .containsExactly("third", "second", "first");
    }

    @Test
    void pagingSlicesCorrectly() {
        CalculationHistory history = historyOf(100);
        for (int i = 1; i <= 5; i++) {
            append(history, "e" + i);
        }
        PageResult<Calculation> firstPage = history.page(0, 2);
        assertThat(firstPage.items()).extracting(c -> c.expression().value())
                .containsExactly("e5", "e4");
        assertThat(firstPage.page()).isEqualTo(0);
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.totalElements()).isEqualTo(5);
        assertThat(firstPage.totalPages()).isEqualTo(3);
        assertThat(firstPage.hasNext()).isTrue();

        PageResult<Calculation> lastPage = history.page(2, 2);
        assertThat(lastPage.items()).extracting(c -> c.expression().value()).containsExactly("e1");
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void pageBeyondEndReturnsEmptyItems() {
        CalculationHistory history = historyOf(100);
        append(history, "only");
        PageResult<Calculation> page = history.page(5, 10);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void emptyHistoryPagesCleanly() {
        PageResult<Calculation> page = historyOf(100).page(0, 10);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    // ---------- FIFO 容量淘汰 ----------

    @Test
    void capacityEvictsOldestFirst() {
        CalculationHistory history = historyOf(3);
        append(history, "e1");
        append(history, "e2");
        append(history, "e3");
        append(history, "e4");

        assertThat(history.size()).isEqualTo(3);
        assertThat(history.page(0, 10).items()).extracting(c -> c.expression().value())
                .containsExactly("e4", "e3", "e2");
        assertThat(history.find(1L)).isEmpty();
        // 淘汰不重置 id 序列
        assertThat(history.page(0, 1).items().get(0).id()).isEqualTo(4L);
    }

    @Test
    void zeroCapacityMeansUnbounded() {
        CalculationHistory history = historyOf(0);
        for (int i = 0; i < 50; i++) {
            append(history, "e" + i);
        }
        assertThat(history.size()).isEqualTo(50);
    }

    @Test
    void negativeCapacityMeansUnbounded() {
        CalculationHistory history = historyOf(-1);
        for (int i = 0; i < 50; i++) {
            append(history, "e" + i);
        }
        assertThat(history.size()).isEqualTo(50);
    }

    // ---------- 清空 ----------

    @Test
    void clearRemovesEverythingAndReportsCount() {
        CalculationHistory history = historyOf(100);
        append(history, "a");
        append(history, "b");
        assertThat(history.clear()).isEqualTo(2);
        assertThat(history.size()).isZero();
        assertThat(history.page(0, 10).totalElements()).isZero();
    }

    @Test
    void clearOnEmptyHistoryReturnsZero() {
        assertThat(historyOf(100).clear()).isZero();
    }

    // ---------- 并发 ----------

    @Test
    void concurrentAppendsNoneLostWhenUnbounded() throws Exception {
        CalculationHistory history = historyOf(0);
        int threads = 8;
        int perThread = 1000;
        runConcurrently(threads, () -> {
            for (int i = 0; i < perThread; i++) {
                append(history, "x");
            }
        });

        assertThat(history.size()).isEqualTo(threads * perThread);
        // id 必须唯一 —— 证明 id 分配没有竞态
        assertThat(history.page(0, history.size()).items())
                .extracting(Calculation::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void concurrentAppendsRespectCapacityExactly() throws Exception {
        int capacity = 500;
        CalculationHistory history = historyOf(capacity);
        runConcurrently(8, () -> {
            for (int i = 0; i < 500; i++) {
                append(history, "x");
            }
        });

        // 「追加 + 淘汰」必须原子，否则并发下 size 会瞬时超过 capacity
        assertThat(history.size()).isEqualTo(capacity);
    }

    @Test
    void concurrentReadsAndWritesDoNotFail() throws Exception {
        CalculationHistory history = historyOf(200);
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
                            append(history, "w");
                        } else {
                            history.page(0, 10);
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

        assertThat(history.size()).isLessThanOrEqualTo(200);
    }

    private void runConcurrently(int threads, Runnable body) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    body.run();
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
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=InMemoryCalculationHistoryTest`

预期：编译失败，`InMemoryCalculationHistory` 不存在。

- [ ] **Step 3: 创建领域层的三个类型**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/calculation/Calculation.java`：

```java
package com.wysjwxm.calculator.domain.model.calculation;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.time.Instant;

/**
 * 计算实体。身份是 id。
 *
 * @param angleUnit 仅当表达式用到了受角度单位影响的函数时才有值，否则为 null
 */
public record Calculation(long id, ExpressionText expression, CalcNumber result,
                          AngleUnit angleUnit, double elapsedMs, Instant createdAt) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/calculation/PageResult.java`：

```java
package com.wysjwxm.calculator.domain.model.calculation;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements,
                            int totalPages, boolean hasNext) {

    public PageResult {
        items = List.copyOf(items);
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/domain/model/calculation/CalculationHistory.java`：

```java
package com.wysjwxm.calculator.domain.model.calculation;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.Optional;

/**
 * 计算历史聚合根。
 *
 * <p>一致性边界：容量上限。领域层只声明契约，内存实现由基础设施层提供。
 * 之所以不额外引入 Repository 类型（聚合根与仓储合并），见 spec §12 D11。
 */
public interface CalculationHistory {

    Calculation append(ExpressionText expression, CalcNumber result, AngleUnit angleUnit,
                       double elapsedMs);

    Optional<Calculation> find(long id);

    /** 按 id 倒序（最新在前）分页。 */
    PageResult<Calculation> page(int page, int size);

    int clear();

    int size();
}
```

- [ ] **Step 4: 创建 InMemoryCalculationHistory**

创建 `src/main/java/com/wysjwxm/calculator/infrastructure/InMemoryCalculationHistory.java`：

```java
package com.wysjwxm.calculator.infrastructure;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.calculation.CalculationHistory;
import com.wysjwxm.calculator.domain.model.calculation.PageResult;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 计算历史聚合根的内存实现，带容量上限。
 *
 * <p><b>并发策略</b>：ArrayDeque + ReentrantReadWriteLock。用锁而非并发集合，
 * 是因为「追加 + 淘汰最旧」必须是一个原子步骤 —— 无锁的并发双端队列无法保证
 * 淘汰后 size 精确等于容量。
 *
 * <p><b>淘汰策略是 FIFO，不是 LRU</b>（spec §8）：历史只追加、不被复用，记录的
 * 价值随时间单调递减（最新最有用），FIFO 与这个语义天然吻合。而 LRU 需要在每次
 * 读时更新访问元数据，会把读操作退化为写操作，与下面「读锁可并发」的设计直接
 * 冲突 —— 而分页查询正是最热的路径。
 */
public class InMemoryCalculationHistory implements CalculationHistory {

    private final Deque<Calculation> records = new ArrayDeque<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicLong sequence = new AtomicLong(0);
    /** <= 0 表示不限制容量。 */
    private final int capacity;

    public InMemoryCalculationHistory(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public Calculation append(ExpressionText expression, CalcNumber result, AngleUnit angleUnit,
                              double elapsedMs) {
        Calculation record = new Calculation(sequence.incrementAndGet(), expression, result,
                angleUnit, elapsedMs, Instant.now());
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
    public Optional<Calculation> find(long id) {
        lock.readLock().lock();
        try {
            return records.stream().filter(r -> r.id() == id).findFirst();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public PageResult<Calculation> page(int page, int size) {
        lock.readLock().lock();
        try {
            long total = records.size();
            List<Calculation> snapshot = new ArrayList<>(records);
            int from = page * size;
            List<Calculation> items = from >= snapshot.size()
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

运行：`mvn -q test -Dtest=InMemoryCalculationHistoryTest`

预期：16 个测试全部 PASS。

**若 `concurrentAppendsRespectCapacityExactly` 失败**：说明「追加 + 淘汰」没有放在同一个写锁临界区内。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/calculation/ \
        src/main/java/com/wysjwxm/calculator/infrastructure/InMemoryCalculationHistory.java \
        src/test/java/com/wysjwxm/calculator/infrastructure/InMemoryCalculationHistoryTest.java
git commit -m "feat: 计算历史聚合根与内存实现 — FIFO 淘汰与读写锁并发"
```

---

### Task 11: 变量聚合根

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/domain/model/variable/VariableSet.java`
- Create: `src/main/java/com/wysjwxm/calculator/infrastructure/InMemoryVariableSet.java`
- Test: `src/test/java/com/wysjwxm/calculator/infrastructure/InMemoryVariableSetTest.java`

**Interfaces:**
- Consumes: `VariableName`、`Variable`（Task 8）、`CalcNumber`（Task 3）
- Produces:
  - `interface VariableSet`：`Variable define(VariableName name, CalcNumber value)`、`Optional<Variable> find(VariableName name)`、`List<Variable> all()`（按 name 升序）、`boolean remove(VariableName name)`、`int size()`
  - `InMemoryVariableSet implements VariableSet`，无参构造器

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/infrastructure/InMemoryVariableSetTest.java`：

```java
package com.wysjwxm.calculator.infrastructure;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.domain.model.variable.Variable;
import com.wysjwxm.calculator.domain.model.variable.VariableName;
import com.wysjwxm.calculator.domain.model.variable.VariableSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryVariableSetTest {

    private final VariableSet variables = new InMemoryVariableSet();

    private VariableName name(String raw) {
        return new VariableName(raw);
    }

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    @Test
    void defineThenFind() {
        variables.define(name("x"), num("5"));
        assertThat(variables.find(name("x")).orElseThrow().value().toDecimal())
                .isEqualByComparingTo("5");
    }

    @Test
    void unknownNameIsEmpty() {
        assertThat(variables.find(name("nope"))).isEmpty();
    }

    @Test
    void defineOverwritesAndKeepsCreatedAt() {
        Variable first = variables.define(name("x"), num("5"));
        Variable second = variables.define(name("x"), num("6"));
        assertThat(second.value().toDecimal()).isEqualByComparingTo("6");
        assertThat(second.createdAt()).isEqualTo(first.createdAt());
        assertThat(variables.size()).isEqualTo(1);
    }

    @Test
    void allIsSortedByName() {
        variables.define(name("zeta"), num("1"));
        variables.define(name("alpha"), num("2"));
        variables.define(name("mid"), num("3"));
        assertThat(variables.all()).extracting(v -> v.name().value())
                .containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void removeReportsWhetherAnythingWasRemoved() {
        variables.define(name("x"), num("5"));
        assertThat(variables.remove(name("x"))).isTrue();
        assertThat(variables.remove(name("x"))).isFalse();
        assertThat(variables.find(name("x"))).isEmpty();
    }

    @Test
    void allIsAnImmutableSnapshot() {
        variables.define(name("x"), num("5"));
        var snapshot = variables.all();
        assertThat(snapshot).hasSize(1);
        variables.define(name("y"), num("6"));
        assertThat(snapshot).hasSize(1);
    }

    // ---------- 并发 ----------

    @Test
    void concurrentWritesToDistinctKeysAllSurvive() throws Exception {
        int threads = 8;
        int perThread = 1000;
        runConcurrently(threads, id -> {
            for (int i = 0; i < perThread; i++) {
                variables.define(name("k" + id + "_" + i), num(String.valueOf(i)));
            }
        });
        assertThat(variables.size()).isEqualTo(threads * perThread);
    }

    @Test
    void concurrentOverwritesOfSameKeyLeaveExactlyOneValue() throws Exception {
        runConcurrently(8, id -> {
            for (int i = 0; i < 500; i++) {
                variables.define(name("shared"), num(String.valueOf(id)));
            }
        });
        assertThat(variables.size()).isEqualTo(1);
        assertThat(variables.find(name("shared"))).isPresent();
    }

    @Test
    void concurrentReadsAndWritesDoNotFail() throws Exception {
        variables.define(name("x"), num("1"));
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
                            variables.define(name("w" + i), num("1"));
                        } else {
                            variables.find(name("x"));
                            variables.all();
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
        assertThat(variables.size()).isGreaterThanOrEqualTo(1);
    }

    private void runConcurrently(int threads, IntConsumer body) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            final int id = t;
            pool.submit(() -> {
                try {
                    start.await();
                    body.accept(id);
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
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=InMemoryVariableSetTest`

预期：编译失败，`InMemoryVariableSet` 不存在。

- [ ] **Step 3: 创建 VariableSet 接口**

创建 `src/main/java/com/wysjwxm/calculator/domain/model/variable/VariableSet.java`：

```java
package com.wysjwxm.calculator.domain.model.variable;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.List;
import java.util.Optional;

/**
 * 变量表聚合根。
 *
 * <p>一致性边界：变量名不得为保留名。该不变量**不在本接口上强制** ——
 * 它由 {@link VariableName} 的构造器保证，因此任何传进来的名字都已合法。
 * 聚合根因此无需重复校验，这就是把规则放进值对象而非服务的好处。
 *
 * <p>方法名用 define 而非 put：语义是「定义（或重定义）一个变量」，
 * 覆盖是刻意支持的行为，而非副作用。
 */
public interface VariableSet {

    /** 幂等 upsert：已存在则整体替换，createdAt 保持不变。 */
    Variable define(VariableName name, CalcNumber value);

    Optional<Variable> find(VariableName name);

    /** 按 name 字典序升序返回全部。 */
    List<Variable> all();

    boolean remove(VariableName name);

    int size();
}
```

- [ ] **Step 4: 创建 InMemoryVariableSet**

创建 `src/main/java/com/wysjwxm/calculator/infrastructure/InMemoryVariableSet.java`：

```java
package com.wysjwxm.calculator.infrastructure;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.variable.Variable;
import com.wysjwxm.calculator.domain.model.variable.VariableName;
import com.wysjwxm.calculator.domain.model.variable.VariableSet;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 变量表聚合根的内存实现。
 *
 * <p>用 ConcurrentHashMap 而非加锁：变量是「按 name 独立」的数据，单键操作天然
 * 原子，无锁读让高频的表达式求值不必等待。
 */
public class InMemoryVariableSet implements VariableSet {

    private final ConcurrentMap<VariableName, Variable> variables = new ConcurrentHashMap<>();

    @Override
    public Variable define(VariableName name, CalcNumber value) {
        Instant now = Instant.now();
        // compute 让「读旧 createdAt + 写新记录」成为单键原子操作
        return variables.compute(name, (key, existing) -> new Variable(
                key, value, existing == null ? now : existing.createdAt(), now));
    }

    @Override
    public Optional<Variable> find(VariableName name) {
        return Optional.ofNullable(variables.get(name));
    }

    @Override
    public List<Variable> all() {
        return variables.values().stream()
                .sorted(Comparator.comparing(v -> v.name().value()))
                .toList();
    }

    @Override
    public boolean remove(VariableName name) {
        return variables.remove(name) != null;
    }

    @Override
    public int size() {
        return variables.size();
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

运行：`mvn -q test -Dtest=InMemoryVariableSetTest`

预期：9 个测试全部 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/domain/model/variable/VariableSet.java \
        src/main/java/com/wysjwxm/calculator/infrastructure/InMemoryVariableSet.java \
        src/test/java/com/wysjwxm/calculator/infrastructure/InMemoryVariableSetTest.java
git commit -m "feat: 变量表聚合根与内存实现 — ConcurrentHashMap 无锁读"
```

---

### Task 12: 应用层用例与 Spring 装配

应用层只做编排，不含业务规则。**所有 Bean 装配集中在 `CalculatorConfiguration`** —— 领域类不能加 Spring 注解。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/application/CalculationPolicy.java`
- Create: `src/main/java/com/wysjwxm/calculator/application/CalculationCommand.java`
- Create: `src/main/java/com/wysjwxm/calculator/application/CalculationUseCase.java`
- Create: `src/main/java/com/wysjwxm/calculator/application/VariableUseCase.java`
- Create: `src/main/java/com/wysjwxm/calculator/application/HistoryUseCase.java`
- Create: `src/main/java/com/wysjwxm/calculator/infrastructure/config/CalculatorConfiguration.java`
- Test: `src/test/java/com/wysjwxm/calculator/application/CalculationUseCaseTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/application/VariableUseCaseTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/application/HistoryUseCaseTest.java`

**Interfaces:**
- Consumes: 全部领域组件（Task 1–11）
- Produces:
  - `record CalculationPolicy(AngleUnit defaultAngleUnit, int maxExpressionLength)`
  - `record CalculationCommand(String expression, AngleUnit angleUnit, Map<String, CalcNumber> variables)`，静态工厂 `of(String expression)`
  - `CalculationUseCase`：`Calculation calculate(CalculationCommand command)`
  - `VariableUseCase`：`Variable define(String rawName, CalcNumber value)`、`Variable get(String rawName)`、`List<Variable> list()`、`void remove(String rawName)`
  - `HistoryUseCase`：`PageResult<Calculation> page(int page, int size)`、`Calculation get(long id)`、`int clear()`
  - `CalculatorConfiguration`（`@Configuration`）：产出 `Numbers`、`FunctionRegistry`、`ExpressionParser`、`ExpressionEvaluator`、`CalculationPolicy`、`VariableSet`、`CalculationHistory`、`CalculationUseCase`、`VariableUseCase`、`HistoryUseCase` 十个 Bean

- [ ] **Step 1: 写 CalculationUseCase 失败测试**

创建 `src/test/java/com/wysjwxm/calculator/application/CalculationUseCaseTest.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import com.wysjwxm.calculator.domain.model.variable.VariableName;
import com.wysjwxm.calculator.infrastructure.InMemoryCalculationHistory;
import com.wysjwxm.calculator.infrastructure.InMemoryVariableSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculationUseCaseTest {

    private final FunctionRegistry registry = new FunctionRegistry();
    private final Numbers numbers = new Numbers(34);
    private final InMemoryCalculationHistory history = new InMemoryCalculationHistory(100);
    private final InMemoryVariableSet variables = new InMemoryVariableSet();
    private final CalculationPolicy policy = new CalculationPolicy(AngleUnit.DEGREE, 1000);
    private final CalculationUseCase useCase = new CalculationUseCase(
            new ExpressionParser(), new ExpressionEvaluator(registry, numbers), history, policy);

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    private Calculation calc(String expression) {
        return useCase.calculate(CalculationCommand.of(expression));
    }

    // ---------- 基本求值与落库 ----------

    @Test
    void evaluatesAndRecordsHistory() {
        Calculation record = calc("1+2");
        assertThat(record.result().toDecimal()).isEqualByComparingTo("3");
        assertThat(record.expression().value()).isEqualTo("1+2");
        assertThat(history.size()).isEqualTo(1);
    }

    @Test
    void historyRecordsElapsedTime() {
        assertThat(calc("1+2").elapsedMs()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void historyIdsIncrease() {
        Calculation first = calc("1+1");
        Calculation second = calc("2+2");
        assertThat(second.id()).isGreaterThan(first.id());
    }

    @Test
    void expressionIsTrimmedBeforeStoring() {
        assertThat(calc("  1+2  ").expression().value()).isEqualTo("1+2");
    }

    @Test
    void failedEvaluationDoesNotWriteHistory() {
        assertThatThrownBy(() -> calc("1/0")).isInstanceOf(CalcException.class);
        assertThat(history.size()).isZero();
    }

    @Test
    void parseErrorCarriesPosition() {
        assertThatThrownBy(() -> calc("1+"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(2));
    }

    // ---------- 策略：表达式长度 ----------

    @Test
    void rejectsExpressionExceedingPolicyLimit() {
        CalculationPolicy tightPolicy = new CalculationPolicy(AngleUnit.DEGREE, 10);
        CalculationUseCase tight = new CalculationUseCase(
                new ExpressionParser(), new ExpressionEvaluator(registry, numbers), history, tightPolicy);
        assertThatThrownBy(() -> tight.calculate(CalculationCommand.of("1+".repeat(20) + "1")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
                    assertThat(ce.getMessage()).contains("10");
                });
    }

    @Test
    void rejectsNullAndBlankExpression() {
        assertThatThrownBy(() -> calc(null)).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> calc("   ")).isInstanceOf(CalcException.class);
    }

    // ---------- 角度单位 ----------

    @Test
    void defaultsToPolicyAngleUnitWhenCommandOmitsIt() {
        Calculation record = calc("sin(30)");
        assertThat(record.result().toDouble())
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(record.angleUnit()).isEqualTo(AngleUnit.DEGREE);
    }

    @Test
    void commandAngleUnitOverridesPolicy() {
        Calculation record = useCase.calculate(
                new CalculationCommand("sin(30)", AngleUnit.RADIAN, Map.of()));
        assertThat(record.result().toDouble())
                .isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
        assertThat(record.angleUnit()).isEqualTo(AngleUnit.RADIAN);
    }

    @Test
    void angleUnitIsNullForNonTrigonometricExpressions() {
        // spec §7.3：非三角记录该字段为 null
        assertThat(calc("1+2").angleUnit()).isNull();
        assertThat(calc("sqrt(16)").angleUnit()).isNull();
        assertThat(calc("hypot(3,4)").angleUnit()).isNull();
        assertThat(calc("atan2(1,1)").angleUnit()).isNotNull();
    }

    // ---------- 请求级变量 ----------

    @Test
    void requestVariablesAreUsable() {
        Calculation record = useCase.calculate(
                new CalculationCommand("x*2", null, Map.of("x", num("7"))));
        assertThat(record.result().toDecimal()).isEqualByComparingTo("14");
    }

    @Test
    void requestVariablesDoNotPersist() {
        useCase.calculate(new CalculationCommand("x*2", null, Map.of("x", num("7"))));
        assertThat(variables.size()).isZero();
    }

    @Test
    void requestVariablesRejectReservedNames() {
        // spec §7.1：请求级变量同样受禁用集合约束，不为「临时、不落库」开口子
        assertThatThrownBy(() -> useCase.calculate(
                new CalculationCommand("sin(pi)", null, Map.of("pi", num("3")))))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
                    assertThat(ce.getMessage()).contains("pi");
                });
    }

    @Test
    void requestVariablesRejectFunctionNames() {
        assertThatThrownBy(() -> useCase.calculate(
                new CalculationCommand("1+1", null, Map.of("sin", num("3")))))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void requestVariablesRejectMalformedNames() {
        assertThatThrownBy(() -> useCase.calculate(
                new CalculationCommand("1+1", null, Map.of("1abc", num("3")))))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectedRequestVariablesLeaveNoTrace() {
        assertThatThrownBy(() -> useCase.calculate(
                new CalculationCommand("sin(pi)", null, Map.of("pi", num("3")))))
                .isInstanceOf(CalcException.class);
        assertThat(history.size()).isZero();
        // pi 仍是常量
        assertThat(calc("pi").result().toDouble())
                .isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void commandOfHelperProducesEmptyVariables() {
        assertThat(CalculationCommand.of("1+1").variables()).isEmpty();
        assertThat(CalculationCommand.of("1+1").angleUnit()).isNull();
    }

    @Test
    void commandVariablesAreDefensivelyCopied() {
        var mutable = new java.util.HashMap<String, CalcNumber>();
        mutable.put("x", num("5"));
        CalculationCommand command = new CalculationCommand("x", null, mutable);
        mutable.put("y", num("6"));
        assertThat(command.variables()).containsOnlyKeys("x");
    }

    @Test
    void nullVariablesAreTreatedAsEmpty() {
        Calculation record = useCase.calculate(new CalculationCommand("1+1", null, null));
        assertThat(record.result().toDecimal()).isEqualByComparingTo("2");
    }

    // ---------- 与聚合根的协作 ----------

    @Test
    void storedVariableIsVisibleToEvaluation() {
        variables.define(new VariableName("stored"), num("11"));
        Calculation record = useCase.calculate(
                new CalculationCommand("stored + x", null, Map.of("x", num("1"))));
        assertThat(record.result().toDecimal()).isEqualByComparingTo("12");
    }
}
```

**注**：「存储变量」与「请求级变量」的合并由 `interfaces` 层的 Controller 完成（它同时持有 `VariableUseCase` 与 `CalculationUseCase`，见 Task 14）。上面最后一条测试注入的是 `InMemoryVariableSet`，只用于验证用例本身的行为边界 —— 真正把已存变量喂进求值是 `CalculatorController` 的职责。

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=CalculationUseCaseTest`

预期：编译失败，`CalculationUseCase` 不存在。

- [ ] **Step 3: 创建 CalculationPolicy 与 CalculationCommand**

创建 `src/main/java/com/wysjwxm/calculator/application/CalculationPolicy.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;

/**
 * 应用层策略：来自配置、但由用例施加的取值。
 *
 * <p>纯 Java record，无任何框架注解 —— 这样 application 层不必 import
 * infrastructure 里的 CalculatorProperties，依赖方向不被配置类破口。
 *
 * @param maxExpressionLength 表达式长度上限。它是资源保护策略而非语言不变量，
 *                            因此不进 ExpressionText 的构造器，在此处强制。
 */
public record CalculationPolicy(AngleUnit defaultAngleUnit, int maxExpressionLength) {

    public CalculationPolicy {
        if (defaultAngleUnit == null) {
            throw new IllegalArgumentException("defaultAngleUnit 不能为空");
        }
        if (maxExpressionLength < 1) {
            throw new IllegalArgumentException("maxExpressionLength 必须为正数: " + maxExpressionLength);
        }
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/application/CalculationCommand.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.Map;

/**
 * 求值用例的入参。由接口层（防腐层）从 JSON 翻译而来。
 *
 * <p>注意 {@code variables} 的键仍是原始字符串 —— 翻译成 {@code VariableName}
 * 是**用例的职责**，这样保留名校验只有一条通道，两个入口共用。
 *
 * @param angleUnit null 表示未指定，取策略缺省值
 * @param variables 求值期可见的变量（由接口层把已存变量与请求级临时变量合并后传入）
 */
public record CalculationCommand(String expression, AngleUnit angleUnit,
                                 Map<String, CalcNumber> variables) {

    public CalculationCommand {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public static CalculationCommand of(String expression) {
        return new CalculationCommand(expression, null, Map.of());
    }
}
```

- [ ] **Step 4: 创建 CalculationUseCase**

创建 `src/main/java/com/wysjwxm/calculator/application/CalculationUseCase.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.calculation.CalculationHistory;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.eval.AstInspection;
import com.wysjwxm.calculator.domain.model.expression.eval.EvaluationContext;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.variable.VariableName;

import java.util.HashMap;
import java.util.Map;

/**
 * 求值用例：编排「校验策略 → 解析 → 求值 → 落历史」。
 *
 * <p>本类不含业务规则 —— 算术、优先级、定义域、函数语义都在领域层；
 * 保留名校验在 {@link VariableName} 的构造器里。这里只做编排与策略施加。
 */
public class CalculationUseCase {

    private final ExpressionParser parser;
    private final ExpressionEvaluator evaluator;
    private final CalculationHistory history;
    private final CalculationPolicy policy;

    public CalculationUseCase(ExpressionParser parser, ExpressionEvaluator evaluator,
                              CalculationHistory history, CalculationPolicy policy) {
        this.parser = parser;
        this.evaluator = evaluator;
        this.history = history;
        this.policy = policy;
    }

    public Calculation calculate(CalculationCommand command) {
        ExpressionText text = buildExpressionText(command.expression());
        Map<String, CalcNumber> variables = validatedVariables(command.variables());
        AngleUnit angleUnit = command.angleUnit() != null
                ? command.angleUnit()
                : policy.defaultAngleUnit();

        long startedAt = System.nanoTime();
        Expression ast = parser.parse(text);
        EvaluationContext context = variables.isEmpty()
                ? EvaluationContext.empty()
                : EvaluationContext.of(variables);
        CalcNumber result = evaluator.evaluate(ast, angleUnit, context);
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        // angleUnit 仅在表达式真的用到了角度相关函数时才有记录价值（spec §7.3）
        AngleUnit recordedUnit = AstInspection.usesAngleSensitiveFunction(
                ast, evaluator.functionRegistry())
                ? angleUnit
                : null;

        return history.append(text, result, recordedUnit, elapsedMs);
    }

    /**
     * 表达式长度上限在此强制：它是来自配置的资源保护策略，不是语言不变量，
     * 因此不属于 ExpressionText 的构造器职责。
     */
    private ExpressionText buildExpressionText(String raw) {
        ExpressionText text = new ExpressionText(raw);
        int limit = policy.maxExpressionLength();
        if (text.value().length() > limit) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "expression 长度不得超过 " + limit + "，实际为 " + text.value().length());
        }
        return text;
    }

    /**
     * 变量名走 VariableName 构造器 —— 与存储写入完全相同的通道。不为「临时、
     * 不落库」开口子：同一表达式在不同入口下对 pi 的含义必须一致。
     */
    private Map<String, CalcNumber> validatedVariables(Map<String, CalcNumber> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, CalcNumber> validated = new HashMap<>();
        for (Map.Entry<String, CalcNumber> entry : raw.entrySet()) {
            VariableName name = new VariableName(entry.getKey());
            validated.put(name.value(), entry.getValue());
        }
        return validated;
    }
}
```

- [ ] **Step 5: 运行确认通过**

运行：`mvn -q test -Dtest=CalculationUseCaseTest`

预期：21 个测试 PASS。

**若 `angleUnitIsNullForNonTrigonometricExpressions` 的 `hypot(3,4)` 断言失败**：检查 `BinaryFunction.HYPOT` 的 `angleSensitive()` 是否为 `false`。

- [ ] **Step 6: 写 VariableUseCase 与 HistoryUseCase 失败测试**

创建 `src/test/java/com/wysjwxm/calculator/application/VariableUseCaseTest.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.infrastructure.InMemoryVariableSet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableUseCaseTest {

    private final VariableUseCase useCase = new VariableUseCase(new InMemoryVariableSet());

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    @Test
    void defineThenGet() {
        useCase.define("x", num("5"));
        assertThat(useCase.get("x").value().toDecimal()).isEqualByComparingTo("5");
    }

    @Test
    void getUnknownThrowsNotFound() {
        assertThatThrownBy(() -> useCase.get("nope"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.VARIABLE_NOT_FOUND);
    }

    @Test
    void defineIsIdempotentUpsert() {
        useCase.define("x", num("5"));
        useCase.define("x", num("6"));
        assertThat(useCase.list()).hasSize(1);
        assertThat(useCase.get("x").value().toDecimal()).isEqualByComparingTo("6");
    }

    @Test
    void removeUnknownThrowsNotFound() {
        assertThatThrownBy(() -> useCase.remove("nope"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.VARIABLE_NOT_FOUND);
    }

    @Test
    void removeWorks() {
        useCase.define("x", num("5"));
        useCase.remove("x");
        assertThat(useCase.list()).isEmpty();
    }

    @Test
    void listIsSortedByName() {
        useCase.define("zeta", num("1"));
        useCase.define("alpha", num("2"));
        assertThat(useCase.list()).extracting(v -> v.name().value())
                .containsExactly("alpha", "zeta");
    }

    // ---------- 保留名（spec §6.4） ----------

    @Test
    void rejectsReservedConstantNames() {
        assertThatThrownBy(() -> useCase.define("pi", num("3")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
                    assertThat(ce.getMessage()).contains("pi");
                });
        assertThatThrownBy(() -> useCase.define("e", num("3")))
                .isInstanceOf(CalcException.class);
    }

    @Test
    void rejectsFunctionNames() {
        assertThatThrownBy(() -> useCase.define("sin", num("3")))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsMalformedNames() {
        assertThatThrownBy(() -> useCase.define("1abc", num("3"))).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> useCase.define("a-b", num("3"))).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> useCase.define("", num("3"))).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> useCase.define(null, num("3"))).isInstanceOf(CalcException.class);
    }

    @Test
    void rejectsOverlongNames() {
        assertThatThrownBy(() -> useCase.define("x".repeat(65), num("3")))
                .isInstanceOf(CalcException.class);
    }

    @Test
    void rejectionAlsoAppliesToReadAndDelete() {
        // 保留名在语言里不存在，因此这些请求本身就是非法的，而非「找不到」
        assertThatThrownBy(() -> useCase.get("pi"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> useCase.remove("sin"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }
}
```

创建 `src/test/java/com/wysjwxm/calculator/application/HistoryUseCaseTest.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import com.wysjwxm.calculator.infrastructure.InMemoryCalculationHistory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HistoryUseCaseTest {

    private final InMemoryCalculationHistory history = new InMemoryCalculationHistory(100);
    private final HistoryUseCase useCase = new HistoryUseCase(history);
    private final Numbers numbers = new Numbers(34);

    @Test
    void pageRejectsNegativePage() {
        assertThatThrownBy(() -> useCase.page(-1, 10))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void pageRejectsSizeOutOfRange() {
        assertThatThrownBy(() -> useCase.page(0, 0)).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> useCase.page(0, 101)).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> useCase.page(0, -5)).isInstanceOf(CalcException.class);
    }

    @Test
    void pageAcceptsBoundarySizes() {
        assertThat(useCase.page(0, 1).size()).isEqualTo(1);
        assertThat(useCase.page(0, 100).size()).isEqualTo(100);
    }

    @Test
    void getUnknownThrowsNotFound() {
        assertThatThrownBy(() -> useCase.get(42L))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.HISTORY_NOT_FOUND);
    }

    @Test
    void getReturnsRecord() {
        var record = history.append(new ExpressionText("1+1"), numbers.of(2L), null, 0.1);
        assertThat(useCase.get(record.id())).isEqualTo(record);
    }

    @Test
    void clearReportsCount() {
        history.append(new ExpressionText("1+1"), numbers.of(1L), null, 0.1);
        history.append(new ExpressionText("2+2"), numbers.of(1L), null, 0.1);
        assertThat(useCase.clear()).isEqualTo(2);
    }

    @Test
    void clearOnEmptyReturnsZero() {
        assertThat(useCase.clear()).isZero();
    }
}
```

- [ ] **Step 7: 运行确认失败**

运行：`mvn -q test -Dtest='VariableUseCaseTest,HistoryUseCaseTest'`

预期：编译失败，两个用例类不存在。

- [ ] **Step 8: 创建其余两个用例**

创建 `src/main/java/com/wysjwxm/calculator/application/VariableUseCase.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.variable.Variable;
import com.wysjwxm.calculator.domain.model.variable.VariableName;
import com.wysjwxm.calculator.domain.model.variable.VariableSet;

import java.util.List;
import java.util.Map;

/**
 * 变量用例：编排变量读写。
 *
 * <p>原始字符串 → {@link VariableName} 的翻译集中在本类。加上
 * {@link CalculationUseCase} 里对变量的同款翻译，两条入口都收敛到值对象的
 * 构造器上 —— 校验不在入口处，在类型里。
 */
public class VariableUseCase {

    private final VariableSet variables;

    public VariableUseCase(VariableSet variables) {
        this.variables = variables;
    }

    public Variable define(String rawName, CalcNumber value) {
        return variables.define(new VariableName(rawName), value);
    }

    public Variable get(String rawName) {
        VariableName name = new VariableName(rawName);
        return variables.find(name).orElseThrow(() -> CalcException.of(
                CalcErrorCode.VARIABLE_NOT_FOUND, "变量不存在: " + name.value()));
    }

    public List<Variable> list() {
        return variables.all();
    }

    public void remove(String rawName) {
        VariableName name = new VariableName(rawName);
        if (!variables.remove(name)) {
            throw CalcException.of(CalcErrorCode.VARIABLE_NOT_FOUND, "变量不存在: " + name.value());
        }
    }

    /** 供求值入口把已存变量并入求值上下文。 */
    public Map<String, CalcNumber> snapshot() {
        return variables.all().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        v -> v.name().value(), Variable::value));
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/application/HistoryUseCase.java`：

```java
package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.calculation.CalculationHistory;
import com.wysjwxm.calculator.domain.model.calculation.PageResult;

/**
 * 历史用例：分页参数校验与查询。
 */
public class HistoryUseCase {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final CalculationHistory history;

    public HistoryUseCase(CalculationHistory history) {
        this.history = history;
    }

    public PageResult<Calculation> page(int page, int size) {
        if (page < 0) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "page 不能为负数: " + page);
        }
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "size 必须在 " + MIN_PAGE_SIZE + " 到 " + MAX_PAGE_SIZE + " 之间，实际为 " + size);
        }
        return history.page(page, size);
    }

    public Calculation get(long id) {
        return history.find(id).orElseThrow(() -> CalcException.of(
                CalcErrorCode.HISTORY_NOT_FOUND, "历史记录不存在: " + id));
    }

    public int clear() {
        return history.clear();
    }
}
```

- [ ] **Step 9: 运行确认通过**

运行：`mvn -q test -Dtest='VariableUseCaseTest,HistoryUseCaseTest'`

预期：18 个测试全部 PASS。

- [ ] **Step 10: 创建装配配置**

创建 `src/main/java/com/wysjwxm/calculator/infrastructure/config/CalculatorConfiguration.java`：

```java
package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.application.HistoryUseCase;
import com.wysjwxm.calculator.application.VariableUseCase;
import com.wysjwxm.calculator.domain.model.calculation.CalculationHistory;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import com.wysjwxm.calculator.domain.model.variable.VariableSet;
import com.wysjwxm.calculator.infrastructure.InMemoryCalculationHistory;
import com.wysjwxm.calculator.infrastructure.InMemoryVariableSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 唯一的 Bean 装配点。
 *
 * <p>为什么装配集中在这里、而不是给每个类加 {@code @Service} / {@code @Repository}：
 * 领域层的类**不允许** import Spring，所以它们本来就不能加注解；若只给基础设施层
 * 加注解、领域层走 @Bean，装配点就散成了两处。集中在一处之后，「谁被注册成 Bean」
 * 有唯一答案。
 *
 * <p>这里同时承担翻译职责：把基础设施的 {@link CalculatorProperties} 翻译成
 * 领域与应用能用的对象，因此 application 层不必认识配置类。
 */
@Configuration
@EnableConfigurationProperties(CalculatorProperties.class)
public class CalculatorConfiguration {

    @Bean
    public Numbers numbers(CalculatorProperties properties) {
        return new Numbers(properties.divisionPrecision());
    }

    @Bean
    public FunctionRegistry functionRegistry() {
        return new FunctionRegistry();
    }

    @Bean
    public ExpressionParser expressionParser() {
        // 无状态，可安全作为单例共享
        return new ExpressionParser();
    }

    @Bean
    public ExpressionEvaluator expressionEvaluator(FunctionRegistry functionRegistry,
                                                   Numbers numbers) {
        return new ExpressionEvaluator(functionRegistry, numbers);
    }

    @Bean
    public CalculationPolicy calculationPolicy(CalculatorProperties properties) {
        return new CalculationPolicy(properties.defaultAngleUnit(),
                properties.maxExpressionLength());
    }

    @Bean
    public VariableSet variableSet() {
        return new InMemoryVariableSet();
    }

    @Bean
    public CalculationHistory calculationHistory(CalculatorProperties properties) {
        return new InMemoryCalculationHistory(properties.historyCapacity());
    }

    @Bean
    public CalculationUseCase calculationUseCase(ExpressionParser expressionParser,
                                                 ExpressionEvaluator expressionEvaluator,
                                                 CalculationHistory calculationHistory,
                                                 CalculationPolicy calculationPolicy) {
        return new CalculationUseCase(expressionParser, expressionEvaluator,
                calculationHistory, calculationPolicy);
    }

    @Bean
    public VariableUseCase variableUseCase(VariableSet variableSet) {
        return new VariableUseCase(variableSet);
    }

    @Bean
    public HistoryUseCase historyUseCase(CalculationHistory calculationHistory) {
        return new HistoryUseCase(calculationHistory);
    }
}
```

- [ ] **Step 11: 运行上下文加载测试**

运行：`mvn -q test -Dtest=ScientificCalculatorApplicationTests`

预期：PASS。**若报找不到 bean**：检查 `CalculationUseCase` 等是否需要 `@Component` —— 它们由上面的 `@Bean` 方法产出，不应再加注解。

- [ ] **Step 12: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 13: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/application/ \
        src/main/java/com/wysjwxm/calculator/infrastructure/config/CalculatorConfiguration.java \
        src/test/java/com/wysjwxm/calculator/application/
git commit -m "feat: 应用层三个用例与集中在 CalculatorConfiguration 的 Bean 装配"
```

---

### Task 13: 接口层错误处理

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/error/ErrorResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/error/ErrorStatusMapper.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/error/GlobalExceptionHandler.java`
- Test: `src/test/java/com/wysjwxm/calculator/interfaces/error/ErrorStatusMapperTest.java`

**Interfaces:**
- Consumes: `CalcErrorCode`、`CalcException`（Task 2）
- Produces:
  - `record ErrorResponse(String code, String message, Integer position, Instant timestamp, String path)`
  - `ErrorStatusMapper.toStatus(CalcErrorCode code)` → `HttpStatus`
  - `GlobalExceptionHandler`（`@RestControllerAdvice`）

- [ ] **Step 1: 写失败测试**

创建 `src/test/java/com/wysjwxm/calculator/interfaces/error/ErrorStatusMapperTest.java`：

```java
package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码 → HTTP 状态的映射必须覆盖全部枚举值。
 *
 * <p>这条测试是防漂移的守门人：映射表与枚举分处两个包，若无人看守，
 * 新增错误码会静默退化成 500 —— 而不是在编译期或测试期暴露。
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
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.UNKNOWN_VARIABLE))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.DIVISION_BY_ZERO))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.DOMAIN_ERROR))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.NON_FINITE_RESULT))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void methodNotAllowedMapsTo405() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.METHOD_NOT_ALLOWED))
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void internalErrorMapsTo500() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
```

- [ ] **Step 2: 运行确认失败**

运行：`mvn -q test -Dtest=ErrorStatusMapperTest`

预期：编译失败，`ErrorStatusMapper` 不存在。

- [ ] **Step 3: 创建 ErrorResponse 与 ErrorStatusMapper**

创建 `src/main/java/com/wysjwxm/calculator/interfaces/error/ErrorResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * @param position 仅语法类错误有值，其余为 null；用 JsonInclude 让无位置的响应里
 *                 不出现该字段，避免调用方误判为 0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Integer position,
                            Instant timestamp, String path) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/error/ErrorStatusMapper.java`：

```java
package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 错误码 → HTTP 状态的唯一映射点。
 *
 * <p>领域层的 {@link CalcErrorCode} 刻意不携带 HTTP 状态，以保持对传输协议无感知；
 * 映射集中在这里，由 ErrorStatusMapperTest 保证不漏。
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

- [ ] **Step 4: 创建 GlobalExceptionHandler**

创建 `src/main/java/com/wysjwxm/calculator/interfaces/error/GlobalExceptionHandler.java`：

```java
package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
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
 * <p>不依赖 Spring 默认错误页 —— application.yaml 已关闭 whitelabel，并开启了
 * throw-exception-if-no-handler-found。
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

- [ ] **Step 5: 运行确认通过**

运行：`mvn -q test -Dtest=ErrorStatusMapperTest`

预期：6 个测试 PASS。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/interfaces/error/ \
        src/test/java/com/wysjwxm/calculator/interfaces/error/
git commit -m "feat: 接口层错误响应、状态映射与全局异常处理"
```

---

### Task 14: 接口层 HTTP 端点

`interfaces` 同时是防腐层：JSON DTO ↔ 领域对象，两侧不互相渗透（DTO 不进领域，领域类型不直接当传输契约）。

**Files:**
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/CalcNumberSerializer.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/CalculateRequest.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/CalculateResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/FunctionsResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/HistoryItemResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/HistoryPageResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/PutVariableRequest.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/VariableResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/VariableListResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/ClearHistoryResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/dto/HealthResponse.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/CalculatorController.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/HistoryController.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/VariableController.java`
- Create: `src/main/java/com/wysjwxm/calculator/interfaces/MetaController.java`
- Test: `src/test/java/com/wysjwxm/calculator/interfaces/CalculatorControllerTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/interfaces/HistoryControllerTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/interfaces/VariableControllerTest.java`
- Test: `src/test/java/com/wysjwxm/calculator/interfaces/MetaControllerTest.java`

**Interfaces:**
- Consumes: 三个 UseCase、`CalculationPolicy`（Task 12）、`OperatorTable`（Task 5）、`FunctionRegistry`、`ReservedNames`（Task 7）、`MathematicalConstant`（Task 7）、`GlobalExceptionHandler`（Task 13）
- Produces: 10 个 HTTP 端点（spec §3.1）

- [ ] **Step 1: 创建 CalcNumberSerializer**

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/CalcNumberSerializer.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;

import java.io.IOException;

/**
 * 把 CalcNumber 序列化为 JSON 数字：精确路径写 BigDecimal（Jackson 输出 0.3
 * 而非 0.30000000000000004），浮点路径写 double。
 *
 * <p>这是防腐层的一部分：领域值对象不直接暴露给 Jackson 的默认序列化。
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

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/CalculateRequest.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.wysjwxm.calculator.domain.AngleUnit;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param variables 请求级临时变量，仅本次求值生效、不落库；键同样受保留名约束
 */
public record CalculateRequest(String expression, AngleUnit angleUnit,
                               Map<String, BigDecimal> variables) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/CalculateResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

public record CalculateResponse(
        String expression,
        @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber result,
        String resultType,
        AngleUnit angleUnit,
        long historyId,
        double elapsedMs) {

    public static CalculateResponse from(Calculation calculation) {
        return new CalculateResponse(
                calculation.expression().value(),
                calculation.result(),
                calculation.result().isExact() ? "DECIMAL" : "FLOATING",
                calculation.angleUnit(),
                calculation.id(),
                calculation.elapsedMs());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/FunctionsResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.Associativity;
import com.wysjwxm.calculator.domain.model.expression.Fixity;
import com.wysjwxm.calculator.domain.model.expression.Operator;

import java.util.List;
import java.util.Set;

/**
 * 服务能力清单，如实描述本服务接受的表达式语法（spec §7.2）。
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

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/HistoryItemResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.time.Instant;

public record HistoryItemResponse(
        long id,
        String expression,
        @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber result,
        String resultType,
        AngleUnit angleUnit,
        double elapsedMs,
        Instant createdAt) {

    public static HistoryItemResponse from(Calculation calculation) {
        return new HistoryItemResponse(
                calculation.id(),
                calculation.expression().value(),
                calculation.result(),
                calculation.result().isExact() ? "DECIMAL" : "FLOATING",
                calculation.angleUnit(),
                calculation.elapsedMs(),
                calculation.createdAt());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/HistoryPageResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.calculation.PageResult;

import java.util.List;

public record HistoryPageResponse(List<HistoryItemResponse> items, int page, int size,
                                  long totalElements, int totalPages, boolean hasNext) {

    public static HistoryPageResponse from(PageResult<Calculation> page) {
        return new HistoryPageResponse(
                page.items().stream().map(HistoryItemResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages(), page.hasNext());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/PutVariableRequest.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import java.math.BigDecimal;

public record PutVariableRequest(BigDecimal value) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/VariableResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.variable.Variable;

import java.time.Instant;

public record VariableResponse(String name,
                               @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber value,
                               Instant createdAt,
                               Instant updatedAt) {

    public static VariableResponse from(Variable variable) {
        return new VariableResponse(variable.name().value(), variable.value(),
                variable.createdAt(), variable.updatedAt());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/VariableListResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

import java.util.List;

public record VariableListResponse(List<VariableResponse> items, int total) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/ClearHistoryResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

public record ClearHistoryResponse(int deleted) {
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/dto/HealthResponse.java`：

```java
package com.wysjwxm.calculator.interfaces.dto;

public record HealthResponse(String status, long uptimeMs) {
}
```

- [ ] **Step 3: 创建四个 Controller**

创建 `src/main/java/com/wysjwxm/calculator/interfaces/CalculatorController.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.CalculationCommand;
import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.application.VariableUseCase;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.MathematicalConstant;
import com.wysjwxm.calculator.domain.model.expression.OperatorTable;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.interfaces.dto.CalculateRequest;
import com.wysjwxm.calculator.interfaces.dto.CalculateResponse;
import com.wysjwxm.calculator.interfaces.dto.FunctionsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 求值与能力清单端点。
 *
 * <p>本类承担防腐层职责：把 JSON DTO 翻译成应用层命令，再把领域对象翻译回
 * 响应 DTO。领域类型不直接充当传输契约。
 */
@RestController
@RequestMapping("/api/v1/calculator")
public class CalculatorController {

    private final CalculationUseCase calculationUseCase;
    private final VariableUseCase variableUseCase;
    private final FunctionRegistry functionRegistry;
    private final CalculationPolicy policy;

    public CalculatorController(CalculationUseCase calculationUseCase,
                                VariableUseCase variableUseCase,
                                FunctionRegistry functionRegistry,
                                CalculationPolicy policy) {
        this.calculationUseCase = calculationUseCase;
        this.variableUseCase = variableUseCase;
        this.functionRegistry = functionRegistry;
        this.policy = policy;
    }

    @PostMapping("/calculate")
    public CalculateResponse calculate(@RequestBody CalculateRequest request) {
        // 已存变量与请求级变量在此合并；请求级同名变量优先覆盖
        Map<String, CalcNumber> variables = new LinkedHashMap<>(variableUseCase.snapshot());
        variables.putAll(toCalcNumbers(request.variables()));
        CalculationCommand command = new CalculationCommand(
                request.expression(), request.angleUnit(), variables);
        return CalculateResponse.from(calculationUseCase.calculate(command));
    }

    @GetMapping("/functions")
    public FunctionsResponse functions() {
        return new FunctionsResponse(
                MathematicalConstant.names(),
                functionRegistry.unaryNames(),
                functionRegistry.binaryNames(),
                OperatorTable.all().stream().map(FunctionsResponse.OperatorEntry::from).toList(),
                List.of(AngleUnit.values()),
                policy.defaultAngleUnit());
    }

    private Map<String, CalcNumber> toCalcNumbers(Map<String, BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, CalcNumber> converted = new LinkedHashMap<>();
        raw.forEach((name, value) -> converted.put(name, new DecimalNumber(value)));
        return converted;
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/HistoryController.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.HistoryUseCase;
import com.wysjwxm.calculator.interfaces.dto.ClearHistoryResponse;
import com.wysjwxm.calculator.interfaces.dto.HistoryItemResponse;
import com.wysjwxm.calculator.interfaces.dto.HistoryPageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/history")
public class HistoryController {

    private final HistoryUseCase historyUseCase;

    public HistoryController(HistoryUseCase historyUseCase) {
        this.historyUseCase = historyUseCase;
    }

    @GetMapping
    public HistoryPageResponse page(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return HistoryPageResponse.from(historyUseCase.page(page, size));
    }

    @GetMapping("/{id}")
    public HistoryItemResponse get(@PathVariable long id) {
        return HistoryItemResponse.from(historyUseCase.get(id));
    }

    @DeleteMapping
    public ClearHistoryResponse clear() {
        return new ClearHistoryResponse(historyUseCase.clear());
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/VariableController.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.VariableUseCase;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.interfaces.dto.PutVariableRequest;
import com.wysjwxm.calculator.interfaces.dto.VariableListResponse;
import com.wysjwxm.calculator.interfaces.dto.VariableResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/variables")
public class VariableController {

    private final VariableUseCase variableUseCase;

    public VariableController(VariableUseCase variableUseCase) {
        this.variableUseCase = variableUseCase;
    }

    /** 幂等 upsert —— 覆盖已存在变量同样返回 200，保持 PUT 语义。 */
    @PutMapping("/{name}")
    public VariableResponse put(@PathVariable String name, @RequestBody PutVariableRequest request) {
        BigDecimal value = request == null ? null : request.value();
        if (value == null) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "value 不能为空");
        }
        return VariableResponse.from(variableUseCase.define(name, new DecimalNumber(value)));
    }

    @GetMapping
    public VariableListResponse list() {
        List<VariableResponse> items = variableUseCase.list().stream()
                .map(VariableResponse::from)
                .toList();
        return new VariableListResponse(items, items.size());
    }

    @GetMapping("/{name}")
    public VariableResponse get(@PathVariable String name) {
        return VariableResponse.from(variableUseCase.get(name));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String name) {
        variableUseCase.remove(name);
    }
}
```

创建 `src/main/java/com/wysjwxm/calculator/interfaces/MetaController.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.interfaces.dto.HealthResponse;
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

创建 `src/test/java/com/wysjwxm/calculator/interfaces/CalculatorControllerTest.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.application.VariableUseCase;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import com.wysjwxm.calculator.infrastructure.InMemoryCalculationHistory;
import com.wysjwxm.calculator.infrastructure.InMemoryVariableSet;
import com.wysjwxm.calculator.interfaces.error.GlobalExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用 standaloneSetup 而非 @WebMvcTest：控制器行为几乎不依赖容器特性，
 * standaloneSetup 更快，且能**显式挂载 GlobalExceptionHandler** ——
 * 错误码路径因此被真实覆盖，而不是依赖切片扫描恰好扫到它。
 */
class CalculatorControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FunctionRegistry registry = new FunctionRegistry();
        Numbers numbers = new Numbers(34);
        CalculationPolicy policy = new CalculationPolicy(AngleUnit.DEGREE, 1000);
        CalculationUseCase useCase = new CalculationUseCase(
                new ExpressionParser(),
                new ExpressionEvaluator(registry, numbers),
                new InMemoryCalculationHistory(100),
                policy);
        VariableUseCase variableUseCase = new VariableUseCase(new InMemoryVariableSet());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculatorController(useCase, variableUseCase, registry, policy))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions post(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/calculator/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ---------- 正常路径 ----------

    @Test
    void calculatesExpression() throws Exception {
        post("{\"expression\":\"1+2*3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(7))
                .andExpect(jsonPath("$.resultType").value("DECIMAL"))
                .andExpect(jsonPath("$.historyId").isNumber())
                .andExpect(jsonPath("$.elapsedMs").isNumber());
    }

    @Test
    void specAcceptanceExpressionReturnsOnePointFive() throws Exception {
        post("{\"expression\":\"1 + 2 * sin(30) ^ 2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1.5))
                .andExpect(jsonPath("$.angleUnit").value("DEGREE"));
    }

    @Test
    void decimalPrecisionSurvivesSerialization() throws Exception {
        // 0.1+0.2 必须序列化成 0.3，而不是 0.30000000000000004
        post("{\"expression\":\"0.1+0.2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(0.3));
    }

    @Test
    void powerAssociativitySurvivesTheWire() throws Exception {
        post("{\"expression\":\"2^3^2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(512));
    }

    @Test
    void unaryMinusBindsLooserThanPowerOverTheWire() throws Exception {
        post("{\"expression\":\"-2^2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(-4));
    }

    @Test
    void angleUnitIsNullForNonTrigExpression() throws Exception {
        post("{\"expression\":\"1+2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").doesNotExist());
    }

    @Test
    void radianAngleUnitIsHonoured() throws Exception {
        post("{\"expression\":\"sin(30)\",\"angleUnit\":\"RADIAN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").value("RADIAN"));
    }

    @Test
    void requestVariablesAreUsable() throws Exception {
        post("{\"expression\":\"x*2\",\"variables\":{\"x\":7}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(14));
    }

    @Test
    void storedVariablesAreVisibleToEvaluation() throws Exception {
        mockMvc.perform(put("/api/v1/variables/y")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":5}"))
                .andExpect(status().isOk());
        post("{\"expression\":\"y*2+1\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(11));
    }

    @Test
    void requestVariableOverridesStoredVariable() throws Exception {
        mockMvc.perform(put("/api/v1/variables/x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":5}"))
                .andExpect(status().isOk());
        post("{\"expression\":\"x\",\"variables\":{\"x\":9}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(9));
    }

    // ---------- 错误码 ----------

    @Test
    void parseErrorReturns400WithPosition() throws Exception {
        post("{\"expression\":\"1+\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.path").value("/api/v1/calculator/calculate"));
    }

    @Test
    void divisionByZeroReturns422() throws Exception {
        post("{\"expression\":\"1/0\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DIVISION_BY_ZERO"))
                .andExpect(jsonPath("$.position").doesNotExist());
    }

    @Test
    void domainErrorReturns422() throws Exception {
        post("{\"expression\":\"sqrt(-1)\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_ERROR"));
    }

    @Test
    void unknownFunctionReturns400() throws Exception {
        post("{\"expression\":\"nope(1)\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FUNCTION"));
    }

    @Test
    void unknownVariableReturns422() throws Exception {
        post("{\"expression\":\"y+1\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_VARIABLE"));
    }

    @Test
    void missingExpressionReturns400() throws Exception {
        post("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void reservedNameInRequestVariablesReturns400() throws Exception {
        // spec §14 验收标准第 5 条的请求级变量版本
        post("{\"expression\":\"sin(pi)\",\"variables\":{\"pi\":3}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("pi")));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        post("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---------- 能力清单 ----------

    @Test
    void functionsManifestListsEverything() throws Exception {
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unaryFunctions.length()").value(23))
                .andExpect(jsonPath("$.binaryFunctions.length()").value(5))
                .andExpect(jsonPath("$.operators.length()").value(9))
                .andExpect(jsonPath("$.constants.length()").value(2))
                .andExpect(jsonPath("$.angleUnits.length()").value(2))
                .andExpect(jsonPath("$.defaultAngleUnit").value("DEGREE"));
    }

    @Test
    void manifestDoesNotAdvertiseRedundantSpellings() throws Exception {
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binaryFunctions", Matchers.not(Matchers.hasItem("pow"))))
                .andExpect(jsonPath("$.binaryFunctions", Matchers.not(Matchers.hasItem("mod"))))
                .andExpect(jsonPath("$.unaryFunctions", Matchers.not(Matchers.hasItem("fact"))));
    }

    @Test
    void manifestExposesOperatorPrecedenceFromTheTable() throws Exception {
        // 断言完整数组，避免依赖 JSONPath 过滤器语法
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operators[0].symbol").value("+"))
                .andExpect(jsonPath("$.operators[0].precedence").value(1))
                .andExpect(jsonPath("$.operators[5].symbol").value("^"))
                .andExpect(jsonPath("$.operators[5].precedence").value(4))
                .andExpect(jsonPath("$.operators[5].associativity").value("RIGHT"))
                .andExpect(jsonPath("$.operators[8].symbol").value("!"))
                .andExpect(jsonPath("$.operators[8].fixity").value("POSTFIX"))
                .andExpect(jsonPath("$.operators[8].associativity").doesNotExist());
    }
}
```

**注**：`manifestExposesOperatorPrecedenceFromTheTable` 依赖 `OperatorTable.all()` 的顺序（`+ - * / % ^ !`，其中前两个是二元，第 7、8 个是一元）。该顺序已由 Task 5 的 `allExposesNineOperatorEntries` 与 `symbolsUseMathematicalNotationNotNames` 固定。若日后调整 `OperatorTable` 的元素顺序，这条测试会失败 —— 这是有意的，顺序变化需要人确认。

- [ ] **Step 5: 运行确认通过**

运行：`mvn -q test -Dtest=CalculatorControllerTest`

预期：全部 PASS。

**若 `malformedJsonReturns400` 返回 500**：检查 `GlobalExceptionHandler` 中 `HttpMessageNotReadableException` 的 handler 是否存在，且 `standaloneSetup` 挂了 `setControllerAdvice`。

- [ ] **Step 6: 写其余三个 Controller 测试**

创建 `src/test/java/com/wysjwxm/calculator/interfaces/HistoryControllerTest.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.HistoryUseCase;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import com.wysjwxm.calculator.infrastructure.InMemoryCalculationHistory;
import com.wysjwxm.calculator.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HistoryControllerTest {

    private InMemoryCalculationHistory history;
    private MockMvc mockMvc;
    private final Numbers numbers = new Numbers(34);

    @BeforeEach
    void setUp() {
        history = new InMemoryCalculationHistory(100);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HistoryController(new HistoryUseCase(history)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void append(String expr, long result) {
        history.append(new ExpressionText(expr), numbers.of(result), AngleUnit.DEGREE, 0.1);
    }

    @Test
    void listsNewestFirst() throws Exception {
        append("1+1", 2);
        append("2+2", 4);

        mockMvc.perform(get("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].expression").value("2+2"))
                .andExpect(jsonPath("$.items[1].expression").value("1+1"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void returnsSingleRecord() throws Exception {
        append("1+1", 2);
        mockMvc.perform(get("/api/v1/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
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
        append("1+1", 2);
        append("2+2", 4);
        mockMvc.perform(delete("/api/v1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));
    }

    @Test
    void paginationSlicesCorrectly() throws Exception {
        for (int i = 1; i <= 5; i++) {
            append("e" + i, i);
        }
        mockMvc.perform(get("/api/v1/history?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].expression").value("e5"))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
    }
}
```

创建 `src/test/java/com/wysjwxm/calculator/interfaces/VariableControllerTest.java`：

```java
package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.VariableUseCase;
import com.wysjwxm.calculator.infrastructure.InMemoryVariableSet;
import com.wysjwxm.calculator.interfaces.error.GlobalExceptionHandler;
import org.hamcrest.Matchers;
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
                        new VariableUseCase(new InMemoryVariableSet())))
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

    // ---------- 保留名（spec §14 验收标准第 5 条） ----------

    @Test
    void reservedConstantNameReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/variables/pi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("pi")));
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
    void readingOrDeletingAReservedNameIsAlsoRejected() throws Exception {
        mockMvc.perform(get("/api/v1/variables/pi"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mockMvc.perform(delete("/api/v1/variables/e"))
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

创建 `src/test/java/com/wysjwxm/calculator/interfaces/MetaControllerTest.java`：

```java
package com.wysjwxm.calculator.interfaces;

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

- [ ] **Step 7: 运行全部测试**

运行：`mvn -q test`

预期：全部 PASS。

- [ ] **Step 8: 提交**

```bash
git add src/main/java/com/wysjwxm/calculator/interfaces/ \
        src/test/java/com/wysjwxm/calculator/interfaces/
git commit -m "feat: 接口层 10 个 HTTP 端点与 DTO 防腐层"
```

---

### Task 15: 端到端测试与打包验证

**Files:**
- Test: `src/test/java/com/wysjwxm/calculator/CalculatorEndToEndTest.java`

**Interfaces:**
- Consumes: 全部组件
- Produces: 全链路验证

- [ ] **Step 1: 写端到端测试**

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

    private ResponseEntity<String> putJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void fullWorkflow() {
        // 1. 健康检查（spec §14 验收标准第 2 条）
        ResponseEntity<String> health = rest.getForEntity(url("/api/v1/health"), String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");

        // 2. 能力清单
        ResponseEntity<String> functions = rest.getForEntity(
                url("/api/v1/calculator/functions"), String.class);
        assertThat(functions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(functions.getBody()).contains("\"sin\"").contains("\"operators\"");

        // 3. 表达式求值（验收标准第 3 条）
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1 + 2 * sin(30) ^ 2\"}");
        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calc.getBody()).contains("\"result\":1.5");

        // 4. 精确性（验收标准第 4 条）
        ResponseEntity<String> exact = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"0.1+0.2\"}");
        assertThat(exact.getBody()).contains("\"result\":0.3");

        // 5. 优先级三条规则
        assertThat(postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"2^3^2\"}").getBody()).contains("\"result\":512");
        assertThat(postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"-2^2\"}").getBody()).contains("\"result\":-4");
        assertThat(postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"3!+1\"}").getBody()).contains("\"result\":7");

        // 6. 定义变量并使用已存变量
        assertThat(putJson("/api/v1/variables/x", "{\"value\":5}").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        ResponseEntity<String> withVariable = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"x * 2 + 1\"}");
        assertThat(withVariable.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(withVariable.getBody()).contains("\"result\":11");

        // 7. 查历史（第 3–6 步共 6 次成功计算）
        ResponseEntity<String> history = rest.getForEntity(
                url("/api/v1/history?page=0&size=10"), String.class);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody()).contains("\"totalElements\":6");

        // 8. 保留名被拒（验收标准第 5 条）—— 存储入口
        ResponseEntity<String> reserved = putJson("/api/v1/variables/pi", "{\"value\":3}");
        assertThat(reserved.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reserved.getBody()).contains("INVALID_REQUEST");

        // 9. 保留名被拒 —— 请求级变量入口（同一规则，两个入口）
        ResponseEntity<String> reservedInline = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"sin(pi)\",\"variables\":{\"pi\":3}}");
        assertThat(reservedInline.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reservedInline.getBody()).contains("INVALID_REQUEST");

        // 10. 错误码
        ResponseEntity<String> divideByZero = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1/0\"}");
        assertThat(divideByZero.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(divideByZero.getBody()).contains("DIVISION_BY_ZERO");

        // 11. 未知路径
        ResponseEntity<String> notFound = rest.getForEntity(url("/api/v1/nope"), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // 12. 清空历史
        ResponseEntity<String> cleared = rest.exchange(url("/api/v1/history"), HttpMethod.DELETE,
                HttpEntity.EMPTY, String.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody()).contains("\"deleted\":6");
    }
}
```

- [ ] **Step 2: 运行端到端测试**

运行：`mvn -q test -Dtest=CalculatorEndToEndTest`

预期：PASS。

**若第 7 步 `totalElements` 不是 6**：说明某个计算请求未落历史或落了两次 —— 检查 `CalculationUseCase.calculate` 是否只在求值成功后 `append`。

**若第 11 步返回 500 而非 404**：检查 `application.yaml` 中 `spring.mvc.throw-exception-if-no-handler-found` 与 `spring.web.resources.add-mappings` 是否都已设置（Task 1 Step 4）。

- [ ] **Step 3: 运行全部测试**

运行：`mvn -q clean test`

预期：BUILD SUCCESS，全部测试通过。

- [ ] **Step 4: 打包**

运行：

```bash
mvn -q clean package
ls -la target/scientific-calculator-0.0.1-SNAPSHOT.jar
```

预期：jar 存在且体积明显大于几 KB（说明 `repackage` 已把依赖打进 fat jar）。

- [ ] **Step 5: 手动冒烟测试**

请用户在自己终端启动（本会话不代跑长驻进程）：

```bash
java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar
```

另开一个终端验证：

```bash
curl -s localhost:8080/api/v1/health
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
  -H 'Content-Type: application/json' -d '{"expression":"1 + 2 * sin(30) ^ 2"}'
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
  -H 'Content-Type: application/json' -d '{"expression":"0.1+0.2"}'
curl -s -X POST localhost:8080/api/v1/calculator/calculate \
  -H 'Content-Type: application/json' -d '{"expression":"-2^2"}'
curl -s -X PUT localhost:8080/api/v1/variables/pi \
  -H 'Content-Type: application/json' -d '{"value":3}'
curl -s localhost:8080/api/v1/calculator/functions
```

预期依次为：`{"status":"UP",...}`、`"result":1.5`、`"result":0.3`、`"result":-4`、
400 + `INVALID_REQUEST`、完整能力清单。

确认后 Ctrl-C 停止服务。

- [ ] **Step 6: 提交**

```bash
git add src/test/java/com/wysjwxm/calculator/CalculatorEndToEndTest.java
git commit -m "test: 端到端全链路测试与 runnable jar 验证"
```

---

### Task 16: 交付文档

**Files:**
- Create: `README.md`
- Create: `docs/01-需求分析.md`
- Create: `docs/02-架构设计.md`
- Create: `docs/03-AI协作记录.md`

**Interfaces:**
- Consumes: spec（v3）与全部已实现代码
- Produces: 交付文档

- [ ] **Step 1: 写 README.md**

内容须包含：项目简介、技术栈、构建命令（`mvn clean package`）、启动命令（`java -jar target/scientific-calculator-0.0.1-SNAPSHOT.jar`）、10 个端点的表格（方法 / 路径 / 说明）、每个端点一条可直接复制的 `curl` 示例、表达式语法速查（含优先级三条规则 `-2^2 = -4`、`2^3^2 = 512`、`3!+1 = 7`）、保留名规则（变量名不得使用函数名与 `pi`/`e`，**两个入口统一生效**）。

- [ ] **Step 2: 写 docs/01-需求分析.md**

从 spec §1、§3、§6、§7 提炼：原始需求、需求拆解过程、功能边界（做/不做及理由）、用例清单、验收标准。重点体现**边界是自行划定并论证的**，而非照抄。

- [ ] **Step 3: 写 docs/02-架构设计.md**

以 spec 为源头整理润色：DDD 四层与依赖方向铁律、**统一语言表与「限界上下文只有一个」的判断**、数值模型取舍、表达式语言规范、并发与存储策略（含 FIFO vs LRU 论证）、错误码表，以及 **§12 设计决策记录 D1–D13 —— 含被否决方案及否决理由**。

特别要写明**刻意不引入**的东西及其理由：领域事件、规约模式、工厂类、CQRS、仓储与聚合分离。这一节的论证价值高于实现本身。

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
| §2 约束（Java17 / SB3.5 / 单一依赖） | Task 1 |
| §3.1 做（10 个端点） | Task 14 |
| §3.2 不做 | 全域（不出现在任何任务中） |
| §4.0 限界上下文与统一语言 | Task 8（变量值对象）、Task 16 |
| §4.1 DDD 四层与包结构 | Task 1–14 的包划分 |
| §4.2 组件职责 | 各任务 |
| §4.3 三条实现约束 | Task 6（无状态）、Task 7（functionName）、Task 12（长度策略） |
| §5 数值模型 | Task 3 |
| §6.1 词法 | Task 4 |
| §6.2–6.3 语法与优先级 | Task 5、Task 6 |
| §6.4 保留常量与禁用集合 | Task 7、8、12、14（两入口统一） |
| §6.5 函数集与定义域 | Task 7 |
| §7.1 求值接口 | Task 14 |
| §7.2 能力清单 | Task 14（由 OperatorTable 生成） |
| §7.3 历史 | Task 10、12、13、14 |
| §7.4 变量 | Task 11、12、14 |
| §7.5 健康检查 | Task 14 |
| §7.6 错误码（12 个） | Task 2、13 |
| §8 并发与存储（FIFO） | Task 10、11 |
| §9 配置项 | Task 1、12 |
| §10 测试策略 | 每个任务的测试步骤 |
| §11 交付物 | Task 16 |
| §12 决策记录 D1–D13 | Task 16 Step 3 要求写入 02-架构设计.md |
| §13 风险 | Task 16 |
| §14 验收标准 | Task 15 |

无未覆盖项。

**2. 占位符扫描**：无 TBD / TODO / 「类似 Task N」/ 无代码的代码步骤。

**3. 类型一致性核对**（逐个签名跨任务核对）：

| 符号 | 定义于 | 使用于 | 一致 |
|---|---|---|---|
| `CalcException.of(code, msg)` / `.at(code, msg, pos)` | Task 2 | Task 3 起 | ✓ |
| `Numbers`（实例类，`int` 构造参数） | Task 3 | Task 7、9、10、12 | ✓ |
| `ExpressionText(String)`，紧凑构造器 trim | Task 4 | Task 6、10、12、14 | ✓ |
| `Lexer(String).tokenize()` | Task 4 | Task 6 | ✓ |
| `Operator` 四元组 `(symbol, fixity, precedence, associativity)` | Task 5 | Task 6、14 | ✓ |
| `ExpressionParser.parse(ExpressionText)`，**无状态** | Task 6 | Task 9、12 | ✓ |
| `MathFunction.functionName()`（**不是 `name()`**） | Task 7 | Task 7、9 | ✓ |
| `ReservedNames.standard()` / `.contains(String)` | Task 7 | Task 8 | ✓ |
| `VariableName(String)` 构造即校验 | Task 8 | Task 11、12、14 | ✓ |
| `EvaluationContext.lookup(String)`（**不是 `VariableName`**） | Task 9 | Task 9、12 | ✓ |
| `ExpressionEvaluator.functionRegistry()` | Task 9 | Task 12 | ✓ |
| `CalculationHistory.append/page/find/clear/size` | Task 10 | Task 12 | ✓ |
| `VariableSet.define/find/all/remove/size` | Task 11 | Task 12 | ✓ |
| `VariableUseCase.snapshot()` | Task 12 | Task 14（合并已存变量） | ✓ |
| `CalculationPolicy(AngleUnit, int)` | Task 12 | Task 12、14 | ✓ |
| `CalculationCommand(String, AngleUnit, Map)` | Task 12 | Task 12、14 | ✓ |
| `ErrorStatusMapper.toStatus(CalcErrorCode)` | Task 13 | Task 13 | ✓ |
| `FunctionsResponse.OperatorEntry.from(Operator)` | Task 14 | Task 14 | ✓ |

**4. 已识别的实现期风险**

| 风险 | 位置 | 处理 |
|---|---|---|
| `spring-boot-starter-parent:3.5.6` 可能不存在 | Task 1 Step 1 | 先查 Maven Central，取实际存在的 3.5.x 补丁版 |
| `jakarta.validation` 是否随 web starter 传递引入 | Task 1 Step 8 | 已给出降级方案（手工校验 + IllegalArgumentException） |
| 枚举无法覆写 `Enum.name()` | Task 7 | 接口方法定名 `functionName()`，已在 spec §4.3 记录 |
| 解析器并发被踩踏 | Task 6 | 游标封在 `ParseRun`，并有并发测试钉住 |
| `9^9^9` 走精确路径抛 `ArithmeticException` | Task 3 | `Numbers.power` 已 catch 并降级 |
| 404 未走 `NoHandlerFoundException` | Task 15 Step 2 | 已给出 `application.yaml` 两项配置 |
| `OperatorTable.all()` 顺序被测试依赖 | Task 14 Step 4 | 断言整数组，并注明顺序变更需人确认 |

**5. 与上一版计划（非 DDD）的差异说明**

上一版计划中 `interfaces` 之前的包名为 `core/api/service/store/config`。本版按 spec v3 改为 DDD 四层。核心算法代码（数值模型、词法、算子表、解析器、求值器、函数表）逻辑未变，仅包名与个别类型名调整；**新增的实质设计**是 `VariableName` 值对象、三个聚合根接口、以及应用层与领域层的分离。
