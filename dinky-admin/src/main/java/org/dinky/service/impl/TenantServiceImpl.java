/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.dinky.service.impl;

import org.dinky.assertion.Asserts;
import org.dinky.common.result.Result;
import org.dinky.context.TenantContextHolder;
import org.dinky.db.service.impl.SuperServiceImpl;
import org.dinky.mapper.TenantMapper;
import org.dinky.model.Namespace;
import org.dinky.model.Role;
import org.dinky.model.Tenant;
import org.dinky.model.UserTenant;
import org.dinky.service.NamespaceService;
import org.dinky.service.RoleService;
import org.dinky.service.TenantService;
import org.dinky.service.UserTenantService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl extends SuperServiceImpl<TenantMapper, Tenant>
        implements TenantService {

    @Resource @Lazy private RoleService roleService;

    @Resource @Lazy private NamespaceService namespaceService;

    private final UserTenantService userTenantService;

    @Override
    public Result<Void> saveOrUpdateTenant(Tenant tenant) {
        Integer tenantId = tenant.getId();
        if (Asserts.isNull(tenantId)) {
            Tenant tenantByTenantCode = getTenantByTenantCode(tenant.getTenantCode());
            if (Asserts.isNotNull(tenantByTenantCode)) {
                return Result.failed("该租户已存在");
            }
            tenant.setIsDelete(false);
            if (save(tenant)) {
                TenantContextHolder.set(tenant.getId());
                return Result.succeed("新增成功");
            }
            return Result.failed("新增失败");
        } else {
            if (modifyTenant(tenant)) {
                return Result.failed("修改成功");
            }
            return Result.failed("新增失败");
        }
    }

    @Override
    public Tenant getTenantByTenantCode(String tenantCode) {
        return getOne(new QueryWrapper<Tenant>().eq("tenant_code", tenantCode).eq("is_delete", 0));
    }

    @Override
    public boolean modifyTenant(Tenant tenant) {
        if (Asserts.isNull(tenant.getId())) {
            return false;
        }
        return updateById(tenant);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<Void> deleteTenantById(JsonNode para) {
        for (JsonNode item : para) {
            Integer id = item.asInt();
            Tenant tenant = getById(id);
            if (Asserts.isNull(tenant)) {
                return Result.failed("租户不存在");
            }

            Long tenantRoleCount =
                    roleService
                            .getBaseMapper()
                            .selectCount(new QueryWrapper<Role>().eq("tenant_id", id));
            if (tenantRoleCount > 0) {
                return Result.failed("删除租户失败，该租户已绑定角色");
            }

            Long tenantNamespaceCount =
                    namespaceService
                            .getBaseMapper()
                            .selectCount(new QueryWrapper<Namespace>().eq("tenant_id", id));
            if (tenantNamespaceCount > 0) {
                return Result.failed("删除租户失败，该租户已绑定名称空间");
            }
            tenant.setIsDelete(true);
            boolean result = updateById(tenant);
            if (result) {
                return Result.succeed("删除成功");
            } else {
                return Result.failed("删除失败");
            }
        }
        return Result.failed("删除租户不存在");
    }

    @Override
    public List<Tenant> getTenantByIds(Set<Integer> tenantIds) {
        return baseMapper.getTenantByIds(tenantIds);
    }

    /**
     * Assign users to specified tenants
     *
     * @param para
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> distributeUsers(JsonNode para) {
        if (para.size() > 0) {
            List<UserTenant> tenantUserList = new ArrayList<>();
            Integer tenantId = para.get("tenantId").asInt();
            userTenantService.remove(new QueryWrapper<UserTenant>().eq("tenant_id", tenantId));
            JsonNode tenantUserJsonNode = para.get("users");
            for (JsonNode ids : tenantUserJsonNode) {
                UserTenant userTenant = new UserTenant();
                userTenant.setTenantId(tenantId);
                userTenant.setUserId(ids.asInt());
                tenantUserList.add(userTenant);
            }
            // save or update user role

            boolean result = userTenantService.saveOrUpdateBatch(tenantUserList, 1000);
            if (result) {
                return Result.succeed("分配用户成功");
            } else {
                if (tenantUserList.size() == 0) {
                    return Result.succeed("该租户下的用户已被全部删除");
                }
                return Result.failed("分配用户失败");
            }
        } else {
            return Result.failed("请选择要分配的用户");
        }
    }

    @Override
    public Result<Void> switchTenant(JsonNode para) {
        if (para.size() > 0) {
            Integer tenantId = para.get("tenantId").asInt();
            TenantContextHolder.clear();
            TenantContextHolder.set(tenantId);
            return Result.succeed("切换租户成功");
        } else {
            return Result.failed("无法切换租户,获取不到租户信息");
        }
    }
}
