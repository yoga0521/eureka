package com.netflix.eureka.cluster;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.endpoint.EndpointUtils;
import com.netflix.discovery.shared.Application;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.ServerCodecs;
import com.netflix.eureka.transport.JerseyReplicationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to manage lifecycle of a collection of {@link PeerEurekaNode}s.
 *
 * @author Tomasz Bak
 */
@Singleton
public class PeerEurekaNodes {

    private static final Logger logger = LoggerFactory.getLogger(PeerEurekaNodes.class);

    /**
     * 应用实例注册表
     */
    protected final PeerAwareInstanceRegistry registry;
    /**
     * Eureka-Server配置对象
     */
    protected final EurekaServerConfig serverConfig;
    /**
     * Eureka-Client配置对象
     */
    protected final EurekaClientConfig clientConfig;
    /**
     * Eureka-Server编解码器
     */
    protected final ServerCodecs serverCodecs;
    /**
     * 应用信息管理对象
     */
    private final ApplicationInfoManager applicationInfoManager;

    /**
     * Eureka-Server集群节点集合
     */
    private volatile List<PeerEurekaNode> peerEurekaNodes = Collections.emptyList();
    /**
     * Eureka-Server服务地址集合
     */
    private volatile Set<String> peerEurekaNodeUrls = Collections.emptySet();

    /**
     * 定时任务执行对象
     */
    private ScheduledExecutorService taskExecutor;

    @Inject
    public PeerEurekaNodes(
            PeerAwareInstanceRegistry registry,
            EurekaServerConfig serverConfig,
            EurekaClientConfig clientConfig,
            ServerCodecs serverCodecs,
            ApplicationInfoManager applicationInfoManager) {
        this.registry = registry;
        this.serverConfig = serverConfig;
        this.clientConfig = clientConfig;
        this.serverCodecs = serverCodecs;
        this.applicationInfoManager = applicationInfoManager;
    }

    public List<PeerEurekaNode> getPeerNodesView() {
        return Collections.unmodifiableList(peerEurekaNodes);
    }

    public List<PeerEurekaNode> getPeerEurekaNodes() {
        return peerEurekaNodes;
    }
    
    public int getMinNumberOfAvailablePeers() {
        return serverConfig.getHealthStatusMinNumberOfAvailablePeers();
    }

    public void start() {
        // 创建定时任务执行对象
        taskExecutor = Executors.newSingleThreadScheduledExecutor(
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread thread = new Thread(r, "Eureka-PeerNodesUpdater");
                        thread.setDaemon(true);
                        return thread;
                    }
                }
        );
        try {
            // 更新Eureka-Server节点和服务地址集合
            updatePeerEurekaNodes(resolvePeerUrls());
            // 创建定时更新Eureka-Server节点和服务地址集合的任务
            Runnable peersUpdateTask = new Runnable() {
                @Override
                public void run() {
                    try {
                        // 更新Eureka-Server节点和服务地址集合
                        updatePeerEurekaNodes(resolvePeerUrls());
                    } catch (Throwable e) {
                        logger.error("Cannot update the replica Nodes", e);
                    }

                }
            };
            taskExecutor.scheduleWithFixedDelay(
                    peersUpdateTask,
                    serverConfig.getPeerEurekaNodesUpdateIntervalMs(),
                    serverConfig.getPeerEurekaNodesUpdateIntervalMs(),
                    TimeUnit.MILLISECONDS
            );
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        // 打印服务地址
        for (PeerEurekaNode node : peerEurekaNodes) {
            logger.info("Replica node URL:  {}", node.getServiceUrl());
        }
    }

    public void shutdown() {
        taskExecutor.shutdown();
        List<PeerEurekaNode> toRemove = this.peerEurekaNodes;

        this.peerEurekaNodes = Collections.emptyList();
        this.peerEurekaNodeUrls = Collections.emptySet();

        for (PeerEurekaNode node : toRemove) {
            node.shutDown();
        }
    }

    /**
     * Resolve peer URLs.
     *
     * 获得 Eureka-Server 集群服务地址结合
     *
     * @return peer URLs with node's own URL filtered out
     */
    protected List<String> resolvePeerUrls() {
        // 获取实例信息
        InstanceInfo myInfo = applicationInfoManager.getInfo();
        // 获取可用区
        String zone = InstanceInfo.getZone(clientConfig.getAvailabilityZones(clientConfig.getRegion()), myInfo);
        // 获取服务地址
        List<String> replicaUrls = EndpointUtils
                .getDiscoveryServiceUrls(clientConfig, zone, new EndpointUtils.InstanceInfoBasedUrlRandomizer(myInfo));

        int idx = 0;
        while (idx < replicaUrls.size()) {
            // 判断是否是自己的服务地址，如果是，就移除
            if (isThisMyUrl(replicaUrls.get(idx))) {
                replicaUrls.remove(idx);
            } else {
                idx++;
            }
        }
        return replicaUrls;
    }

    /**
     * Given new set of replica URLs, destroy {@link PeerEurekaNode}s no longer available, and
     * create new ones.
     *
     * @param newPeerUrls peer node URLs; this collection should have local node's URL filtered out
     */
    protected void updatePeerEurekaNodes(List<String> newPeerUrls) {
        if (newPeerUrls.isEmpty()) {
            logger.warn("The replica size seems to be empty. Check the route 53 DNS Registry");
            return;
        }

        // 获取需要关闭的Eureka-Server服务地址
        Set<String> toShutdown = new HashSet<>(peerEurekaNodeUrls);
        toShutdown.removeAll(newPeerUrls);
        // 获取需要添加的Eureka-Server服务地址
        Set<String> toAdd = new HashSet<>(newPeerUrls);
        toAdd.removeAll(peerEurekaNodeUrls);

        // 如果需要关闭和需要添加的服务地址都为空
        if (toShutdown.isEmpty() && toAdd.isEmpty()) { // No change
            return;
        }

        // Remove peers no long available
        List<PeerEurekaNode> newNodeList = new ArrayList<>(peerEurekaNodes);

        if (!toShutdown.isEmpty()) {
            logger.info("Removing no longer available peer nodes {}", toShutdown);
            int i = 0;
            // 循环判断Eureka-Server节点中是否有需要关闭的节点，有就关闭
            while (i < newNodeList.size()) {
                PeerEurekaNode eurekaNode = newNodeList.get(i);
                if (toShutdown.contains(eurekaNode.getServiceUrl())) {
                    newNodeList.remove(i);
                    eurekaNode.shutDown();
                } else {
                    i++;
                }
            }
        }

        // Add new peers
        if (!toAdd.isEmpty()) {
            logger.info("Adding new peer nodes {}", toAdd);
            // 添加新的Eureka-Server节点
            for (String peerUrl : toAdd) {
                newNodeList.add(createPeerEurekaNode(peerUrl));
            }
        }

        this.peerEurekaNodes = newNodeList;
        this.peerEurekaNodeUrls = new HashSet<>(newPeerUrls);
    }

    protected PeerEurekaNode createPeerEurekaNode(String peerEurekaNodeUrl) {
        // 创建Eureka-Server集群通信客户端
        HttpReplicationClient replicationClient = JerseyReplicationClient.createReplicationClient(serverConfig, serverCodecs, peerEurekaNodeUrl);
        String targetHost = hostFromUrl(peerEurekaNodeUrl);
        if (targetHost == null) {
            targetHost = "host";
        }
        // 创建 PeerEurekaNode
        return new PeerEurekaNode(registry, targetHost, peerEurekaNodeUrl, replicationClient, serverConfig);
    }

    /**
     * @deprecated 2016-06-27 use instance version of {@link #isThisMyUrl(String)}
     *
     * Checks if the given service url contains the current host which is trying
     * to replicate. Only after the EIP binding is done the host has a chance to
     * identify itself in the list of replica nodes and needs to take itself out
     * of replication traffic.
     *
     * @param url the service url of the replica node that the check is made.
     * @return true, if the url represents the current node which is trying to
     *         replicate, false otherwise.
     */
    public static boolean isThisMe(String url) {
        InstanceInfo myInfo = ApplicationInfoManager.getInstance().getInfo();
        String hostName = hostFromUrl(url);
        return hostName != null && hostName.equals(myInfo.getHostName());
    }

    /**
     * Checks if the given service url contains the current host which is trying
     * to replicate. Only after the EIP binding is done the host has a chance to
     * identify itself in the list of replica nodes and needs to take itself out
     * of replication traffic.
     *
     * @param url the service url of the replica node that the check is made.
     * @return true, if the url represents the current node which is trying to
     *         replicate, false otherwise.
     */
    public boolean isThisMyUrl(String url) {
        return isInstanceURL(url, applicationInfoManager.getInfo());
    }
    
    /**
     * Checks if the given service url matches the supplied instance
     *
     * @param url the service url of the replica node that the check is made.
     * @param instance the instance to check the service url against
     * @return true, if the url represents the supplied instance, false otherwise.
     */
    public boolean isInstanceURL(String url, InstanceInfo instance) {
        String hostName = hostFromUrl(url);
        String myInfoComparator = instance.getHostName();
        if (clientConfig.getTransportConfig().applicationsResolverUseIp()) {
            myInfoComparator = instance.getIPAddr();
        }
        return hostName != null && hostName.equals(myInfoComparator);
    }

    public static String hostFromUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            logger.warn("Cannot parse service URI {}", url, e);
            return null;
        }
        return uri.getHost();
    }
}
