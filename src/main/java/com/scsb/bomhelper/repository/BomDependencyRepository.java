package com.scsb.bomhelper.repository;

import com.scsb.bomhelper.entity.BomDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BomDependencyRepository extends JpaRepository<BomDependency, Integer> {

    /**
     * 💡 升級版：精準判斷是否為「間接依賴 (Transitive)」
     * * 邏輯說明：
     * 我們要找出傳入的套件 (d1) 的「父節點」(d1.parentRef)。
     * 如果這個父節點「同時也是別人的子節點」(d2.childRef)，
     * 代表這個父節點只是一個中間的套件，而不是專案本身 (Root)。
     * 因此，該套件就是「間接依賴」。
     * * 反之，如果父節點不是任何人的子節點（代表它是專案本身），那就是「直接引入」。
     */
    @Query("SELECT CASE WHEN COUNT(d1) > 0 THEN true ELSE false END " +
            "FROM BomDependency d1 " +
            "WHERE d1.childRef = :bomRef AND d1.bomReport.scanId = :scanId " +
            "AND EXISTS (SELECT 1 FROM BomDependency d2 WHERE d2.childRef = d1.parentRef AND d2.bomReport.scanId = :scanId)")
    boolean checkIsTransitive(@Param("bomRef") String bomRef, @Param("scanId") String scanId);
}