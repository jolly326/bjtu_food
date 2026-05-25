package com.bjtufood.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * DFA 敏感词过滤器
 * <p>
 * 功能：对评价文本进行敏感词检测与替换，防止违规内容发布。
 * 使用 DFA（Deterministic Finite Automaton，确定性有限自动机）算法，
 * 效率高、性能稳定。
 * <p>
 * 使用方式：
 * <pre>
 * // 在评价 Service 中调用
 * String filtered = sensitiveFilter.filter(content);
 * if (sensitiveFilter.containsSensitive(content)) {
 *     // 包含敏感词，拒绝提交或替换为 *
 * }
 * </pre>
 */
@Slf4j
@Component
public class SensitiveFilter {

    /** 敏感词库文件路径（classpath 下的文本文件，每行一个敏感词） */
    private static final String SENSITIVE_WORDS_FILE = "sensitive_words.txt";

    /** 替换字符 */
    private static final char REPLACE_CHAR = '*';

    /** DFA 字典树根节点 */
    private final TrieNode root = new TrieNode();

    /**
     * 初始化：项目启动时加载敏感词库到 DFA 字典树
     */
    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(SENSITIVE_WORDS_FILE);
            if (!resource.exists()) {
                log.warn("敏感词库文件不存在: {}，敏感词过滤功能不可用", SENSITIVE_WORDS_FILE);
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim();
                    if (!word.isEmpty() && !word.startsWith("#")) {
                        addWord(word);
                        count++;
                    }
                }
                log.info("敏感词库加载完成，共 {} 个敏感词", count);
            }
        } catch (Exception e) {
            log.error("加载敏感词库失败", e);
        }
    }

    /**
     * 向 DFA 字典树中添加一个敏感词
     *
     * @param word 敏感词
     */
    private void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.isEnd = true;
    }

    /**
     * 检测文本中是否包含敏感词
     *
     * @param text 待检测文本
     * @return true=包含敏感词
     */
    public boolean containsSensitive(String text) {
        // TODO: 使用 DFA 算法遍历 text，检测是否命中敏感词
        // 实现思路：
        // 1. 双重循环遍历 text 的每个字符
        // 2. 对每个起始位置，在字典树中逐字符匹配
        // 3. 如果匹配到 isEnd=true 的节点，返回 true
        // 4. 匹配失败则从下一个起始字符重新匹配
        return false; // placeholder
    }

    /**
     * 过滤敏感词，将敏感词替换为 *
     *
     * @param text 原始文本
     * @return 替换后的文本
     */
    public String filter(String text) {
        // TODO: 使用 DFA 算法遍历 text
        // 1. 检测所有敏感词位置
        // 2. 将命中位置替换为 REPLACE_CHAR
        // 3. 返回处理后的文本
        return text; // placeholder
    }

    // ==================== DFA 字典树节点 ====================

    /**
     * DFA 字典树节点
     * <p>
     * children：子节点映射（字符 → 节点）
     * isEnd：是否为一个敏感词的结尾
     */
    private static class TrieNode {
        java.util.Map<Character, TrieNode> children = new java.util.HashMap<>();
        boolean isEnd = false;
    }
}
