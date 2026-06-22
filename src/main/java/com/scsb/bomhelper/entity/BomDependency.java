package com.scsb.bomhelper.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "\"BomDependency\"")
public class BomDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"")
    private Integer id;

    // Many-to-One: link to BomReport via ScanId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"ScanId\"", referencedColumnName = "\"ScanId\"", nullable = false)
    private BomReport bomReport;

    // bom-ref of the parent component (depender)
    @Column(name = "\"ParentRef\"", length = 1000, nullable = false)
    private String parentRef;

    // bom-ref of the child component (dependee)
    @Column(name = "\"ChildRef\"", length = 1000, nullable = false)
    private String childRef;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public BomReport getBomReport() { return bomReport; }
    public void setBomReport(BomReport bomReport) { this.bomReport = bomReport; }

    public String getParentRef() { return parentRef; }
    public void setParentRef(String parentRef) { this.parentRef = parentRef; }

    public String getChildRef() { return childRef; }
    public void setChildRef(String childRef) { this.childRef = childRef; }
}
