package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.MathematicalConstant;
import com.wysjwxm.calculator.domain.model.expression.OperatorTable;
import com.wysjwxm.calculator.domain.model.function.BinaryFunction;
import com.wysjwxm.calculator.domain.model.function.UnaryFunction;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityQueryTest {

    private final CapabilityQuery query =
            new CapabilityQuery(new CalculationPolicy(AngleUnit.DEGREE, 1000, 34));

    private CapabilityManifest describe() {
        return query.describe(List.of(new ApiEndpoint("GET", "/x", "示例")));
    }

    @Test
    void listsEveryUnaryAndBinaryFunctionInDeclarationOrder() {
        CapabilityManifest m = describe();
        assertThat(m.unaryFunctions()).hasSize(23);
        assertThat(m.binaryFunctions()).hasSize(5);
        assertThat(m.unaryFunctions()).extracting(FunctionDescription::name)
                .containsExactlyElementsOf(
                        java.util.Arrays.stream(UnaryFunction.values())
                                .map(UnaryFunction::functionName).toList());
        assertThat(m.binaryFunctions()).extracting(FunctionDescription::name)
                .containsExactlyElementsOf(
                        java.util.Arrays.stream(BinaryFunction.values())
                                .map(BinaryFunction::functionName).toList());
    }

    @Test
    void everyFunctionCarriesANonBlankDescriptionAndDomain() {
        CapabilityManifest m = describe();
        assertThat(m.unaryFunctions()).allSatisfy(f -> {
            assertThat(f.description()).isNotBlank();
            assertThat(f.domain()).isNotBlank();
            assertThat(f.arity()).isEqualTo(1);
        });
        assertThat(m.binaryFunctions()).allSatisfy(f -> {
            assertThat(f.description()).isNotBlank();
            assertThat(f.domain()).isNotBlank();
            assertThat(f.arity()).isEqualTo(2);
        });
    }

    @Test
    void everyDomainEnumDescriptionIsNonBlankAndDistinct() {
        List<String> all = new java.util.ArrayList<>();
        for (UnaryFunction f : UnaryFunction.values()) {
            all.add(f.description());
        }
        for (BinaryFunction f : BinaryFunction.values()) {
            all.add(f.description());
        }
        for (MathematicalConstant c : MathematicalConstant.values()) {
            all.add(c.description());
        }
        assertThat(all).allSatisfy(d -> assertThat(d).isNotBlank());
        assertThat(all).doesNotHaveDuplicates();
    }

    /**
     * 清单里的说明与定义域必须逐位来自源头枚举 —— 这是 spec §12 D8 的防漂移规则：
     * 清单由源头生成而非手写，否则清单会与语言行为各说各话。
     *
     * <p>与「非空 + 不重复」互补：那条管枚举侧文本本身，这条管**清单有没有如实转述**。
     * 少了它，在 CapabilityQuery 里对某个未被线上钉子钉住的函数写死说明是不会变红的。
     */
    @Test
    void manifestDescriptionsAndDomainsComeFromTheDomainEnums() {
        CapabilityManifest m = describe();
        assertThat(m.unaryFunctions()).extracting(FunctionDescription::description)
                .containsExactlyElementsOf(java.util.Arrays.stream(UnaryFunction.values())
                        .map(UnaryFunction::description).toList());
        assertThat(m.unaryFunctions()).extracting(FunctionDescription::domain)
                .containsExactlyElementsOf(java.util.Arrays.stream(UnaryFunction.values())
                        .map(f -> f.domain().description()).toList());
        assertThat(m.binaryFunctions()).extracting(FunctionDescription::description)
                .containsExactlyElementsOf(java.util.Arrays.stream(BinaryFunction.values())
                        .map(BinaryFunction::description).toList());
        assertThat(m.binaryFunctions()).extracting(FunctionDescription::domain)
                .containsExactlyElementsOf(java.util.Arrays.stream(BinaryFunction.values())
                        .map(BinaryFunction::domainDescription).toList());
        assertThat(m.constants()).extracting(ConstantDescription::description)
                .containsExactlyElementsOf(java.util.Arrays.stream(MathematicalConstant.values())
                        .map(MathematicalConstant::description).toList());
    }

    @Test
    void angleSensitivityMatchesTheDomainEnums() {
        CapabilityManifest m = describe();
        assertThat(m.unaryFunctions()).allSatisfy(f ->
                assertThat(f.angleSensitive())
                        .isEqualTo(UnaryFunction.valueOf(f.name().toUpperCase()).angleSensitive()));
        assertThat(m.binaryFunctions()).allSatisfy(f ->
                assertThat(f.angleSensitive())
                        .isEqualTo(BinaryFunction.valueOf(f.name().toUpperCase()).angleSensitive()));
    }

    @Test
    void operatorsComeStraightFromTheOperatorTable() {
        // spec §14 第 6 条：清单必须与 OperatorTable 一致
        assertThat(describe().operators()).isEqualTo(OperatorTable.all());
    }

    @Test
    void constantsAreListedInDeclarationOrder() {
        assertThat(describe().constants()).extracting(ConstantDescription::name)
                .containsExactlyElementsOf(MathematicalConstant.names());
    }

    @Test
    void limitsMatchTheConfiguredPolicyAndTheDomainBounds() {
        // 刻意用一组与线上配置不同的值：若 CapabilityQuery 不读 policy 而是写死，
        // 这条断言必须变红。用线上同款 1000/34 则与断言值同源，关不上缺口。
        CalculationPolicy policy = new CalculationPolicy(AngleUnit.RADIAN, 500, 11);
        CapabilityQuery policyQuery = new CapabilityQuery(policy);
        assertThat(policyQuery.describe(List.of()).limits()).isEqualTo(new Limits(
                policy.maxExpressionLength(), policy.divisionPrecision(),
                Numbers.MAX_EXACT_DIGITS, DecimalNumber.MAX_SCALE_MAGNITUDE));
    }

    @Test
    void endpointsArePassedThroughUnchanged() {
        List<ApiEndpoint> given = List.of(new ApiEndpoint("GET", "/x", "示例"));
        assertThat(query.describe(given).endpoints()).isEqualTo(given);
    }
}
