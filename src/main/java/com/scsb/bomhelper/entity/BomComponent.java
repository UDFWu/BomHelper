package com.scsb.bomhelper.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "\"BomComponent\"")
public class BomComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "\"Id\"")
    private Integer id;

    // Many-to-One: link to BomReport via ScanId
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "\"ScanId\"", referencedColumnName = "\"ScanId\"", nullable = false)
    private BomReport bomReport;

    @Column(name = "\"GroupName\"", length = 255)
    private String groupName;

    @Column(name = "\"Name\"", length = 255, nullable = false)
    private String name;

    @Column(name = "\"Version\"", length = 100)
    private String version;

    @Column(name = "\"Purl\"", length = 1000, columnDefinition = "varchar(1000)")
    private String purl;

    @Column(name = "\"BomRef\"", length = 1000, columnDefinition = "varchar(1000)")
    private String bomRef;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public BomReport getBomReport() { return bomReport; }
    public void setBomReport(BomReport bomReport) { this.bomReport = bomReport; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getPurl() { return purl; }
    public void setPurl(String purl) { this.purl = purl; }

    public String getBomRef() { return bomRef; }
    public void setBomRef(String bomRef) { this.bomRef = bomRef; }
}
