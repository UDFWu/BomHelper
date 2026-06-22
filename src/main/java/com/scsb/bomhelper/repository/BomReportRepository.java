package com.scsb.bomhelper.repository;

import com.scsb.bomhelper.entity.BomReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BomReportRepository extends JpaRepository<BomReport, Integer> {

    /**
     * 💡 新增：檢查特定 ScanId 的報告是否已經匯入過
     * 用於 Service 匯入前的重複性檢查，對應資料表中的 ScanId 欄位
     */
    boolean existsByScanId(String scanId);

    /**
     * 檢查特定 SerialNumber 的報告是否已經匯入過
     */
    boolean existsBySerialNumber(String serialNumber);

    /**
     * 根據單一 GitLab Group ID 查詢報告 (預設依匯入時間降冪排序)
     */
    List<BomReport> findByGitlabGroupIdOrderByImportDateDesc(String gitlabGroupId);

    /**
     * 根據多個 GitLab Group IDs 查詢報告
     */
    List<BomReport> findByGitlabGroupIdInOrderByImportDateDesc(List<String> gitlabGroupIds);

    /**
     * 根據單一 GitLab Project ID 查詢報告
     */
    List<BomReport> findByGitlabProjectIdOrderByImportDateDesc(String gitlabProjectId);

    /**
     * 核心權限查詢：找出使用者所屬的 Groups 或被獨立授權的 Projects 裡的所有報告
     */
    List<BomReport> findByGitlabGroupIdInOrGitlabProjectIdInOrderByImportDateDesc(
            List<String> groupIds,
            List<String> projectIds
    );
}