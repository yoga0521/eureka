/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.cluster;

import java.net.MalformedURLException;
import java.net.URL;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.lease.Lease;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;
import com.netflix.eureka.resources.ASGResource.ASGStatus;
import com.netflix.eureka.util.batcher.TaskDispatcher;
import com.netflix.eureka.util.batcher.TaskDispatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>PeerEurekaNode</code> represents a peer node to which information
 * should be shared from this node.
 *
 * <p>
 * This class handles replicating all update operations like
 * <em>Register,Renew,Cancel,Expiration and Status Changes</em> to the eureka
 * node it represents.
 * <p>
 *
 * @author Karthik Ranganathan, Greg Kim
 *
 */
public class PeerEurekaNode {

    /**
     * A time to wait before continuing work if there is network level error.
     */
    private static final long RETRY_SLEEP_TIME_MS = 100;

    /**
     * A time to wait before continuing work if there is congestion on the server side.
     */
    private static final long SERVER_UNAVAILABLE_SLEEP_TIME_MS = 1000;

    /**
     * Maximum amount of time in ms to wait for new items prior to dispatching a batch of tasks.
     */
    private static final long MAX_BATCHING_DELAY_MS = 500;

    /**
     * Maximum batch size for batched requests.
     */
    private static final int BATCH_SIZE = 250;

    private static final Logger logger = LoggerFactory.getLogger(PeerEurekaNode.class);

    public static final String BATCH_URL_PATH = "peerreplication/batch/";

    public static final String HEADER_REPLICATION = "x-netflix-discovery-replication";

    private final String serviceUrl;
    private final EurekaServerConfig config;
    private final long maxProcessingDelayMs;
    private final PeerAwareInstanceRegistry registry;
    private final String targetHost;
    private final HttpReplicationClient replicationClient;

    private final TaskDispatcher<String, ReplicationTask> batchingDispatcher;
    private final TaskDispatcher<String, ReplicationTask> nonBatchingDispatcher;

    public PeerEurekaNode(PeerAwareInstanceRegistry registry, String targetHost, String serviceUrl, HttpReplicationClient replicationClient, EurekaServerConfig config) {
        this(registry, targetHost, serviceUrl, replicationClient, config, BATCH_SIZE, MAX_BATCHING_DELAY_MS, RETRY_SLEEP_TIME_MS, SERVER_UNAVAILABLE_SLEEP_TIME_MS);
    }

    /* For testing */ PeerEurekaNode(PeerAwareInstanceRegistry registry, String targetHost, String serviceUrl,
                                     HttpReplicationClient replicationClient, EurekaServerConfig config,
                                     int batchSize, long maxBatchingDelayMs,
                                     long retrySleepTimeMs, long serverUnavailableSleepTimeMs) {
        this.registry = registry;
        this.targetHost = targetHost;
        this.replicationClient = replicationClient;

        this.serviceUrl = serviceUrl;
        this.config = config;
        this.maxProcessingDelayMs = config.getMaxTimeForReplication();

        String batcherName = getBatcherName();
        // 初始化集群复制处理器
        ReplicationTaskProcessor taskProcessor = new ReplicationTaskProcessor(targetHost, replicationClient);
        // 创建任务批处理分发器
        this.batchingDispatcher = TaskDispatchers.createBatchingTaskDispatcher(
                batcherName,
                config.getMaxElementsInPeerReplicationPool(),
                batchSize,
                config.getMaxThreadsForPeerReplication(),
                maxBatchingDelayMs,
                serverUnavailableSleepTimeMs,
                retrySleepTimeMs,
                taskProcessor
        );
        // 创建单任务分发器
        this.nonBatchingDispatcher = TaskDispatchers.createNonBatchingTaskDispatcher(
                targetHost,
                config.getMaxElementsInStatusReplicationPool(),
                config.getMaxThreadsForStatusReplication(),
                maxBatchingDelayMs,
                serverUnavailableSleepTimeMs,
                retrySleepTimeMs,
                taskProcessor
        );
    }

    /**
     * Sends the registration information of {@link InstanceInfo} receiving by
     * this node to the peer node represented by this class.
     *
     * 将实例的注册信息发送给其他节点
     *
     * @param info
     *            the instance information {@link InstanceInfo} of any instance
     *            that is send to this instance.
     * @throws Exception
     */
    public void register(final InstanceInfo info) throws Exception {
        // 任务过期时间
        long expiryTime = System.currentTimeMillis() + getLeaseRenewalOf(info);
        // 执行批处理任务
        batchingDispatcher.process(
                // 任务id，相同应用实例的相同同步操作使用相同任务编号，
                // 批处理中，接收线程(Runner)合并任务，将相同任务编号的任务合并，只执行一次。
                // 例如，Eureka-Server 同步某个应用实例的 Heartbeat 操作，接收同步的 Eureak-Server 挂了，
                // 一方面这个应用的这次操作会重试，另一方面，这个应用实例会发起新的 Heartbeat 操作，
                // 通过任务编号合并，接收同步的 Eureka-Server 恢复后，减少收到重复积压的任务。
                taskId("register", info),
                // ReplicationTask 子类
                new InstanceReplicationTask(targetHost, Action.Register, info, null, true) {
                    public EurekaHttpResponse<Void> execute() {
                        // 发送注册请求，并返回结果
                        return replicationClient.register(info);
                    }
                },
                expiryTime
        );
    }

    /**
     * Send the cancellation information of an instance to the node represented
     * by this class.
     *
     * 将实例的取消信息发送给其他节点
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @throws Exception
     */
    public void cancel(final String appName, final String id) throws Exception {
        // 任务过期时间
        long expiryTime = System.currentTimeMillis() + maxProcessingDelayMs;
        // 执行批处理任务
        batchingDispatcher.process(
                // 任务id，相同应用实例的相同同步操作使用相同任务编号，
                // 批处理中，接收线程(Runner)合并任务，将相同任务编号的任务合并，只执行一次。
                // 例如，Eureka-Server 同步某个应用实例的 Heartbeat 操作，接收同步的 Eureak-Server 挂了，
                // 一方面这个应用的这次操作会重试，另一方面，这个应用实例会发起新的 Heartbeat 操作，
                // 通过任务编号合并，接收同步的 Eureka-Server 恢复后，减少收到重复积压的任务。
                taskId("cancel", appName, id),
                // ReplicationTask 子类
                new InstanceReplicationTask(targetHost, Action.Cancel, appName, id) {
                    @Override
                    public EurekaHttpResponse<Void> execute() {
                        // 发送下线请求，并返回结果
                        return replicationClient.cancel(appName, id);
                    }

                    @Override
                    public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
                        // 当Eureka-Server不存在下线的应用实例时，返回404，打印错误日志
                        super.handleFailure(statusCode, responseEntity);
                        if (statusCode == 404) {
                            logger.warn("{}: missing entry.", getTaskName());
                        }
                    }
                },
                expiryTime
        );
    }

    /**
     * Send the heartbeat information of an instance to the node represented by
     * this class. If the instance does not exist the node, the instance
     * registration information is sent again to the peer node.
     *
     * 将实例的续约信息发送给其他节点
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param info
     *            the instance info {@link InstanceInfo} of the instance.
     * @param overriddenStatus
     *            the overridden status information if any of the instance.
     * @throws Throwable
     */
    public void heartbeat(final String appName, final String id,
                          final InstanceInfo info, final InstanceStatus overriddenStatus,
                          boolean primeConnection) throws Throwable {
        if (primeConnection) {
            // We do not care about the result for priming request.
            replicationClient.sendHeartBeat(appName, id, info, overriddenStatus);
            return;
        }
        // 同步任务
        ReplicationTask replicationTask = new InstanceReplicationTask(targetHost, Action.Heartbeat, info, overriddenStatus, false) {
            @Override
            public EurekaHttpResponse<InstanceInfo> execute() throws Throwable {
                // 发送续约请求，并返回结果
                return replicationClient.sendHeartBeat(appName, id, info, overriddenStatus);
            }

            @Override
            public void handleFailure(int statusCode, Object responseEntity) throws Throwable {
                // 当Eureka-Server不存在续约的应用实例时，返回404，打印错误日志
                super.handleFailure(statusCode, responseEntity);
                if (statusCode == 404) {
                    logger.warn("{}: missing entry.", getTaskName());
                    // 如果实例信息不为null，注册该实例信息
                    if (info != null) {
                        logger.warn("{}: cannot find instance id {} and hence replicating the instance with status {}",
                                getTaskName(), info.getId(), info.getStatus());
                        register(info);
                    }
                } else if (config.shouldSyncWhenTimestampDiffers()) {
                    // 本地的应用实例的lastDirtyTimestamp小于Eureka-Server应用实例的lastDirtyTimestamp
                    InstanceInfo peerInstanceInfo = (InstanceInfo) responseEntity;
                    if (peerInstanceInfo != null) {
                        // 覆盖注册本地应用实例
                        syncInstancesIfTimestampDiffers(appName, id, info, peerInstanceInfo);
                    }
                }
            }
        };
        // 任务过期时间
        long expiryTime = System.currentTimeMillis() + getLeaseRenewalOf(info);
        // 执行批处理任务
        batchingDispatcher.process(taskId("heartbeat", info), replicationTask, expiryTime);
    }

    /**
     * Send the status information of of the ASG represented by the instance.
     *
     * <p>
     * ASG (Autoscaling group) names are available for instances in AWS and the
     * ASG information is used for determining if the instance should be
     * registered as {@link InstanceStatus#DOWN} or {@link InstanceStatus#UP}.
     *
     * @param asgName
     *            the asg name if any of this instance.
     * @param newStatus
     *            the new status of the ASG.
     */
    public void statusUpdate(final String asgName, final ASGStatus newStatus) {
        long expiryTime = System.currentTimeMillis() + maxProcessingDelayMs;
        nonBatchingDispatcher.process(
                asgName,
                new AsgReplicationTask(targetHost, Action.StatusUpdate, asgName, newStatus) {
                    public EurekaHttpResponse<?> execute() {
                        return replicationClient.statusUpdate(asgName, newStatus);
                    }
                },
                expiryTime
        );
    }

    /**
     *
     * Send the status update of the instance.
     *
     * 将实例的状态更新信息发送给其他节点
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param newStatus
     *            the new status of the instance.
     * @param info
     *            the instance information of the instance.
     */
    public void statusUpdate(final String appName, final String id,
                             final InstanceStatus newStatus, final InstanceInfo info) {
        // 任务过期时间
        long expiryTime = System.currentTimeMillis() + maxProcessingDelayMs;
        // 执行批处理任务
        batchingDispatcher.process(
                // 任务id，相同应用实例的相同同步操作使用相同任务编号，
                // 批处理中，接收线程(Runner)合并任务，将相同任务编号的任务合并，只执行一次。
                // 例如，Eureka-Server 同步某个应用实例的 Heartbeat 操作，接收同步的 Eureak-Server 挂了，
                // 一方面这个应用的这次操作会重试，另一方面，这个应用实例会发起新的 Heartbeat 操作，
                // 通过任务编号合并，接收同步的 Eureka-Server 恢复后，减少收到重复积压的任务
                taskId("statusUpdate", appName, id),
                // ReplicationTask 子类
                new InstanceReplicationTask(targetHost, Action.StatusUpdate, info, null, false) {
                    @Override
                    public EurekaHttpResponse<Void> execute() {
                        // 发送状态更新请求，并返回结果
                        return replicationClient.statusUpdate(appName, id, newStatus, info);
                    }
                },
                expiryTime
        );
    }

    /**
     * Delete instance status override.
     *
     * 将实例删除覆盖状态信息发送给其他节点
     *
     * @param appName
     *            the application name of the instance.
     * @param id
     *            the unique identifier of the instance.
     * @param info
     *            the instance information of the instance.
     */
    public void deleteStatusOverride(final String appName, final String id, final InstanceInfo info) {
        // 任务过期时间
        long expiryTime = System.currentTimeMillis() + maxProcessingDelayMs;
        // 执行批处理任务
        batchingDispatcher.process(
                // 任务id，相同应用实例的相同同步操作使用相同任务编号，
                // 批处理中，接收线程(Runner)合并任务，将相同任务编号的任务合并，只执行一次。
                // 例如，Eureka-Server 同步某个应用实例的 Heartbeat 操作，接收同步的 Eureak-Server 挂了，
                // 一方面这个应用的这次操作会重试，另一方面，这个应用实例会发起新的 Heartbeat 操作，
                // 通过任务编号合并，接收同步的 Eureka-Server 恢复后，减少收到重复积压的任务
                taskId("deleteStatusOverride", appName, id),
                // ReplicationTask 子类
                new InstanceReplicationTask(targetHost, Action.DeleteStatusOverride, info, null, false) {
                    @Override
                    public EurekaHttpResponse<Void> execute() {
                        // 发送删除覆盖状态请求，并返回结果
                        return replicationClient.deleteStatusOverride(appName, id, info);
                    }
                },
                expiryTime);
    }

    /**
     * Get the service Url of the peer eureka node.
     *
     * @return the service Url of the peer eureka node.
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((serviceUrl == null) ? 0 : serviceUrl.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PeerEurekaNode other = (PeerEurekaNode) obj;
        if (serviceUrl == null) {
            if (other.serviceUrl != null) {
                return false;
            }
        } else if (!serviceUrl.equals(other.serviceUrl)) {
            return false;
        }
        return true;
    }

    /**
     * Shuts down all resources used for peer replication.
     */
    public void shutDown() {
        batchingDispatcher.shutdown();
        nonBatchingDispatcher.shutdown();
    }

    /**
     * Synchronize {@link InstanceInfo} information if the timestamp between
     * this node and the peer eureka nodes vary.
     */
    private void syncInstancesIfTimestampDiffers(String appName, String id, InstanceInfo info, InstanceInfo infoFromPeer) {
        try {
            if (infoFromPeer != null) {
                logger.warn("Peer wants us to take the instance information from it, since the timestamp differs,"
                        + "Id : {} My Timestamp : {}, Peer's timestamp: {}", id, info.getLastDirtyTimestamp(), infoFromPeer.getLastDirtyTimestamp());

                if (infoFromPeer.getOverriddenStatus() != null && !InstanceStatus.UNKNOWN.equals(infoFromPeer.getOverriddenStatus())) {
                    logger.warn("Overridden Status info -id {}, mine {}, peer's {}", id, info.getOverriddenStatus(), infoFromPeer.getOverriddenStatus());
                    // 将覆盖状态保存到覆盖状态集合
                    registry.storeOverriddenStatusIfRequired(appName, id, infoFromPeer.getOverriddenStatus());
                }
                // 注册来自节点应用实例信息
                registry.register(infoFromPeer, true);
            }
        } catch (Throwable e) {
            logger.warn("Exception when trying to set information from peer :", e);
        }
    }

    public String getBatcherName() {
        String batcherName;
        try {
            batcherName = new URL(serviceUrl).getHost();
        } catch (MalformedURLException e1) {
            batcherName = serviceUrl;
        }
        return "target_" + batcherName;
    }

    private static String taskId(String requestType, String appName, String id) {
        return requestType + '#' + appName + '/' + id;
    }

    private static String taskId(String requestType, InstanceInfo info) {
        return taskId(requestType, info.getAppName(), info.getId());
    }

    private static int getLeaseRenewalOf(InstanceInfo info) {
        return (info.getLeaseInfo() == null ? Lease.DEFAULT_DURATION_IN_SECS : info.getLeaseInfo().getRenewalIntervalInSecs()) * 1000;
    }
}
