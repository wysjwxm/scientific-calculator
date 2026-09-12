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
                .standaloneSetup(new CalculatorController(useCase, policy))
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
        // angleUnit 为 null 时字段照常出现、值为 null —— spec §7.3 的措辞是
        // 「其余记录该字段为 null」，不是「字段消失」。这里断言 null 值而非 doesNotExist()：
        // 后者要求键不存在，而 CalculateResponse 上没有 @JsonInclude(NON_NULL)，键一定在。
        calculate("{\"expression\":\"1+2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.angleUnit").value(Matchers.nullValue()));
    }

    @Test
    void radianAngleUnitIsHonoured() throws Exception {
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
}
