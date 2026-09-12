package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.Operator;

import java.util.List;

/** 服务对外能力清单的读模型。所有字段都是纯数据，不含领域类型，可直接序列化。 */
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
