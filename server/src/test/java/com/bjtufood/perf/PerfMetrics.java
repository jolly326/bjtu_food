package com.bjtufood.perf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 性能度量发射器（仅测试期存在，不进生产包）。
 * <p>
 * 把基准结果以固定行格式同时输出到 stdout（`METRIC|key|value|unit|note`）与
 * {@code target/perf-metrics.tsv}，使「优化前 / 优化后」两轮数值可用同一命令复现、
 * 直接抄进性能对比文档，避免口头描述性能。
 * <p>
 * <b>本类只做记录，不做断言</b>：耗时数值受机器负载影响，写成断言会造成构建随机失败；
 * 缓存正确性等行为由各自的业务单元测试钉死。
 */
final class PerfMetrics {

    /** 汇总输出文件（`target/` 已被 git 忽略，不污染工作区） */
    private static final Path OUTPUT = Paths.get("target", "perf-metrics.tsv");

    private PerfMetrics() {
    }

    /** 记录一个度量项（长整型便捷入口） */
    static void emit(String key, long value, String unit, String note) {
        emit(key, (double) value, unit, note);
    }

    /** 记录一个度量项：stdout 行 + TSV 追加各一份 */
    static synchronized void emit(String key, double value, String unit, String note) {
        String text = String.format("%.3f", value);
        System.out.println("METRIC|" + key + "|" + text + "|" + unit + "|" + note);
        try {
            Path parent = OUTPUT.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(OUTPUT,
                    (String.join("\t", key, text, unit, note) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("性能度量写入失败: " + OUTPUT, e);
        }
    }
}
