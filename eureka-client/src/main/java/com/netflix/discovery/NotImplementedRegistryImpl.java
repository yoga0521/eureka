package com.netflix.discovery;

import javax.inject.Singleton;

import com.netflix.discovery.shared.Applications;

/**
 * @author Nitesh Kant
 */
@Singleton
public class NotImplementedRegistryImpl implements BackupRegistry {

    /**
     * 获取应用注册信息
     *
     * @return 应用列表对象 （默认为null）
     */
    @Override
    public Applications fetchRegistry() {
        return null;
    }

    /**
     * 获取应用注册信息
     *
     * @param includeRemoteRegions 远程区域列表
     * @return 应用列表对象（默认为null）
     */
    @Override
    public Applications fetchRegistry(String[] includeRemoteRegions) {
        return null;
    }
}
