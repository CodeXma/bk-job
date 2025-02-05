/*
 * Tencent is pleased to support the open source community by making BK-JOB蓝鲸智云作业平台 available.
 *
 * Copyright (C) 2021 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-JOB蓝鲸智云作业平台 is licensed under the MIT License.
 *
 * License for BK-JOB蓝鲸智云作业平台:
 * --------------------------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 * CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.tencent.bk.job.manage.dao.whiteip.impl;

import com.tencent.bk.job.manage.dao.whiteip.WhiteIPActionScopeDAO;
import com.tencent.bk.job.manage.model.dto.whiteip.WhiteIPActionScopeDTO;
import lombok.val;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.generated.tables.WhiteIpActionScope;
import org.jooq.types.ULong;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class WhiteIPActionScopeDAOImpl implements WhiteIPActionScopeDAO {
    private static final WhiteIpActionScope T_WHITE_IP_ACTION_SCOPE = WhiteIpActionScope.WHITE_IP_ACTION_SCOPE;

    @Override
    public Long insertWhiteIPActionScope(DSLContext dslContext, WhiteIPActionScopeDTO whiteIPActionScopeDTO) {
        Record record = dslContext.insertInto(T_WHITE_IP_ACTION_SCOPE,
            T_WHITE_IP_ACTION_SCOPE.RECORD_ID,
            T_WHITE_IP_ACTION_SCOPE.ACTION_SCOPE_ID,
            T_WHITE_IP_ACTION_SCOPE.CREATOR,
            T_WHITE_IP_ACTION_SCOPE.CREATE_TIME,
            T_WHITE_IP_ACTION_SCOPE.LAST_MODIFY_USER,
            T_WHITE_IP_ACTION_SCOPE.LAST_MODIFY_TIME
        ).values(
            whiteIPActionScopeDTO.getRecordId(),
            whiteIPActionScopeDTO.getActionScopeId(),
            whiteIPActionScopeDTO.getCreator(),
            ULong.valueOf(whiteIPActionScopeDTO.getCreateTime()),
            whiteIPActionScopeDTO.getLastModifier(),
            ULong.valueOf(whiteIPActionScopeDTO.getLastModifyTime())
        ).returning(T_WHITE_IP_ACTION_SCOPE.ID)
            .fetchOne();
        return record.get(T_WHITE_IP_ACTION_SCOPE.ID);
    }

    @Override
    public int deleteWhiteIPActionScopeById(DSLContext dslContext, Long id) {
        return dslContext.deleteFrom(T_WHITE_IP_ACTION_SCOPE).where(
            T_WHITE_IP_ACTION_SCOPE.ID.eq(id)
        ).execute();
    }

    @Override
    public int deleteWhiteIPActionScopeByRecordId(DSLContext dslContext, Long recordId) {
        return dslContext.deleteFrom(T_WHITE_IP_ACTION_SCOPE).where(
            T_WHITE_IP_ACTION_SCOPE.RECORD_ID.eq(recordId)
        ).execute();
    }

    @Override
    public WhiteIPActionScopeDTO getWhiteIPActionScopeById(DSLContext dslContext, Long id) {
        val record = dslContext.selectFrom(T_WHITE_IP_ACTION_SCOPE).where(
            T_WHITE_IP_ACTION_SCOPE.ID.eq(id)
        ).fetchOne();
        if (record == null) {
            return null;
        } else {
            return new WhiteIPActionScopeDTO(
                record.getId(),
                record.getRecordId(),
                record.getActionScopeId(),
                record.getCreator(),
                record.getCreateTime().longValue(),
                record.getLastModifyUser(),
                record.getLastModifyTime().longValue()
            );
        }
    }

    @Override
    public List<WhiteIPActionScopeDTO> getWhiteIPActionScopeByRecordId(DSLContext dslContext, Long recordId) {
        val records = dslContext.selectFrom(T_WHITE_IP_ACTION_SCOPE).where(
            T_WHITE_IP_ACTION_SCOPE.RECORD_ID.eq(recordId)
        ).fetch();
        if (records == null) {
            return new ArrayList<>();
        }
        return records.stream().map(record ->
            new WhiteIPActionScopeDTO(
                record.getId(),
                record.getRecordId(),
                record.getActionScopeId(),
                record.getCreator(),
                record.getCreateTime().longValue(),
                record.getLastModifyUser(),
                record.getLastModifyTime().longValue()
            )
        ).collect(Collectors.toList());
    }

    @Override
    public int updateWhiteIPActionScope(DSLContext dslContext, WhiteIPActionScopeDTO whiteIPActionScopeDTO) {
        return dslContext.update(T_WHITE_IP_ACTION_SCOPE)
            .set(T_WHITE_IP_ACTION_SCOPE.RECORD_ID, whiteIPActionScopeDTO.getRecordId())
            .set(T_WHITE_IP_ACTION_SCOPE.ACTION_SCOPE_ID, whiteIPActionScopeDTO.getActionScopeId())
            .set(T_WHITE_IP_ACTION_SCOPE.CREATOR, whiteIPActionScopeDTO.getCreator())
            .set(T_WHITE_IP_ACTION_SCOPE.CREATE_TIME, ULong.valueOf(whiteIPActionScopeDTO.getCreateTime()))
            .set(T_WHITE_IP_ACTION_SCOPE.LAST_MODIFY_USER, whiteIPActionScopeDTO.getLastModifier())
            .set(T_WHITE_IP_ACTION_SCOPE.LAST_MODIFY_TIME, ULong.valueOf(whiteIPActionScopeDTO.getLastModifyTime()))
            .where(T_WHITE_IP_ACTION_SCOPE.ID.eq(whiteIPActionScopeDTO.getId()))
            .execute();
    }
}
