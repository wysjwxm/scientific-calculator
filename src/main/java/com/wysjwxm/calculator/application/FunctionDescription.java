package com.wysjwxm.calculator.application;

/** 一个函数：语言名、元数、是否受角度影响、定义域边界、一句话说明。 */
public record FunctionDescription(String name, int arity, boolean angleSensitive,
                                  String domain, String description) {
}
