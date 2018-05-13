package com.netflix.discovery;

import java.util.TimerTask;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.LongGauge;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A supervisor task that schedules subtasks while enforce a timeout.
 * Wrapped subtasks must be thread safe.
 *
 * @author David Qiang Liu
 */
public class TimedSupervisorTask extends TimerTask {
    private static final Logger logger = LoggerFactory.getLogger(TimedSupervisorTask.class);

    /**
     * 超时计数，配合netflix servo监控
     */
    private final Counter timeoutCounter;
    /**
     * 拒绝计数，配合netflix servo监控
     */
    private final Counter rejectedCounter;
    /**
     * 报错计数，配合netflix servo监控
     */
    private final Counter throwableCounter;
    private final LongGauge threadPoolLevelGauge;

    /**
     * 定时任务服务，定时发起子任务
     */
    private final ScheduledExecutorService scheduler;
    /**
     * 执行子线程任务线程池
     */
    private final ThreadPoolExecutor executor;
    /**
     * 子任务超时时间
     */
    private final long timeoutMillis;
    /**
     * 子任务
     */
    private final Runnable task;

    /**
     * 执行频率
     */
    private final AtomicLong delay;
    /**
     * 最大执行频率，子任务超时时使用
     */
    private final long maxDelay;

    public TimedSupervisorTask(String name, ScheduledExecutorService scheduler, ThreadPoolExecutor executor,
                               int timeout, TimeUnit timeUnit, int expBackOffBound, Runnable task) {
        this.scheduler = scheduler;
        this.executor = executor;
        this.timeoutMillis = timeUnit.toMillis(timeout);
        this.task = task;
        this.delay = new AtomicLong(timeoutMillis);
        this.maxDelay = timeoutMillis * expBackOffBound;

        // Initialize the counters and register.
        timeoutCounter = Monitors.newCounter("timeouts");
        rejectedCounter = Monitors.newCounter("rejectedExecutions");
        throwableCounter = Monitors.newCounter("throwables");
        threadPoolLevelGauge = new LongGauge(MonitorConfig.builder("threadPoolUsed").build());
        Monitors.registerObject(name, this);
    }

    @Override
    public void run() {
        Future<?> future = null;
        try {
            // 提交子任务
            future = executor.submit(task);
            threadPoolLevelGauge.set((long) executor.getActiveCount());
            // 阻塞获取任务的执行结果或者超时
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);  // block until done or timeout
            // 设置下一次执行任务的时间间隔
            delay.set(timeoutMillis);
            threadPoolLevelGauge.set((long) executor.getActiveCount());
        } catch (TimeoutException e) {
            logger.warn("task supervisor timed out", e);
            // 超时计数
            timeoutCounter.increment();

            // 设置下一次执行任务的时间间隔
            long currentDelay = delay.get();
            long newDelay = Math.min(maxDelay, currentDelay * 2);
            delay.compareAndSet(currentDelay, newDelay);

        } catch (RejectedExecutionException e) {
            if (executor.isShutdown() || scheduler.isShutdown()) {
                logger.warn("task supervisor shutting down, reject the task", e);
            } else {
                logger.warn("task supervisor rejected the task", e);
            }

            // 拒绝计数
            rejectedCounter.increment();
        } catch (Throwable e) {
            if (executor.isShutdown() || scheduler.isShutdown()) {
                logger.warn("task supervisor shutting down, can't accept the task");
            } else {
                logger.warn("task supervisor threw an exception", e);
            }

            // 报错计数
            throwableCounter.increment();
        } finally {
            // 取消未完成的任务
            if (future != null) {
                future.cancel(true);
            }

            // 调度下一次TimedSupervisorTask任务
            if (!scheduler.isShutdown()) {
                scheduler.schedule(this, delay.get(), TimeUnit.MILLISECONDS);
            }
        }
    }
}