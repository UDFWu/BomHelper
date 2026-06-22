package com.scsb.bomhelper.repository;

import com.scsb.bomhelper.entity.BomComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BomComponentRepository extends JpaRepository<BomComponent, Integer> {

    /**
     * 根據報告 ID 查詢該份報告的所有元件
     */
    List<BomComponent> findByBomReportId(Integer reportId);

    /**
     * 根據套件名稱 (Name) 模糊查詢 (例如找所有包含 'spring-core' 的元件)
     */
    List<BomComponent> findByNameContainingIgnoreCase(String name);

    /**
     * 根據精準的 PURL 查詢，用來快速比對是否有受到特定漏洞影響的套件
     */
    List<BomComponent> findByPurl(String purl);

    /**
     * 透過關聯的主檔 (BomReport) 裡的 GitlabProjectId (專案代號) 進行模糊查詢
     * 這是為了支援前端 search.html 的「依專案名稱查詢」功能
     */
    List<BomComponent> findByBomReport_GitlabProjectIdContainingIgnoreCase(String gitlabProjectId);

    /**
     * 💡 修正：結合主檔權限的跨表查詢
     * 改用 @Query 明確定義括號，確保邏輯為：(名稱符合) AND (Group有權限 OR Project有權限)
     */
    @Query("SELECT c FROM BomComponent c " +
            "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :name, '%')) " +
            "AND (c.bomReport.gitlabGroupId IN :groupIds OR c.bomReport.gitlabProjectId IN :projectIds)")
    List<BomComponent> findAuthorizedComponentsByName(
            @Param("name") String name,
            @Param("groupIds") List<String> groupIds,
            @Param("projectIds") List<String> projectIds
    );

    /**
     * 依專案 ID（模糊比對）查詢元件，並僅回傳登入者「所屬 Group 或 Project」範圍內的資料。
     * 用於 search.html 的「依專案名稱查詢」功能。
     */
    @Query("SELECT c FROM BomComponent c " +
            "WHERE LOWER(c.bomReport.gitlabProjectId) LIKE LOWER(CONCAT('%', :projectId, '%')) " +
            "AND (c.bomReport.gitlabGroupId IN :groupIds OR c.bomReport.gitlabProjectId IN :projectIds)")
    List<BomComponent> findAuthorizedComponentsByProjectId(
            @Param("projectId") String projectId,
            @Param("groupIds") List<String> groupIds,
            @Param("projectIds") List<String> projectIds
    );
}