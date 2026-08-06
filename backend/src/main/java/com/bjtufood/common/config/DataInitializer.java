package com.bjtufood.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.constant.RoleConst;
import com.bjtufood.content.broadcast.entity.Broadcast;
import com.bjtufood.content.broadcast.mapper.BroadcastMapper;
import com.bjtufood.content.category.entity.Category;
import com.bjtufood.content.category.mapper.CategoryMapper;
import com.bjtufood.dish.entity.Dish;
import com.bjtufood.dish.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * MVP 首次启动数据初始化：
 * - 库为空时插入默认管理员 admin / admin123（role=admin），便于直接登录后台预览。
 * - 附带少量示例 食堂/档口/菜品（approved + on），让首页/列表一启动即有内容可看。
 * 仅当 user 表为空时执行，已有数据则跳过，可安全重复启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final CanteenMapper canteenMapper;
    private final StallMapper stallMapper;
    private final DishMapper dishMapper;
    private final BroadcastMapper broadcastMapper;
    private final CategoryMapper categoryMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            return;
        }

        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@bjtu.edu.cn");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setNickname("超级管理员");
        admin.setRole(RoleConst.ADMIN);
        admin.setStatus("active");
        userMapper.insert(admin);
        log.info(">>> [MVP] 已初始化默认管理员账号: admin / admin123");

        Canteen canteen = new Canteen();
        canteen.setName("学一食堂");
        canteen.setLocation("本部校区");
        canteen.setDescription("MVP 示例食堂");
        canteen.setStatus("open");
        canteen.setAuditStatus("approved");
        canteenMapper.insert(canteen);

        Stall stall = new Stall();
        stall.setCanteenId(canteen.getId());
        stall.setName("川湘风味窗口");
        stall.setLocation("一楼");
        stall.setDescription("MVP 示例档口");
        stall.setStatus("open");
        stall.setAuditStatus("approved");
        stallMapper.insert(stall);

        String[] names = {"红烧肉", "麻婆豆腐", "鱼香肉丝"};
        int[] prices = {1800, 1200, 1500};
        for (int i = 0; i < names.length; i++) {
            Dish dish = new Dish();
            dish.setStallId(stall.getId());
            dish.setName(names[i]);
            dish.setPrice(prices[i]);
            dish.setTags("signature");
            dish.setStatus("on");
            dish.setAuditStatus("approved");
            dishMapper.insert(dish);
        }
        log.info(">>> [MVP] 已初始化示例 食堂/档口/菜品 数据");

        // 首页广播通知条示例（enabled，按 sort_order 升序）
        Object[][] broadcasts = {
                {"食堂开放通知", "学一食堂二层已恢复营业，欢迎前往品尝", "NOTICE", 1},
                {"招牌推荐", "川湘风味窗口「麻婆豆腐」今日特惠", "DISH", 2},
                {"活动公告", "食在交大美食季正在进行中", "URL", 3}
        };
        for (Object[] b : broadcasts) {
            Broadcast bc = new Broadcast();
            bc.setTitle((String) b[0]);
            bc.setContent((String) b[1]);
            bc.setBroadcastType((String) b[2]);
            bc.setSortOrder((Integer) b[3]);
            bc.setStatus("enabled");
            if ("DISH".equals(b[2])) {
                bc.setTargetId(dishMapper.selectOne(new LambdaQueryWrapper<com.bjtufood.dish.entity.Dish>()
                        .eq(com.bjtufood.dish.entity.Dish::getName, "麻婆豆腐")).getId());
            }
            if ("URL".equals(b[2])) {
                bc.setTargetUrl("https://mp.weixin.qq.com");
            }
            broadcastMapper.insert(bc);
        }
        log.info(">>> [MVP] 已初始化示例 首页广播通知 数据");

        // 菜品分类示例（find 宫格，按 sort_order 升序）
        String[] categories = {"早餐", "午餐", "晚餐", "夜宵", "面食", "米饭", "麻辣", "清淡"};
        for (int i = 0; i < categories.length; i++) {
            Category cat = new Category();
            cat.setName(categories[i]);
            cat.setSortOrder(i + 1);
            cat.setStatus("enabled");
            categoryMapper.insert(cat);
        }
        log.info(">>> [MVP] 已初始化示例 菜品分类 数据");
    }
}
