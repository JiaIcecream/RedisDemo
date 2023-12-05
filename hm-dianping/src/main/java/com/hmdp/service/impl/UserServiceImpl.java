package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

//    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }

        // 2. 生成随机验证码
        String code = RandomUtil.randomNumbers(6);

        // 3. 设置session

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("验证码已发送 code {}", code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {

        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式不正确");
        }

        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());

        if (!loginForm.getCode().equals(code) || loginForm.getCode() == null) {
            return Result.fail("验证码不正确");
        }
        // 查看该phone是否再数据库中存在，存在就把数据存入到redis中，不存在就创建一个用户存入数据库和redis
        User user = query().eq("phone", loginForm.getPhone()).one();

        UserDTO userDTO = new UserDTO();

        if (user == null) {
            User newUser = new User();
            newUser.setPhone(loginForm.getPhone());
            newUser.setNickName("user:" + RandomUtil.randomString(10));
            newUser.setPassword("123");

            save(newUser);
            user = newUser;
        }

        BeanUtil.copyProperties(user, userDTO);


        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fileName, fileValue) -> fileValue.toString()));
        String token = UUID.randomUUID().toString();
        String key = LOGIN_USER_KEY + token;

        stringRedisTemplate.opsForHash().putAll(key, userMap);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    @Override
    public Result logout() {
        // 使用token 将redis中的token删除
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        String token = request.getHeader("authorization");
        stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Result.ok();
    }
}
