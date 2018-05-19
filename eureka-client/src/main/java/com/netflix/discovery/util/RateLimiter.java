/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter implementation is based on token bucket algorithm. There are two parameters:
 * <ul>
 * <li>
 *     burst size - maximum number of requests allowed into the system as a burst
 * </li>
 * <li>
 *     average rate - expected number of requests per second (RateLimiters using MINUTES is also supported)
 * </li>
 * </ul>
 *
 * @author Tomasz Bak
 */
public class RateLimiter {

    /**
     * 速率转换为毫秒
     */
    private final long rateToMsConversion;

    /**
     * 消耗的令牌数
     */
    private final AtomicInteger consumedTokens = new AtomicInteger();
    /**
     * 最近的填充时间
     */
    private final AtomicLong lastRefillTime = new AtomicLong(0);

    @Deprecated
    public RateLimiter() {
        this(TimeUnit.SECONDS);
    }

    public RateLimiter(TimeUnit averageRateUnit) {
        // averageRateUnit速率单位
        switch (averageRateUnit) {
            // 秒级
            case SECONDS:
                rateToMsConversion = 1000;
                break;
            // 分钟级
            case MINUTES:
                rateToMsConversion = 60 * 1000;
                break;
            default:
                throw new IllegalArgumentException("TimeUnit of " + averageRateUnit + " is not supported");
        }
    }

    public boolean acquire(int burstSize, long averageRate) {
        return acquire(burstSize, averageRate, System.currentTimeMillis());
    }

    /**
     * 获取Token令牌
     *
     * @param burstSize         令牌桶上限
     * @param averageRate       令牌填充平均速率
     * @param currentTimeMillis 当前时间
     * @return 是否获取成功
     */
    public boolean acquire(int burstSize, long averageRate, long currentTimeMillis) {
        // 不抛出异常，而是允许所有的流量
        if (burstSize <= 0 || averageRate <= 0) { // Instead of throwing exception, we just let all the traffic go
            return true;
        }

        // 填充令牌
        refillToken(burstSize, averageRate, currentTimeMillis);
        // 消费令牌
        return consumeToken(burstSize);
    }

    /**
     * 填充已消耗的令牌
     *
     * @param burstSize         令牌桶上限
     * @param averageRate       令牌填充平均速率
     * @param currentTimeMillis 当前时间
     */
    private void refillToken(int burstSize, long averageRate, long currentTimeMillis) {
        // 获取最近的填充时间
        long refillTime = lastRefillTime.get();
        // 计算已经多久没有填充过令牌
        long timeDelta = currentTimeMillis - refillTime;

        // 计算最大可填充的令牌数
        long newTokens = timeDelta * averageRate / rateToMsConversion;
        if (newTokens > 0) {
            // 计算新的填充时间
            long newRefillTime = refillTime == 0
                    ? currentTimeMillis
                    : refillTime + newTokens * rateToMsConversion / averageRate;
            // CAS设置新的填充时间，保证只有一个线程进行填充
            if (lastRefillTime.compareAndSet(refillTime, newRefillTime)) {
                // 死循环，直到填充成功
                while (true) {
                    // 获取消费的令牌数
                    int currentLevel = consumedTokens.get();
                    // 获取消费的令牌数与令牌桶上限更小的那个数值
                    int adjustedLevel = Math.min(currentLevel, burstSize); // In case burstSize decreased
                    // 获取需要填充的令牌数量
                    int newLevel = (int) Math.max(0, adjustedLevel - newTokens);
                    // CAS设置消费的令牌数，避免和正在消费令牌的线程冲突
                    if (consumedTokens.compareAndSet(currentLevel, newLevel)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * 消费（获取）令牌
     *
     * @param burstSize 令牌桶上限
     * @return 获取令牌是否成功
     */
    private boolean consumeToken(int burstSize) {
        // 死循环直到成功或失败
        while (true) {
            // 获取消费的令牌数
            int currentLevel = consumedTokens.get();
            // 如果消费的令牌数已达上限，就返回false
            if (currentLevel >= burstSize) {
                return false;
            }
            // CAS设置消费的令牌数，避免和正在消费令牌或者填充令牌的线程冲突，设置成功返回true
            if (consumedTokens.compareAndSet(currentLevel, currentLevel + 1)) {
                return true;
            }
        }
    }

    public void reset() {
        consumedTokens.set(0);
        lastRefillTime.set(0);
    }
}
