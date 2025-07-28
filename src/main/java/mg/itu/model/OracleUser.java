package mg.itu.model;

import javax.persistence.*;
import java.util.Date;

@Entity
@Table(name = "DBA_USERS")
public class OracleUser {
    
    @Id
    @Column(name = "USERNAME")
    private String username;
    
    @Column(name = "USER_ID")
    private Long userId;
    
    @Column(name = "ACCOUNT_STATUS")
    private String accountStatus;
    
    @Column(name = "LOCK_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date lockDate;
    
    @Column(name = "EXPIRY_DATE")
    @Temporal(TemporalType.TIMESTAMP)
    private Date expiryDate;
    
    @Column(name = "DEFAULT_TABLESPACE")
    private String defaultTablespace;
    
    @Column(name = "TEMPORARY_TABLESPACE")
    private String temporaryTablespace;
    
    @Column(name = "CREATED")
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;
    
    @Column(name = "PROFILE")
    private String profile;

    // Constructeurs
    public OracleUser() {}
    
    public OracleUser(String username) {
        this.username = username;
    }

    // Getters et Setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Date getLockDate() {
        return lockDate;
    }

    public void setLockDate(Date lockDate) {
        this.lockDate = lockDate;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getDefaultTablespace() {
        return defaultTablespace;
    }

    public void setDefaultTablespace(String defaultTablespace) {
        this.defaultTablespace = defaultTablespace;
    }

    public String getTemporaryTablespace() {
        return temporaryTablespace;
    }

    public void setTemporaryTablespace(String temporaryTablespace) {
        this.temporaryTablespace = temporaryTablespace;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    // Méthodes utilitaires
    public boolean isAccountOpen() {
        return "OPEN".equals(accountStatus);
    }
    
    public boolean isAccountLocked() {
        return accountStatus != null && accountStatus.contains("LOCKED");
    }
    
    public boolean isAccountExpired() {
        return accountStatus != null && accountStatus.contains("EXPIRED");
    }

    @Override
    public String toString() {
        return "OracleUser{" +
                "username='" + username + '\'' +
                ", accountStatus='" + accountStatus + '\'' +
                ", defaultTablespace='" + defaultTablespace + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OracleUser that = (OracleUser) o;
        return username != null ? username.equals(that.username) : that.username == null;
    }

    @Override
    public int hashCode() {
        return username != null ? username.hashCode() : 0;
    }
}