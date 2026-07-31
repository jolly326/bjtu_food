package com.bjtufood.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtufood.auth.entity.User;
import com.bjtufood.auth.mapper.UserMapper;
import com.bjtufood.canteen.entity.Canteen;
import com.bjtufood.canteen.entity.Stall;
import com.bjtufood.canteen.mapper.CanteenMapper;
import com.bjtufood.canteen.mapper.StallMapper;
import com.bjtufood.common.constant.RoleConst;
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
    }
}
