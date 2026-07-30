package com.lbc.common.service;

import com.lbc.common.pojo.User;

import java.util.concurrent.CompletableFuture;

/**
 * UserService 异步接口
 * <p>
 * 返回类型为 CompletableFuture 的方法将被 ClientProxy 识别为异步调用，
 * 调用后立即返回 Future，不阻塞业务线程。
 *
 * @author Lbc
 */
public interface UserServiceAsync {

    /**
     * 根据用户ID获取用户（异步）
     */
    CompletableFuture<User> getUserByUserId(Integer id);

    /**
     * 插入用户（异步）
     */
    CompletableFuture<Integer> insertUserId(User user);
}
