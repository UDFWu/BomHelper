package com.scsb.bomhelper.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

/** Local credentials. Passwords are salted, one-way hashes, never reversible ciphertext. */
@Entity
@Table(name = "BomUser")
public class BomUser {
    @Id
    @Column(name = "UserId", nullable = false, length = 100)
    private String userId;
    @Column(name = "UserPassValidWord", length = 255)
    private String userPassValidWord;
    @Column(name = "Status", nullable = false, length = 1)
    private String status;
    @Column(name = "AuthorityCode", nullable = false, length = 4)
    private String authorityCode;
    @Column(name = "CreatedBy", nullable = false, length = 100)
    private String createdBy;
    @Column(name = "CreatedDate", nullable = false)
    private LocalDateTime createdDate;
    @Column(name = "UpdatedBy", nullable = false, length = 100)
    private String updatedBy;
    @Column(name = "UpdatedDate", nullable = false)
    private LocalDateTime updatedDate;
    public String getUserId() { return userId; }
    public void setUserId(String value) { this.userId = value; }
    public String getUserPassValidWord() { return userPassValidWord; }
    public void setUserPassValidWord(String value) { this.userPassValidWord = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getAuthorityCode() { return authorityCode; }
    public void setAuthorityCode(String value) { this.authorityCode = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { this.createdBy = value; }
    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime value) { this.createdDate = value; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { this.updatedBy = value; }
    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime value) { this.updatedDate = value; }
}
