package com.bjtufood.common.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        if (text == null || text.isEmpty()) {
            return false;
        }
        int n = text.length();
        for (int i = 0; i < n; i++) {
            TrieNode node = root;
            int j = i;
            while (j < n) {
                node = node.children.get(text.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.isEnd) {
                    return true;
                }
                j++;
            }
        }
        return false;
    }

    /**
     * 过滤敏感词，将命中的敏感词（连续字符）替换为 *
     *
     * @param text 原始文本
     * @return 替换后的文本；null/空串原样返回
     */
    public String filter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        int n = text.length();
        StringBuilder sb = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            // 从当前位置尝试在字典树中匹配最长敏感词
            TrieNode node = root;
            int matchEnd = -1; // 命中的末尾索引（不含）
            int j = i;
            while (j < n) {
                node = node.children.get(text.charAt(j));
                if (node == null) {
                    break;
                }
                if (node.isEnd) {
                    matchEnd = j + 1;
                }
                j++;
            }
            if (matchEnd != -1) {
                // 命中：将 [i, matchEnd) 连续字符替换为 *
                for (int k = i; k < matchEnd; k++) {
                    sb.append(REPLACE_CHAR);
                }
                i = matchEnd;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        return sb.toString();
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
