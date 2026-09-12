package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.Operator;

import java.util.List;

/**
 * 服务对外能力清单的读模型。
 *
 * <p>字段里含领域类型（{@code Operator}、{@code AngleUnit}），这是刻意的：清单的职责就是
 * 如实转述语言的语法与算子表，另造一套并行类型只会多一层需要同步的映射。这些类型都是
 * 不可变值对象且能被 Jackson 直接序列化，不构成防腐层漏洞。
 */
public record CapabilityManifest(
        List<ApiEndpoint> endpoints,
        List<ConstantDescription> constants,
        List<FunctionDescription> unaryFunctions,
        List<FunctionDescription> binaryFunctions,
        List<Operator> operators,
        List<AngleUnit> angleUnits,
        AngleUnit defaultAngleUnit,
        Limits limits) {
}
