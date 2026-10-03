package com.bjtufood.canteen.constant;

import java.util.List;
import java.util.Set;

/**
 * 楼层受控字典（<b>唯一真源 · 值即汉字</b>）。
 * <p>
 * 口径见 docs/schema/stall.md：
 * <ul>
 *   <li><b>值域</b>：{@code 负一层 / 一层 / 二层 / 三层 / 四层}（存储值 = 显示值 ⇒ <b>全链零映射</b>）；</li>
 *   <li><b>变更规则</b>：<b>新增</b>楼层 = 两端各加一项（免迁移）；<b>删除 / 改名</b> = 先把该楼层的档口
 *       改到其它楼层，再改本字典 —— 因此<b>字典外的值不应存在</b>，管理端与端上都不需要兜底；</li>
 *   <li>字典外的历史值（如 {@code 1F}）由 `db` 初始化 / 种子脚本一次性改写，<b>禁线上 ALTER</b>。</li>
 * </ul>
 * 管理端 A2 档口管理的楼层为<b>下拉</b>、小程序纠错页为<b>固定字典单选</b>，两处同用本字典。
 */
public final class FloorDict {

    /** 楼层字典取值（顺序即展示顺序） */
    public static final List<String> VALUES = List.of("负一层", "一层", "二层", "三层", "四层");

    private static final Set<String> SET = Set.copyOf(VALUES);

    /** 是否命中字典（首尾空白已规范化） */
    public static boolean isValid(String floor) {
        return floor != null && SET.contains(floor.trim());
    }

    /** 规范化：仅 trim（不做任何映射 —— 值即汉字） */
    public static String normalize(String floor) {
        return floor == null ? null : floor.trim();
    }

    private FloorDict() {
    }
}
