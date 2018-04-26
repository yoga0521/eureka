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

package com.netflix.eureka;

import com.netflix.eureka.aws.AwsBindingStrategy;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Configuration information required by the eureka server to operate.
 *
 * <p>
 * Most of the required information is provided by the default configuration
 * {@link com.netflix.eureka.DefaultEurekaServerConfig}.
 *
 * Note that all configurations are not effective at runtime unless and
 * otherwise specified.
 * </p>
 *
 * @author Karthik Ranganathan
 *
 */
public interface EurekaServerConfig {

    /**
     * Gets the <em>AWS Access Id</em>. This is primarily used for
     * <em>Elastic IP Biding</em>. The access id should be provided with
     * appropriate AWS permissions to bind the EIP.
     *
     * AWS相关，跳过
     *
     * @return
     */
    String getAWSAccessId();

    /**
     * Gets the <em>AWS Secret Key</em>. This is primarily used for
     * <em>Elastic IP Biding</em>. The access id should be provided with
     * appropriate AWS permissions to bind the EIP.
     *
     * AWS相关，跳过
     *
     * @return
     */
    String getAWSSecretKey();

    /**
     * Gets the number of times the server should try to bind to the candidate
     * EIP.
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * AWS相关，跳过
     *
     * @return the number of times the server should try to bind to the
     *         candidate EIP.
     */
    int getEIPBindRebindRetries();

    /**
     * Get the interval with which the server should check if the EIP is bound
     * and should try to bind in the case if it is already not bound, iff the EIP
     * is not currently bound.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * AWS相关，跳过
     *
     * @return the time in milliseconds.
     */
    int getEIPBindingRetryIntervalMsWhenUnbound();

    /**
     * Gets the interval with which the server should check if the EIP is bound
     * and should try to bind in the case if it is already not bound, iff the EIP
     * is already bound. (so this refresh is just for steady state checks)
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * AWS相关，跳过
     *
     * @return the time in milliseconds.
     */
    int getEIPBindingRetryIntervalMs();

    /**
     * Checks to see if the eureka server is enabled for self preservation.
     *
     * <p>
     * When enabled, the server keeps track of the number of <em>renewals</em>
     * it should receive from the server. Any time, the number of renewals drops
     * below the threshold percentage as defined by
     * {@link #getRenewalPercentThreshold()}, the server turns off expirations
     * to avert danger.This will help the server in maintaining the registry
     * information in case of network problems between client and the server.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * 是否开启自我保护模式
     *
     * @return true to enable self preservation, false otherwise.
     */
    boolean shouldEnableSelfPreservation();

    /**
     * The minimum percentage of renewals that is expected from the clients in
     * the period specified by {@link #getRenewalThresholdUpdateIntervalMs()}.
     * If the renewals drop below the threshold, the expirations are disabled if
     * the {@link #shouldEnableSelfPreservation()} is enabled.
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * 自我保护百分比阈值，超过这个值就会开启自我保护模式。默认为0.85
     *
     * @return value between 0 and 1 indicating the percentage. For example,
     *         <code>85%</code> will be specified as <code>0.85</code>.
     */
    double getRenewalPercentThreshold();

    /**
     * The interval with which the threshold as specified in
     * {@link #getRenewalPercentThreshold()} needs to be updated.
     *
     * 自我保护阈值更新的时间间隔，单位为毫秒（ms）
     *
     * @return time in milliseconds indicating the interval.
     */
    int getRenewalThresholdUpdateIntervalMs();

    /**
     * The interval with which the information about the changes in peer eureka
     * nodes is updated. The user can use the DNS mechanism or dynamic
     * configuration provided by <a href="https://github.com/Netflix/archaius">Archaius</a> to
     * change the information dynamically.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * eureka集群节点更新时间间隔，单位为毫秒（ms）
     *
     * @return timer in milliseconds indicating the interval.
     */
    int getPeerEurekaNodesUpdateIntervalMs();

    /**
     * If set to true, the replicated data send in the request will be always compressed.
     * This does not define response path, which is driven by "Accept-Encoding" header.
     *
     * 是否开启eureka集群之间复制信息请求压缩
     */
    boolean shouldEnableReplicatedRequestCompression();

    /**
     * Get the number of times the replication events should be retried with
     * peers.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return the number of retries.
     */
    @Deprecated
    int getNumberOfReplicationRetries();

    /**
     * Gets the interval with which the status information about peer nodes is
     * updated.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return time in milliseconds indicating the interval.
     */
    @Deprecated
    int getPeerEurekaStatusRefreshTimeIntervalMs();

    /**
     * Gets the time to wait when the eureka server starts up unable to get
     * instances from peer nodes. It is better not to start serving rightaway
     * during these scenarios as the information that is stored in the registry
     * may not be complete.
     *
     * When the instance registry starts up empty, it builds over time when the
     * clients start to send heartbeats and the server requests the clients for
     * registration information.
     *
     * 如果eureka在启动的时候，获取不到注册信息，就等待。单位为毫秒（ms）
     *
     * @return time in milliseconds.
     */
    int getWaitTimeInMsWhenSyncEmpty();

    /**
     * Gets the timeout value for connecting to peer eureka nodes for
     * replication.
     *
     * eureka集群节点之间请求连接超时的时间（进行复制信息操作），单位为毫秒（ms）
     *
     * @return timeout value in milliseconds.
     */
    int getPeerNodeConnectTimeoutMs();

    /**
     * Gets the timeout value for reading information from peer eureka nodes for
     * replication.
     *
     * eureka集群节点之间请求读取超时的时间（进行复制信息操作），单位为毫秒（ms）
     *
     * @return timeout value in milliseconds.
     */
    int getPeerNodeReadTimeoutMs();

    /**
     * Gets the total number of <em>HTTP</em> connections allowed to peer eureka
     * nodes for replication.
     *
     * eureka集群节点之间请求的连接总数（进行复制信息操作）
     *
     * @return total number of allowed <em>HTTP</em> connections.
     */
    int getPeerNodeTotalConnections();

    /**
     * Gets the total number of <em>HTTP</em> connections allowed to a
     * particular peer eureka node for replication.
     *
     * 单个eureka节点的请求连接总数（进行复制信息操作）
     *
     * @return total number of allowed <em>HTTP</em> connections for a peer
     *         node.
     */
    int getPeerNodeTotalConnectionsPerHost();

    /**
     * Gets the idle time after which the <em>HTTP</em> connection should be
     * cleaned up.
     *
     * 获取Eureka-Server请求空闲时间，单位为毫秒
     *
     * @return idle time in seconds.
     */
    int getPeerNodeConnectionIdleTimeoutSeconds();

    /**
     * Get the time for which the delta information should be cached for the
     * clients to retrieve the value without missing it.
     *
     * 获取增量信息缓存的时间，以避免客户端检索的时候丢失，单位为毫秒
     *
     * @return time in milliseconds
     */
    long getRetentionTimeInMSInDeltaQueue();

    /**
     * Get the time interval with which the clean up task should wake up and
     * check for expired delta information.
     *
     * 获取清理任务应唤醒的时间间隔，并检查过期的增量信息，单位为毫秒
     *
     * @return time in milliseconds.
     */
    long getDeltaRetentionTimerIntervalInMs();

    /**
     * Get the time interval with which the task that expires instances should
     * wake up and run.
     *
     * 租约过期定时任务执行频率，单位为毫秒。
     *
     * @return time in milliseconds.
     */
    long getEvictionIntervalTimerInMs();

    /**
     * Get the timeout value for querying the <em>AWS</em> for <em>ASG</em>
     * information.
     *
     * AWS相关
     *
     * @return timeout value in milliseconds.
     */
    int getASGQueryTimeoutMs();

    /**
     * Get the time interval with which the <em>ASG</em> information must be
     * queried from <em>AWS</em>.
     *
     * AWS相关
     *
     * @return time in milliseconds.
     */
    long getASGUpdateIntervalMs();

    /**
     * Get the expiration value for the cached <em>ASG</em> information
     *
     * AWS相关
     *
     * @return time in milliseconds.
     */
    long getASGCacheExpiryTimeoutMs();

    /**
     * Gets the time for which the registry payload should be kept in the cache
     * if it is not invalidated by change events.
     *
     * 当注册表信息被改变时，则其被保存在缓存中过期时间，单位为秒，默认值为180s。
     *
     * @return time in seconds.
     */
    long getResponseCacheAutoExpirationInSeconds();

    /**
     * Gets the time interval with which the payload cache of the client should
     * be updated.
     *
     * 只读缓存更新频率，单位为毫秒。
     * 只读缓存定时更新任务只更新读取过请求，因此虽然永不过期，也会存在读取不到的情况。
     *
     * @return time in milliseconds.
     */
    long getResponseCacheUpdateIntervalMs();

    /**
     * The {@link com.netflix.eureka.registry.ResponseCache} currently uses a two level caching
     * strategy to responses. A readWrite cache with an expiration policy, and a readonly cache
     * that caches without expiry.
     *
     * 是否使用只读请求响应缓存，默认为true
     * 响应缓存 ( ResponseCache ) 机制目前使用两层缓存策略。
     * 优先读取永不过期的只读缓存，读取不到后读取固定过期的读写缓存。
     *
     * @return true if the read only cache is to be used
     */
    boolean shouldUseReadOnlyResponseCache();

    /**
     * Checks to see if the delta information can be served to client or not.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * 是否可以将增量信息提供给客户端，默认为false
     *
     * @return true if the delta information is allowed to be served, false
     *         otherwise.
     */
    boolean shouldDisableDelta();

    /**
     * Get the idle time for which the status replication threads can stay
     * alive.
     *
     * 获取状态复制线程可以保持存活的空闲时间，单位为分钟，默认为10分钟
     *
     * @return time in minutes.
     */
    long getMaxIdleThreadInMinutesAgeForStatusReplication();

    /**
     * Get the minimum number of threads to be used for status replication.
     *
     * 获取状态复制的最小线程数，默认为1
     *
     * @return minimum number of threads to be used for status replication.
     */
    int getMinThreadsForStatusReplication();

    /**
     * Get the maximum number of threads to be used for status replication.
     *
     * 获取状态复制的最大线程数，默认为1
     *
     * @return maximum number of threads to be used for status replication.
     */
    int getMaxThreadsForStatusReplication();

    /**
     * Get the maximum number of replication events that can be allowed to back
     * up in the status replication pool.
     * <p>
     * Depending on the memory allowed, timeout and the replication traffic,
     * this value can vary.
     * </p>
     *
     * 获取可以在状态复制池中备份的复制事件的最大数量，默认为10000
     *
     * @return the maximum number of replication events that can be allowed to
     *         back up.
     */
    int getMaxElementsInStatusReplicationPool();

    /**
     * Checks whether to synchronize instances when timestamp differs.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * 否同步应用实例信息，当应用实例信息最后更新时间戳( lastDirtyTimestamp )发生改变。默认为true
     *
     * @return true, to synchronize, false otherwise.
     */
    boolean shouldSyncWhenTimestampDiffers();

    /**
     * Get the number of times that a eureka node would try to get the registry
     * information from the peers during startup.
     *
     * 获取eureka服务器启动时，从远程 Eureka-Server 读取失败重试次数。默认为5
     *
     * @return the number of retries
     */
    int getRegistrySyncRetries();

    /**
     * Get the wait/sleep time between each retry sync attempts, if the prev retry failed and there are
     * more retries to attempt.
     *
     * Eureka-Server 启动时，从远程 Eureka-Server 读取注册信息失败，再次尝试获取的等待( sleep )间隔，单位为毫秒，默认为30 * 1000 ms
     *
     * @return the wait time in ms between each sync retries
     */
    long getRegistrySyncRetryWaitMs();

    /**
     * Get the maximum number of replication events that can be allowed to back
     * up in the replication pool. This replication pool is responsible for all
     * events except status updates.
     * <p>
     * Depending on the memory allowed, timeout and the replication traffic,
     * this value can vary.
     * </p>
     *
     * 获取可以允许在复制池中备份的复制事件的最大数量， 该复制池负责除状态更新以外的所有事件，默认为10000
     *
     * @return the maximum number of replication events that can be allowed to
     *         back up.
     */
    int getMaxElementsInPeerReplicationPool();

    /**
     * Get the idle time for which the replication threads can stay alive.
     *
     * 获取复制线程可以保持活动的空闲时间，单位为分钟，默认为15分钟
     *
     * @return time in minutes.
     */
    long getMaxIdleThreadAgeInMinutesForPeerReplication();

    /**
     * Get the minimum number of threads to be used for replication.
     *
     * 获取最小线程数（进行复制操作），默认为5
     *
     * @return minimum number of threads to be used for replication.
     */
    int getMinThreadsForPeerReplication();

    /**
     * Get the maximum number of threads to be used for replication.
     *
     * 获取最大线程数（进行复制操作），默认为20
     *
     * @return maximum number of threads to be used for replication.
     */
    int getMaxThreadsForPeerReplication();

    /**
     * Get the minimum number of available peer replication instances
     * for this instance to be considered healthy. The design of eureka allows
     * for an instance to continue operating with zero peers, but that would not
     * be ideal.
     * <p>
     * The default value of -1 is interpreted as a marker to not compare
     * the number of replicas. This would be done to either disable this check
     * or to run eureka in a single node configuration.
     *
     * 获取可用集群复制实例的最小数量，默认值为-1
     *
     * @return minimum number of available peer replication instances
     *         for this instance to be considered healthy.
     */
    int getHealthStatusMinNumberOfAvailablePeers();

    /**
     * Get the time in milliseconds to try to replicate before dropping
     * replication events.
     *
     * 获取在删除复制事件之前进行复制的时间，单位为毫秒，默认为30000ms
     *
     * @return time in milliseconds
     */
    int getMaxTimeForReplication();

    /**
     * Checks whether the connections to replicas should be primed. In AWS, the
     * firewall requires sometime to establish network connection for new nodes.
     *
     * AWS相关
     *
     * @return true, if connections should be primed, false otherwise.
     */
    boolean shouldPrimeAwsReplicaConnections();

    /**
     * Checks to see if the delta information can be served to client or not for
     * remote regions.
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * 是否将增量信息提供给客户端或远程区域，默认为false
     *
     * @return true if the delta information is allowed to be served, false
     *         otherwise.
     */
    boolean shouldDisableDeltaForRemoteRegions();

    /**
     * Gets the timeout value for connecting to peer eureka nodes for remote
     * regions.
     *
     * 获取连接到远程Eureka-Server的超时时间，单位为毫秒，默认为2000ms
     *
     * @return timeout value in milliseconds.
     */
    int getRemoteRegionConnectTimeoutMs();

    /**
     * Gets the timeout value for reading information from peer eureka nodes for
     * remote regions.
     *
     * 获取读取远程Eureka-Server的超时时间，单位为毫秒，默认为5000ms
     *
     * @return timeout value in milliseconds.
     */
    int getRemoteRegionReadTimeoutMs();

    /**
     * Gets the total number of <em>HTTP</em> connections allowed to peer eureka
     * nodes for remote regions.
     *
     * 获取远程Eureka-Server的Http连接总数，默认为1000
     *
     * @return total number of allowed <em>HTTP</em> connections.
     */

    int getRemoteRegionTotalConnections();

    /**
     * Gets the total number of <em>HTTP</em> connections allowed to a
     * particular peer eureka node for remote regions.
     *
     * 获取单个远程Eureka-Server的Http连接总数，默认为500
     *
     * @return total number of allowed <em>HTTP</em> connections for a peer
     *         node.
     */
    int getRemoteRegionTotalConnectionsPerHost();

    /**
     * Gets the idle time after which the <em>HTTP</em> connection should be
     * cleaned up for remote regions.
     *
     * 获取远程Eureka-Server请求空闲超时时间，单位为秒，默认为30s
     *
     * @return idle time in seconds.
     */
    int getRemoteRegionConnectionIdleTimeoutSeconds();

    /**
     * Indicates whether the content fetched from eureka server has to be
     * compressed for remote regions whenever it is supported by the server. The
     * registry information from the eureka server is compressed for optimum
     * network traffic.
     *
     * Eureka-Server中获取的内容是否在远程地区被压缩，默认为true
     *
     * @return true, if the content need to be compressed, false otherwise.
     */
    boolean shouldGZipContentFromRemoteRegion();

    /**
     * Get a map of region name against remote region discovery url.
     *
     * 获取远程Eureka-Server的地址，key为Eureka-server名，value为Eureka-Server地址
     *
     * @return - An unmodifiable map of remote region name against remote region discovery url. Empty map if no remote
     * region url is defined.
     */
    Map<String, String> getRemoteRegionUrlsWithName();

    /**
     * Get the list of remote region urls.
     * @return - array of string representing {@link java.net.URL}s.
     * @deprecated Use {@link #getRemoteRegionUrlsWithName()}
     */
    String[] getRemoteRegionUrls();

    /**
     * Returns a list of applications that must be retrieved from the passed remote region. <br/>
     * This list can be <code>null</code> which means that no filtering should be applied on the applications
     * for this region i.e. all applications must be returned. <br/>
     * A global whitelist can also be configured which can be used when no setting is available for a region, such a
     * whitelist can be obtained by passing <code>null</code> to this method.
     *
     * @param regionName Name of the region for which the application whitelist is to be retrieved. If null a global
     *                   setting is returned.
     *
     * 获取远程Eureka-Server获取注册的Applications集合
     *
     * @return A set of application names which must be retrieved from the passed region. If <code>null</code> all
     * applications must be retrieved.
     */
    @Nullable
    Set<String> getRemoteRegionAppWhitelist(@Nullable String regionName);

    /**
     * 获取从远程Eureka-Server获取注册信息的时间间隔，单位为秒，默认为30s
     *
     * Get the time interval for which the registry information need to be fetched from the remote region.
     * @return time in seconds.
     */
    int getRemoteRegionRegistryFetchInterval();

    /**
     * 获取从远程Eureka-Server获取注册信息的线程池大小，默认为20
     *
     * Size of a thread pool used to execute remote region registry fetch requests. Delegating these requests
     * to internal threads is necessary workaround to https://bugs.openjdk.java.net/browse/JDK-8049846 bug.
     */
    int getRemoteRegionFetchThreadPoolSize();

    /**
     * Gets the fully qualified trust store file that will be used for remote region registry fetches.
     *
     * 用来合格请求远程Eureka-Server注册表的信任存储文件，默认为空
     *
     * @return
     */
    String getRemoteRegionTrustStore();

    /**
     * Get the remote region trust store's password.
     *
     * 获取远程 Eureka-Server 信任存储文件的密码，默认为changeit
     *
     */
    String getRemoteRegionTrustStorePassword();

    /**
     * Old behavior of fallback to applications in the remote region (if configured) if there are no instances of that
     * application in the local region, will be disabled.
     *
     * 是否禁用本地读取不到注册信息，从远程 Eureka-Server 读取，默认为false
     *
     * @return {@code true} if the old behavior is to be disabled.
     */
    boolean disableTransparentFallbackToOtherRegion();

    /**
     * Indicates whether the replication between cluster nodes should be batched for network efficiency.
     *
     * 集群节点之间的复制是否为了网络效率而进行批处理，默认为false
     *
     * @return {@code true} if the replication needs to be batched.
     */
    boolean shouldBatchReplication();

    /**
     * Indicates whether the eureka server should log/metric clientAuthHeaders
     *
     * 打印访问的客户端名和版本号，配合 Netflix Servo 实现监控信息采集，默认为true
     *
     * @return {@code true} if the clientAuthHeaders should be logged and/or emitted as metrics
     */
    boolean shouldLogIdentityHeaders();

    /**
     * Indicates whether the rate limiter should be enabled or disabled.
     *
     * 是否开启请求限流，默认为false
     *
     */
    boolean isRateLimiterEnabled();

    /**
     * Indicate if rate limit standard clients. If set to false, only non standard clients
     * will be rate limited.
     *
     * 是否限制非标准客户端的访问，默认为false
     *
     */
    boolean isRateLimiterThrottleStandardClients();

    /**
     * A list of certified clients. This is in addition to standard eureka Java clients.
     *
     * 标准客户端名集合
     *
     * 标准客户端名集合。默认包含"DefaultClient" 和 "DefaultServer" 。
     */
    Set<String> getRateLimiterPrivilegedClients();

    /**
     * Rate limiter, token bucket algorithm property. See also {@link #getRateLimiterRegistryFetchAverageRate()}
     * and {@link #getRateLimiterFullFetchAverageRate()}.
     *
     * 速率限制的burst size，默认为10。令牌桶算法
     */
    int getRateLimiterBurstSize();

    /**
     * Rate limiter, token bucket algorithm property. Specifies the average enforced request rate.
     * See also {@link #getRateLimiterBurstSize()}.
     *
     * 指定增量获取注册信息的平均执行注册请求速率，默认为500
     */
    int getRateLimiterRegistryFetchAverageRate();

    /**
     * Rate limiter, token bucket algorithm property. Specifies the average enforced request rate.
     * See also {@link #getRateLimiterBurstSize()}.
     *
     * 指定全量获取注册信息的平均执行注册请求速率，默认为100
     */
    int getRateLimiterFullFetchAverageRate();

    /**
     * Name of the Role used to describe auto scaling groups from third AWS accounts.
     *
     * AWS相关
     */
    String getListAutoScalingGroupsRoleName();

    /**
     * JSON编码器名称，默认为null
     *
     * @return the class name of the full json codec to use for the server. If none set a default codec will be used
     */
    String getJsonCodecName();

    /**
     * XML编码器名称，默认为null
     *
     * @return the class name of the full xml codec to use for the server. If none set a default codec will be used
     */
    String getXmlCodecName();

    /**
     * Get the configured binding strategy EIP or Route53.
     *
     * AWS相关
     *
     * @return the configured binding strategy
     */
    AwsBindingStrategy getBindingStrategy();

    /**
     *
     * AWS相关
     *
     * @return the ttl used to set up the route53 domain if new
     */
    long getRoute53DomainTTL();

    /**
     * Gets the number of times the server should try to bind to the candidate
     * Route53 domain.
     *
     * AWS相关
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return the number of times the server should try to bind to the
     *         candidate Route53 domain.
     */
    int getRoute53BindRebindRetries();

    /**
     * Gets the interval with which the server should check if the Route53 domain is bound
     * and should try to bind in the case if it is already not bound.
     *
     * AWS相关
     *
     * <p>
     * <em>The changes are effective at runtime.</em>
     * </p>
     *
     * @return the time in milliseconds.
     */
    int getRoute53BindingRetryIntervalMs();

    /**
     * To avoid configuration API pollution when trying new/experimental or features or for the migration process,
     * the corresponding configuration can be put into experimental configuration section.
     *
     * 实验属性，当尝试新功能迁移过程时，为了避免配置API污染，相应的配置即可投入实验配置部分，默认为null
     *
     * @return a property of experimental feature
     */
    String getExperimental(String name);
}
