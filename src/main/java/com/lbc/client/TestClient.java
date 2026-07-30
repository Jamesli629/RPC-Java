package com.lbc.client;

import com.lbc.client.proxy.ClientProxy;
import com.lbc.common.config.ConfigManager;
import com.lbc.common.pojo.User;
import com.lbc.common.service.UserService;

public class TestClient {
    public static void main(String[] args) {
        // 从配置读取服务地址和端口（直连模式时使用）
        ConfigManager config = ConfigManager.getInstance();
        String host = config.getString("rpc.server.host", "127.0.0.1");
        int port = config.getInt("rpc.server.port", 9999);

        ClientProxy clientProxy = new ClientProxy();
//        ClientProxy clientProxy = new ClientProxy(host, port);
        UserService proxy = clientProxy.getProxy(UserService.class);

        User user = proxy.getUserByUserId(1);
        System.out.println("从服务端得到的user=" + user.toString());

        User u = User.builder().id(100).userName("wxx").sex(true).build();
        Integer id = proxy.insertUserId(u);
        System.out.println("向服务端插入user的id" + id);
    }
}
