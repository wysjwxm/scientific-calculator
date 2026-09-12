package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import com.wysjwxm.calculator.interfaces.error.GlobalExceptionHandler;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用 standaloneSetup 而非 @WebMvcTest：控制器行为几乎不依赖容器特性，
 * standaloneSetup 更快，且能**显式挂载 GlobalExceptionHandler** ——
 * 错误码路径因此被真实覆盖，而不是依赖切片扫描恰好扫到它。
 *
 * <p><b>本类同时是 GlobalExceptionHandler 的首次真实覆盖</b>：Task 13 交付它时只有
 * ErrorStatusMapperTest 测到映射表，下面的 400 / 422 分支要等控制器存在后才走得到。
 * 因此这里的每条状态码与错误码断言都是那个 handler 唯一的行为证据。
 *
 * <p>本期是 MVP：没有变量存储与历史，故没有 PUT 变量、历史、/functions 清单的用例。
 *
 * <p><b>本类的 ObjectMapper 与生产栈不一致</b>：{@code standaloneSetup} 用的 mapper 与 Boot
 * 自动配置的 ObjectMapper **都注册了 {@code jackson-datatype-jsr310}**（{@code getRegisteredModuleIds()}
 * 实测两边都有），差别不在模块，在
 * {@code SerializationFeature.WRITE_DATES_AS_TIMESTAMPS} —— 前者为 {@code true}
 * （{@code Instant} 被写成一个十进制小数，实测 {@code 1789208624.860137000}），后者被 Boot 关掉了
 * （写成 ISO-8601 字符串，实测 {@code 2026-09-12T10:23:44.860137Z}）。所以同一个 {@code timestamp}
 * 字段在两个测试基座下的形态不同。**本类不要断言 {@code timestamp} 的形态** —— 目前也没有一条断言碰它。
 * 要断言就写到真实栈那个测试类里去。
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
                policy);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CalculatorController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** 求值请求的快捷入口。方法名刻意不叫 post —— 同名会遮蔽静态导入的
     *  {@code MockMvcRequestBuilders.post}，方法体内就再也拿不到请求构造器了。 */
    private ResultActions calculate(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/calculator/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    // ---------- 正常路径 ----------

    @Test
    void calculatesExpression() throws Exception {
        calculate("{\"expression\":\"1+2*3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(7))
                .andExpect(jsonPath("$.resultType").value("DECIMAL"));
    }

    @Test
    void specAcceptanceExpressionReturnsOnePointFive() throws Exception {
        calculate("{\"expression\":\"1 + 2 * sin(30) ^ 2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(1.5))
                .andExpect(jsonPath("$.angleUnit").value("DEGREE"));
    }

    @Test
    void decimalPrecisionSurvivesSerialization() throws Exception {
        // 0.1+0.2 必须序列化成 0.3，而不是 0.30000000000000004
        calculate("{\"expression\":\"0.1+0.2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(0.3));
    }

    @Test
    void powerAssociativitySurvivesTheWire() throws Exception {
        calculate("{\"expression\":\"2^3^2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(512));
    }

    @Test
    void unaryMinusBindsLooserThanPowerOverTheWire() throws Exception {
        calculate("{\"expression\":\"-2^2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(-4));
    }

    @Test
    void angleUnitIsNullForNonTrigExpression() throws Exception {
        // angleUnit 为 null 时字段照常出现、值为 null —— 这是本期的设计选择：
        // CalculateResponse 上没有 @JsonInclude(NON_NULL)，键一定在。
        // 故断言 null 值，而非 doesNotExist()（后者要求键不存在）。
        // 注意：spec 未规定求值响应的 null 形态；spec :420 的「其余记录该字段为 null」
        // 讲的是历史记录，不是这里。
        calculate("{\"expression\":\"1+2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").value(Matchers.nullValue()));
    }

    @Test
    void radianAngleUnitIsHonoured() throws Exception {
        // 只断言回显：本类的职责是「接口层把 angleUnit 原样翻译成命令、再原样回显」。
        // RADIAN 的**数值语义**由领域层测试钉住（FunctionRegistryTest 有
        // apply("sin", AngleUnit.RADIAN, 30) 的数值断言），此处不重复覆盖 ——
        // 免得读者误以为这条覆盖了弧度算术。
        calculate("{\"expression\":\"sin(30)\",\"angleUnit\":\"RADIAN\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").value("RADIAN"));
    }

    @Test
    void requestVariablesAreUsable() throws Exception {
        calculate("{\"expression\":\"x*2\",\"variables\":{\"x\":7}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(14));
    }

    // ---------- 错误码 ----------

    @Test
    void parseErrorReturns400WithPosition() throws Exception {
        calculate("{\"expression\":\"1+\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.position").value(2))
                .andExpect(jsonPath("$.path").value("/api/v1/calculator/calculate"));
    }

    @Test
    void divisionByZeroReturns422() throws Exception {
        calculate("{\"expression\":\"1/0\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DIVISION_BY_ZERO"))
                // position 为 null 时该键**不出现**：ErrorResponse 上有 @JsonInclude(NON_NULL)，
                // 其 javadoc 明确写了「避免调用方误判为 0」—— 这是 Task 13 冻结的对外契约。
                // 故此处断言键不在场，而不是断言 null 值。
                // 与上方 angleUnitIsNullForNonTrigExpression 的形态刻意不同：那个响应
                // 没有 @JsonInclude，字段在场为 null。两处不对称是有意的，不要「统一」。
                .andExpect(jsonPath("$.position").doesNotExist());
    }

    @Test
    void domainErrorReturns422() throws Exception {
        calculate("{\"expression\":\"sqrt(-1)\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_ERROR"));
    }

    @Test
    void unknownFunctionReturns400() throws Exception {
        calculate("{\"expression\":\"nope(1)\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_FUNCTION"));
    }

    @Test
    void unknownVariableReturns422() throws Exception {
        calculate("{\"expression\":\"y+1\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNKNOWN_VARIABLE"));
    }

    @Test
    void missingExpressionReturns400() throws Exception {
        calculate("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        calculate("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void wrongMethodOnExistingPathReturns405() throws Exception {
        // GET 打到只接受 POST 的路径：RequestMappingHandlerMapping 抛
        // HttpRequestMethodNotSupportedException，由 GlobalExceptionHandler 统一成
        // 405 + METHOD_NOT_ALLOWED。这条与 handler 里那个分支一一对应。
        mockMvc.perform(get("/api/v1/calculator/calculate"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void nullVariableValueReturns400NotServerError() throws Exception {
        // R52：{"variables":{"x":null}} 经 Jackson 得到 value 为 null 的映射，曾是
        // new DecimalNumber(null) 抛裸 NPE → HTTP 500 的真实可达路径。这条断言
        // 400 而不是 500，是「接口层翻译点守住了」的端到端证据 ——
        // CalculationCommand 里的守卫在这条路径上根本执行不到。
        // JSON 对象的键不可能是 null，所以只构造 null 值这一种。
        calculate("{\"expression\":\"x*2\",\"variables\":{\"x\":null}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedContentTypeFallsBackToUnified500() throws Exception {
        // 出厂配置下就能走到的兜底路径：Content-Type 不是 application/json 时，
        // 消息转换器在进入 handler 之前就抛 HttpMediaTypeNotSupportedException，
        // 而 GlobalExceptionHandler 没有它的分支，于是落到兜底 → 500 INTERNAL_ERROR。
        // 注意这不是「死分支」：不需要引入任何缺陷就能到达。
        // 状态码 500 而非 415 是本期的已知取舍 —— spec §7.6 的错误码表里没有 415，
        // 而 ErrorStatusMapper 是穷尽 switch、INVALID_REQUEST 固定映 400，
        // 想要 415 就得新增错误码（擅扩契约）。统一响应体这一条是满足的。
        mockMvc.perform(post("/api/v1/calculator/calculate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"expression\":\"1+2\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("服务内部错误"));
    }
}
