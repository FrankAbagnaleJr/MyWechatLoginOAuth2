微信OAuth2.0认证，

认证模式采用策略模式，都实现AuthService接口，重写execute方法

    1. 如果是账号密码登录，实现接口@Service("password_authservice")
    2. 如果是微信登录，实现接口@Service("wx_authservice")
    3. 如果是短信登录，实现接口@Service("sms_authservice")
    4. 如果是QQ登录，实现接口@Service("qq_authservice")

在UserDetails的校验方法loadUserByUsername中，前端传入的是表单json字符串

    1.从json字符串中获取认证类型
    2.从SpringApplication中获取AuthService的实现类，根据名字拿
    3.然后执行实现类中的execute方法校验，返回UserDetails对象


用户扫码就相当于访问：

    https://open.weixin.qq.com/connect/oauth2/authorize?
        appid=wx123123&
        redirect_uri=http://192.168.101.65:8080/wxlogin/&
        response_type=code&
        scope=snsapi_userinfo&
        state=STATE
        #wechat_redirect

    appid =                       微信注册后给的id
    redirect_uri = redirectUrl    重定向的地址，用户确认后微信调用你的接口给你授权码.(String redirectUrl = URLEncoder.encode("http://192.168.101.65:8080/wxLogin","UTF-8");)
    response_type = code          固定就这样写
    scope = snsapi_userinfo       固定就这样写，获取用户的信息，还有一个是只获取用户id
    state = STATE                 固定就这样写
    #wechat_redirect              这个是必带的

微信回调自定义接口，接口里再根据授权码拿令牌，访问微信的接口 和 微信返回的内容（令牌只有30天时间，过期了需要重新向客户确认）：

    https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code

    appid:        公众号的唯一标识,注册时给的
    secret :      公众号的appsecret,注册时给的
    code :        填写第一步获取的code参数，授权码
    grant_type :  固定填写为authorization_code(授权码模式)

    返回的信息：（收到直接转实体类）

    access_token :         网页授权接口调用凭证,注意:此access token与基础支持的access_token不同，（令牌）
    expires_in :           access_ token接口调用凭证超时时间，单位(秒)
    refresh_token :        用户刷新access_ token
    openid :               用户唯一标识, 请注意，在未关注公众号时，用户访问公众号的网页，也会产生一个用户和公众号唯一的OpenID
    scope :                用户授权的作用域，使用逗号(,) 分隔
    is_snapshotuser :      是否为快照页模式虚拟账号，只有当用户是快照页模式虚拟账号时返回，值为1
    unionid :              用户统一标识(针对-一个微信开放平台帐号下的应用，同- -用户的unionid是唯一的)，只有当scope为" snsapi userinfo" 时返回

    //参考代码
    private Map<String,String> getAccess_token(String code){
        //定义请求获取用户数据的路径
        String url_template = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";
        //拼接路径
        String url = String.format(url_template, appid, secret, code);
        //restTemplate远程调用此url
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, null, String.class);
        //获取响应的结果
        String result = exchange.getBody();
        //将result转成map
        Map<String,String> map = JSON.parseObject(result, Map.class);
        return map;
    }

再根据令牌拿用户信息，微信会返回个人信息：
    
    https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s&lang=zh_CN
    
    access_token : 就是上面返回的令牌
    openid :       就是上面返回的
    lang :         返回的语言，这个加不加都行

    返回的信息：（用户个人信息）

    openid :     用户的唯一标识
    nickname :   用户昵称
    sex :        用户的性别，值为1时是男性，值为2时是女性,值为0时是未知
    province :   用户个人资料填写的省份
    city :       普通用户个人资料填写的城市
    country :    国家，如中国为CN
    headimgurl : 用户头像，最后一个数值代表正方形头像大小(有0、46、64、 96. 132数值可选， 0代表 640*640正方形头像)，用户没有头像时该项为空。若用户更换头像，原有头像URL将失效。
    privilege :  用户特权信息，json 数组，如微信沃卡用户为(chinaunicom)


    
    //参考代码
    private Map<String,String> getUserinfo(String access_token,String openid){
        //定义请求获取用户数据的路径
        String url_template = "https://api.weixin.qq.com/sns/userinfo?access_token=%s&openid=%s";
        //拼接路径
        String url = String.format(url_template, access_token, openid);
        //用restTemplate远程调用
        ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
        //获取响应的结果，这里可以加个判断，如果返回码是200，则成功
        String result = new String(exchange.getBody().getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8);
        //将result转成map
        Map<String,String> map = JSON.parseObject(result, Map.class);
        return map;
    }   

保存用户信息到数据库，并返回。参考代码

    @Transactional
    public XcUser addWxUser(Map<String,String> userInfo_map){
        String unionid = userInfo_map.get("unionid");
        String nickname = userInfo_map.get("nickname");
        //根据unionid查询用户信息
        XcUser xcUser = xcUserMapper.selectOne(new LambdaQueryWrapper<XcUser>().eq(XcUser::getWxUnionid, unionid));
        //如果查到则不是新用户，直接返回
        if(xcUser !=null) return xcUser;
        //向数据库新增记录
        xcUser = new XcUser();
        String userId= UUID.randomUUID().toString();
        xcUser.setId(userId);//主键
        xcUser.setUsername(unionid);
        xcUser.setPassword(unionid);
        xcUser.setWxUnionid(unionid);
        xcUser.setNickname(nickname);
        xcUser.setName(nickname);
        xcUser.setUtype("101001");//学生类型
        xcUser.setStatus("1");//用户状态
        xcUser.setCreateTime(LocalDateTime.now());
        //插入
        int insert = xcUserMapper.insert(xcUser);

        //向用户角色关系表新增记录
        XcUserRole xcUserRole = new XcUserRole();
        xcUserRole.setId(UUID.randomUUID().toString());
        xcUserRole.setUserId(userId);
        xcUserRole.setRoleId("17");//学生角色
        xcUserRole.setCreateTime(LocalDateTime.now());
        xcUserRoleMapper.insert(xcUserRole);
        return xcUser;
    }


刷新token，用户同意后拿到的令牌只有30天，过期后将不能再获取用户信息，刷新令牌的方式如下，访问微信提供的接口：

    https://api.weixin.qq.com/sns/oauth2/refresh_token?appid=?&grant_type=refresh_token&refresh_token=?

    appid :          注册后给的id  
    grant_type :     固定填写refresh_token
    refresh_token :  通过获取令牌时候一起获取的

用户登录成功后把用户登录状态保存

















    