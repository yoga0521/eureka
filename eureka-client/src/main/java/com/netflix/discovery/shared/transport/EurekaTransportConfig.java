package com.netflix.discovery.shared.transport;

/**
 * Config class that governs configurations relevant to the transport layer
 *
 * @author David Liu
 */
public interface EurekaTransportConfig {

    /**
     * Eureka-Client 会话重新连接的时间间隔，单位为秒，默认为20*60s
     *
     * @return the reconnect inverval to use for sessioned clients
     */
    int getSessionedClientReconnectIntervalSeconds();

    /**
     * 请求失败的Eureka-Client隔离集合占Eureka-Client总数的占比，超过该比例会进行清空，默认为0.66
     *
     * @return the percentage of the full endpoints set above which the quarantine set is cleared in the range [0, 1.0]
     */
    double getRetryableClientQuarantineRefreshPercentage();

    /**
     * 获取应用解析器数据过时时间间隔，默认为5*60s
     *
     * @return the max staleness threshold tolerated by the applications resolver
     */
    int getApplicationsResolverDataStalenessThresholdSeconds();

    /**
     * By default, the applications resolver extracts the public hostname from internal InstanceInfos for resolutions.
     * Set this to true to change this behaviour to use ip addresses instead (private ip if ip type can be determined).
     *
     * 应用解析器是否改用ip，默认情况下是从instance获取公共主机名，默认为false
     *
     * @return false by default
     */
    boolean applicationsResolverUseIp();

    /**
     * 异步解析 EndPoint 集群频率，单位为毫秒。默认为5*60*1000ms
     *
     * @return the interval to poll for the async resolver.
     */
    int getAsyncResolverRefreshIntervalMs();

    /**
     * 异步解析器预热解析 EndPoint 集群超时时间，单位为毫秒。默认为5000s
     *
     * @return the async refresh timeout threshold in ms.
     */
    int getAsyncResolverWarmUpTimeoutMs();

    /**
     * 异步线程池大小，默认为5
     *
     * @return the max threadpool size for the async resolver's executor
     */
    int getAsyncExecutorThreadPoolSize();

    /**
     * The remote vipAddress of the primary eureka cluster to register with.
     *
     * 获取要写入到eureka集群的远程vip，默认为null
     *
     * @return the vipAddress for the write cluster to register with
     */
    String getWriteClusterVip();

    /**
     * The remote vipAddress of the eureka cluster (either the primaries or a readonly replica) to fetch registry
     * data from.
     *
     * 获取eureka集群的远程vip地址，默认为null
     *
     * @return the vipAddress for the readonly cluster to redirect to, if applicable (can be the same as the bootstrap)
     */
    String getReadClusterVip();

    /**
     * Can be used to specify different bootstrap resolve strategies. Current supported strategies are:
     *  - default (if no match): bootstrap from dns txt records or static config hostnames
     *  - composite: bootstrap from local registry if data is available
     *    and warm (see {@link #getApplicationsResolverDataStalenessThresholdSeconds()}, otherwise
     *    fall back to a backing default
     *
     * 获取bootstrap解析器的策略，默认为null
     *
     * @return null for the default strategy, by default
     */
    String getBootstrapResolverStrategy();

    /**
     * By default, the transport uses the same (bootstrap) resolver for queries.
     *
     * Set this property to false to use an indirect resolver to resolve query targets
     * via {@link #getReadClusterVip()}. This indirect resolver may or may not return the same
     * targets as the bootstrap servers depending on how servers are setup.
     *
     * 使用bootstrap解析器进行查询，默认为true
     *
     * @return true by default.
     */
    boolean useBootstrapResolverForQuery();
}
