package com.kyrie.controller;

import com.kyrie.model.po.XcUser;
import com.kyrie.service.WxAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

/**
 * @auther: jijin
 * @date: 2023/10/2 19:27 周一
 * @project_name: MyWechatLoginOAuth2
 * @version: 1.0
 * @description TODO 微信回调的接口
 */
@Slf4j
@Controller
public class WxLoginController {
    @Autowired
    WxAuthService wxAuthService;

    @RequestMapping("/wxLogin")
    public String wxLogin(String code, String state) throws IOException {
        log.debug("微信扫码回调,code:{},state:{}", code, state);
        //远程调用微信请令牌，拿到令牌查询用户信息，将用户信息写入本项目数据库
        XcUser xcUser = wxAuthService.wxAuth(code);

        //用户拒绝了重定向到错误页面
        if (xcUser == null) return "redirect:http://www.51xuecheng.cn/error.html";

        //用户同意登录，重定向到统一认证入口
        String username = xcUser.getUsername();
        return "redirect:http://www.51xuecheng.cn/sign.html?username=" + username + "&authType=wx";
    }
}
