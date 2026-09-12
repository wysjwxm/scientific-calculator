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

    /** 面向调用方的一句话说明：这个函数算什么。中文，不含营销词。 */
    String description();

    CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers);
}
