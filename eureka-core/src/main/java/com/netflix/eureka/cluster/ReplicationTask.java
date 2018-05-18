package com.netflix.eureka.cluster;

import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 同步任务抽象类
 * Base class for all replication tasks.
 */
abstract class ReplicationTask {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationTask.class);

    /**
     * 节点名称
     */
    protected final String peerNodeName;
    /**
     * 同步的操作类型
     */
    protected final Action action;

    ReplicationTask(String peerNodeName, Action action) {
        this.peerNodeName = peerNodeName;
        this.action = action;
    }

    /**
     * 获取任务名
     *
     * @return 任务名
     */
    public abstract String getTaskName();

    /**
     * 获取同步的操作类型
     *
     * @return 操作类型
     */
    public Action getAction() {
        return action;
    }

    /**
     * 执行同步任务
     *
     * @return 执行结果
     * @throws Throwable 异常
     */
    public abstract EurekaHttpResponse<?> execute() throws Throwable;

    /**
     * 处理成功执行同步结果
     */
    public void handleSuccess() {
    }

    /**
     * 处理失败执行同步结果
     *
     * @param statusCode     状态code
     * @param responseEntity 返回结果
     * @throws Throwable 异常
     */
    public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
        logger.warn("The replication of task {} failed with response code {}", getTaskName(), statusCode);
    }
}
