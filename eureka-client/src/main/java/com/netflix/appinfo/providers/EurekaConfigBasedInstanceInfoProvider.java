package com.netflix.appinfo.providers;

import javax.inject.Singleton;
import javax.inject.Provider;
import java.util.Map;

import com.google.inject.Inject;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.InstanceInfo.PortType;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.RefreshableInstanceConfig;
import com.netflix.appinfo.UniqueIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InstanceInfo provider that constructs the InstanceInfo this this instance using
 * EurekaInstanceConfig.
 *
 * This provider is @Singleton scope as it provides the InstanceInfo for both DiscoveryClient
 * and ApplicationInfoManager, and need to provide the same InstanceInfo to both.
 *
 * @author elandau
 *
 */
@Singleton
public class EurekaConfigBasedInstanceInfoProvider implements Provider<InstanceInfo> {
    private static final Logger LOG = LoggerFactory.getLogger(EurekaConfigBasedInstanceInfoProvider.class);

    private final EurekaInstanceConfig config;

    private InstanceInfo instanceInfo;

    @Inject(optional = true)
    private VipAddressResolver vipAddressResolver = null;

    /**
     * 设置EurekaInstanceConfig
     *
     * @param config EurekaInstanceConfig对象
     */
    @Inject
    public EurekaConfigBasedInstanceInfoProvider(EurekaInstanceConfig config) {
        this.config = config;
    }

    /**
     * 获取InstanceInfo
     *
     * @return InstanceInfo
     */
    @Override
    public synchronized InstanceInfo get() {
        if (instanceInfo == null) {
            // Build the lease information to be passed to the server based on config
            // 创建租约信息生成器，设置租约续约时间间隔和租约过期时间
            LeaseInfo.Builder leaseInfoBuilder = LeaseInfo.Builder.newBuilder()
                    .setRenewalIntervalInSecs(config.getLeaseRenewalIntervalInSeconds())
                    .setDurationInSecs(config.getLeaseExpirationDurationInSeconds());

            // 创建vip（Virtual IP Address虚拟IP地址）地址解析器
            if (vipAddressResolver == null) {
                vipAddressResolver = new Archaius1VipAddressResolver();
            }

            // Builder the instance information to be registered with eureka server
            // 创建实例信息生成器，设置vip地址解析器
            InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder(vipAddressResolver);

            // set the appropriate id for the InstanceInfo, falling back to datacenter Id if applicable, else hostname
            // 设置实例id
            String instanceId = config.getInstanceId();
            // 如果EurekaInstanceConfig的实例id为空
            if (instanceId == null || instanceId.isEmpty()) {
                DataCenterInfo dataCenterInfo = config.getDataCenterInfo();
                // 判断数据中心是否是唯一标识的
                if (dataCenterInfo instanceof UniqueIdentifier) {
                    // 将数据中心id设置为实例id
                    instanceId = ((UniqueIdentifier) dataCenterInfo).getId();
                } else {
                    // 将主机名设置为实例id
                    instanceId = config.getHostName(false);
                }
            }

            // 默认地址
            String defaultAddress;
            if (config instanceof RefreshableInstanceConfig) {
                // Refresh AWS data center info, and return up to date address
                // AWS相关
                defaultAddress = ((RefreshableInstanceConfig) config).resolveDefaultAddress(false);
            } else {
                // 获取主机名
                defaultAddress = config.getHostName(false);
            }

            // fail safe
            // 如果默认地址为空
            if (defaultAddress == null || defaultAddress.isEmpty()) {
                // 将EurekaInstanceConfig的IP地址设置为默认地址
                defaultAddress = config.getIpAddress();
            }

            //设置生成器属性
            builder.setNamespace(config.getNamespace())
                    .setInstanceId(instanceId)
                    .setAppName(config.getAppname())
                    .setAppGroupName(config.getAppGroupName())
                    .setDataCenterInfo(config.getDataCenterInfo())
                    .setIPAddr(config.getIpAddress())
                    .setHostName(defaultAddress)
                    .setPort(config.getNonSecurePort())
                    .enablePort(PortType.UNSECURE, config.isNonSecurePortEnabled())
                    .setSecurePort(config.getSecurePort())
                    .enablePort(PortType.SECURE, config.getSecurePortEnabled())
                    .setVIPAddress(config.getVirtualHostName())
                    .setSecureVIPAddress(config.getSecureVirtualHostName())
                    .setHomePageUrl(config.getHomePageUrlPath(), config.getHomePageUrl())
                    .setStatusPageUrl(config.getStatusPageUrlPath(), config.getStatusPageUrl())
                    .setASGName(config.getASGName())
                    .setHealthCheckUrls(config.getHealthCheckUrlPath(),
                            config.getHealthCheckUrl(), config.getSecureHealthCheckUrl());


            // Start off with the STARTING state to avoid traffic
            // 判断实例是否初始化就启动
            if (!config.isInstanceEnabledOnit()) {
                // 设置实例状态为STARTING状态
                InstanceStatus initialStatus = InstanceStatus.STARTING;
                LOG.info("Setting initial instance status as: {}", initialStatus);
                builder.setStatus(initialStatus);
            } else {
                // 实例状态为UP状态（默认）
                LOG.info("Setting initial instance status as: {}. This may be too early for the instance to advertise "
                         + "itself as available. You would instead want to control this via a healthcheck handler.",
                         InstanceStatus.UP);
            }

            // Add any user-specific metadata information
            // 设置元数据
            for (Map.Entry<String, String> mapEntry : config.getMetadataMap().entrySet()) {
                String key = mapEntry.getKey();
                String value = mapEntry.getValue();
                builder.add(key, value);
            }

            // 创建实例对象
            instanceInfo = builder.build();
            // 设置实例的租约信息
            instanceInfo.setLeaseInfo(leaseInfoBuilder.build());
        }
        return instanceInfo;
    }

}
