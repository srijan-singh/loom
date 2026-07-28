/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.loom.storage.repository;

import com.loom.domain.Skill;
import com.loom.storage.DatabaseManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class SkillRepository extends BaseRepository<Skill> {

    // column names
    private static final String COL_NAME = "name";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_CONTENT = "content";
    private static final String COL_TAGS = "tags";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_UPDATED_AT = "updated_at";

    // queries
    private static final String TABLE = "skills";

    private static final String FIND_BY_TAG = String.join(" ",
            "SELECT DISTINCT s.* FROM skills s,",
            " json_each(s.tags) t WHERE t.value = ?");

    private static final String SAVE = upsert(
            TABLE,
            COL_ID,
            COL_NAME,
            COL_DESCRIPTION,
            COL_CONTENT,
            COL_TAGS,
            COL_CREATED_AT,
            COL_UPDATED_AT
    );

    public SkillRepository(DatabaseManager db) {
        super(db, TABLE);
        setMapper(this::map);
    }

    public void save(Skill skill) {
        db().update(SAVE, ps -> {
            ps.setString(1, skill.getId());
            ps.setString(2, skill.getName());
            ps.setString(3, skill.getDescription());
            ps.setString(4, skill.getContent());
            ps.setString(5, toJsonList(skill.getTags()));
            ps.setLong(6, skill.getCreatedAt());
            ps.setLong(7, skill.getUpdatedAt());
        });
    }

    public List<Skill> findByTag(String tag) {
        return db().queryList(FIND_BY_TAG, ps -> ps.setString(1, tag), this::map);
    }

    private Skill map(ResultSet rs) throws SQLException {
        Skill s = new Skill();
        s.setId(rs.getString(COL_ID));
        s.setName(rs.getString(COL_NAME));
        s.setDescription(rs.getString(COL_DESCRIPTION));
        s.setContent(rs.getString(COL_CONTENT));
        s.setTags(fromJsonList(rs.getString(COL_TAGS)));
        s.setCreatedAt(rs.getLong(COL_CREATED_AT));
        s.setUpdatedAt(rs.getLong(COL_UPDATED_AT));
        return s;
    }

}
