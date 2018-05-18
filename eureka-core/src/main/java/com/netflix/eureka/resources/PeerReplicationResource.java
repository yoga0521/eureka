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

package com.netflix.eureka.resources;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerContextHolder;
import com.netflix.eureka.cluster.protocol.ReplicationInstance;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse;
import com.netflix.eureka.cluster.protocol.ReplicationInstanceResponse.Builder;
import com.netflix.eureka.cluster.protocol.ReplicationList;
import com.netflix.eureka.cluster.protocol.ReplicationListResponse;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <em>jersey</em> resource that handles requests for replication purposes.
 *
 * @author Karthik Ranganathan
 *
 */
@Path("/{version}/peerreplication")
@Produces({"application/xml", "application/json"})
public class PeerReplicationResource {

    private static final Logger logger = LoggerFactory.getLogger(PeerReplicationResource.class);

    private static final String REPLICATION = "true";

    private final EurekaServerConfig serverConfig;
    private final PeerAwareInstanceRegistry registry;

    @Inject
    PeerReplicationResource(EurekaServerContext server) {
        this.serverConfig = server.getServerConfig();
        this.registry = server.getRegistry();
    }

    public PeerReplicationResource() {
        this(EurekaServerContextHolder.getInstance().getServerContext());
    }

    /**
     * Process batched replication events from peer eureka nodes.
     *
     * <p>
     *  The batched events are delegated to underlying resources to generate a
     *  {@link ReplicationListResponse} containing the individual responses to the batched events
     * </p>
     *
     * @param replicationList
     *            The List of replication events from peer eureka nodes
     * @return A batched response containing the information about the responses of individual events
     */
    @Path("batch")
    @POST
    public Response batchReplication(ReplicationList replicationList) {
        try {
            ReplicationListResponse batchResponse = new ReplicationListResponse();
            // 逐个同步操作任务处理，并将处理结果ReplicationInstanceResponse合并到ReplicationListResponse
            for (ReplicationInstance instanceInfo : replicationList.getReplicationList()) {
                try {
                    // 调用dispatch执行同步操作，并把结果添加到ReplicationListResponse
                    batchResponse.addResponse(dispatch(instanceInfo));
                } catch (Exception e) {
                    batchResponse.addResponse(new ReplicationInstanceResponse(Status.INTERNAL_SERVER_ERROR.getStatusCode(), null));
                    logger.error("{} request processing failed for batch item {}/{}",
                            instanceInfo.getAction(), instanceInfo.getAppName(), instanceInfo.getId(), e);
                }
            }
            return Response.ok(batchResponse).build();
        } catch (Throwable e) {
            logger.error("Cannot execute batch Request", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ReplicationInstanceResponse dispatch(ReplicationInstance instanceInfo) {
        // 创建应用Resource(Controller)
        ApplicationResource applicationResource = createApplicationResource(instanceInfo);
        // 创建实例信息Resource(Controller)
        InstanceResource resource = createInstanceResource(instanceInfo, applicationResource);

        // 获取最近实例信息更新时间戳
        String lastDirtyTimestamp = toString(instanceInfo.getLastDirtyTimestamp());
        // 获取实例的覆盖状态
        String overriddenStatus = toString(instanceInfo.getOverriddenStatus());
        // 获取实例状态
        String instanceStatus = toString(instanceInfo.getStatus());

        Builder singleResponseBuilder = new Builder();
        switch (instanceInfo.getAction()) {
            case Register:
                singleResponseBuilder = handleRegister(instanceInfo, applicationResource);
                break;
            case Heartbeat:
                singleResponseBuilder = handleHeartbeat(serverConfig, resource, lastDirtyTimestamp, overriddenStatus, instanceStatus);
                break;
            case Cancel:
                singleResponseBuilder = handleCancel(resource);
                break;
            case StatusUpdate:
                singleResponseBuilder = handleStatusUpdate(instanceInfo, resource);
                break;
            case DeleteStatusOverride:
                singleResponseBuilder = handleDeleteStatusOverride(instanceInfo, resource);
                break;
        }
        return singleResponseBuilder.build();
    }

    /* Visible for testing */ ApplicationResource createApplicationResource(ReplicationInstance instanceInfo) {
        return new ApplicationResource(instanceInfo.getAppName(), serverConfig, registry);
    }

    /* Visible for testing */ InstanceResource createInstanceResource(ReplicationInstance instanceInfo,
                                                                      ApplicationResource applicationResource) {
        return new InstanceResource(applicationResource, instanceInfo.getId(), serverConfig, registry);
    }

    private static Builder handleRegister(ReplicationInstance instanceInfo, ApplicationResource applicationResource) {
        // 调用ApplicationResource的注册方法
        applicationResource.addInstance(instanceInfo.getInstanceInfo(), REPLICATION);
        // 返回结果
        return new Builder().setStatusCode(Status.OK.getStatusCode());
    }

    private static Builder handleCancel(InstanceResource resource) {
        // 调用InstanceResource的下线方法
        Response response = resource.cancelLease(REPLICATION);
        // 返回结果
        return new Builder().setStatusCode(response.getStatus());
    }

    private static Builder handleHeartbeat(EurekaServerConfig config, InstanceResource resource, String lastDirtyTimestamp, String overriddenStatus, String instanceStatus) {
        // 调用InstanceResource的续约方法
        Response response = resource.renewLease(REPLICATION, overriddenStatus, instanceStatus, lastDirtyTimestamp);
        // 处理返回结果
        int responseStatus = response.getStatus();
        Builder responseBuilder = new Builder().setStatusCode(responseStatus);

        if ("false".equals(config.getExperimental("bugfix.934"))) {
            if (responseStatus == Status.OK.getStatusCode() && response.getEntity() != null) {
                responseBuilder.setResponseEntity((InstanceInfo) response.getEntity());
            }
        } else {
            if ((responseStatus == Status.OK.getStatusCode() || responseStatus == Status.CONFLICT.getStatusCode())
                    && response.getEntity() != null) {
                responseBuilder.setResponseEntity((InstanceInfo) response.getEntity());
            }
        }
        return responseBuilder;
    }

    private static Builder handleStatusUpdate(ReplicationInstance instanceInfo, InstanceResource resource) {
        // 调用InstanceResource的状态更新方法
        Response response = resource.statusUpdate(instanceInfo.getStatus(), REPLICATION, toString(instanceInfo.getLastDirtyTimestamp()));
        // 返回结果
        return new Builder().setStatusCode(response.getStatus());
    }

    private static Builder handleDeleteStatusOverride(ReplicationInstance instanceInfo, InstanceResource resource) {
        // 调用InstanceResource的移除实例覆盖状态方法
        Response response = resource.deleteStatusUpdate(REPLICATION, instanceInfo.getStatus(),
                instanceInfo.getLastDirtyTimestamp().toString());
        // 返回结果
        return new Builder().setStatusCode(response.getStatus());
    }

    private static <T> String toString(T value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }
}
