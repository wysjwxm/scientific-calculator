package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.CalculationCommand;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.interfaces.dto.CalculateRequest;
import com.wysjwxm.calculator.interfaces.dto.CalculateResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 求值端点。
 *
 * <p>本类承担防腐层职责：把 JSON DTO 翻译成应用层命令，再把领域对象翻译回
 * 响应 DTO。领域类型不直接充当传输契约。
 *
 * <p>本期是 MVP：没有变量存储与历史，故没有变量与历史端点（Phase 2 补，见
 * docs/mvp-and-roadmap.md）；能力清单由同包的 {@link CapabilityController} 提供。
 */
@RestController
@RequestMapping("/api/v1/calculator")
public class CalculatorController {

    private final CalculationUseCase calculationUseCase;

    public CalculatorController(CalculationUseCase calculationUseCase) {
        this.calculationUseCase = calculationUseCase;
    }

    @PostMapping("/calculate")
    public CalculateResponse calculate(@RequestBody CalculateRequest request) {
        CalculationCommand command = new CalculationCommand(
                request.expression(), request.angleUnit(), toCalcNumbers(request.variables()));
        return CalculateResponse.from(calculationUseCase.calculate(command));
    }

    /**
     * 把请求体里的变量翻译成领域数值。
     *
     * <p><b>必须在此拒收 null 值</b>：请求体 {"variables":{"x":null}} 经 Jackson
     * 反序列化就得到 value 为 null 的映射，而 DecimalNumber 的紧凑构造器用
     * Objects.requireNonNull 抛的是**裸 NullPointerException** —— 那会一路逃成
     * HTTP 500。这里必须**在 new DecimalNumber(value) 之前**拦住（→ 400）。
     * 不能指望 CalculationCommand 的守卫：那个守卫在这行**之后**才执行。
     */
    private Map<String, CalcNumber> toCalcNumbers(Map<String, BigDecimal> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, CalcNumber> converted = new LinkedHashMap<>();
        raw.forEach((name, value) -> {
            if (value == null) {
                throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                        "变量 " + name + " 的值不能为 null");
            }
            converted.put(name, new DecimalNumber(value));
        });
        return converted;
    }
}
