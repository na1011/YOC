package com.yoc.wms.mail.dao;

import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 메일 DAO (리팩토링)
 */
@Repository
public class MailDao {

    @Autowired
    private SqlSession sqlSession;

    // ========================================
    // 기본 CRUD (범용)
    // ========================================

    public Map<String, Object> selectOne(String statement, Map<String, Object> params) {
        return sqlSession.selectOne(statement, params);
    }

    public List<Map<String, Object>> selectList(String statement, Map<String, Object> params) {
        return sqlSession.selectList(statement, params);
    }

    public int insert(String statement, Map<String, Object> params) {
        return sqlSession.insert(statement, params);
    }

    public int update(String statement, Map<String, Object> params) {
        return sqlSession.update(statement, params);
    }

    public int delete(String statement, Map<String, Object> params) {
        return sqlSession.delete(statement, params);
    }
}