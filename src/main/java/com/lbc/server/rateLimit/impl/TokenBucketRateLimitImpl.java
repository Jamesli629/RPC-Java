package com.lbc.server.rateLimit.impl;

import com.lbc.server.rateLimit.RateLimit;

/**
 * @author Lbc
 * @date 2024/10/11 22:27
 **/
public class TokenBucketRateLimitImpl implements RateLimit {

    //令牌产生速率（单位为ms）
    private final int rate;
    //桶容量
    private final int capacity;
    //当前桶容量
    private volatile int curCapacity;
    //时间戳
    private volatile long timeStamp = System.currentTimeMillis();

    public TokenBucketRateLimitImpl(int rate, int capacity) {
        this.rate = rate;
        this.capacity = capacity;
        this.curCapacity = capacity;
    }

    @Override
    public synchronized boolean getToken() {
        //如果当前桶还有剩余，就直接返回
        if (curCapacity > 0) {
            curCapacity--;
            return true;
        }
        //如果桶无剩余
        long current = System.currentTimeMillis();
        //如果距离上一次的请求的时间大于rate的时间
        if (current - timeStamp > rate) {
            //计算这段时间间隔中生成的令牌，如果>2,桶容量加上（计算的令牌-1）
            //如果==1，就不做操作（因为这一次操作要消耗一个令牌）
            if ((current - timeStamp) / rate >= 2) {
                curCapacity += (int)(current - timeStamp) / rate - 1;
            }
            //保持桶内令牌容量不超过上限
            if (curCapacity > capacity) {
                curCapacity = capacity;
            }
            //刷新时间戳为本次请求
            timeStamp = current;
            return true;
        }
        //获得不到，返回false
        return false;
    }
}
