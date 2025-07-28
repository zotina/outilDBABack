package mg.itu.repository;

import mg.itu.model.OracleUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OracleUserRepository extends JpaRepository<OracleUser, String> {
    
    
    @Query("SELECT u FROM OracleUser u WHERE u.accountStatus = 'OPEN'")
    List<OracleUser> findActiveUsers();
    
    
    @Query("SELECT u FROM OracleUser u WHERE u.username NOT IN " +
           "('SYS', 'SYSTEM', 'DBSNMP', 'SYSMAN', 'OUTLN', 'FLOWS_FILES', " +
           "'MDSYS', 'ORDSYS', 'EXFSYS', 'WMSYS', 'APPQOSSYS', 'APEX_030200', " +
           "'APEX_PUBLIC_USER', 'FLOWS_040100', 'OWBSYS', 'OWBSYS_AUDIT', " +
           "'ORDPLUGINS', 'ORDDATA', 'CTXSYS', 'ANONYMOUS', 'XDB', 'XS$NULL', " +
           "'APEX_040200', 'HR', 'OE', 'PM', 'IX', 'SH', 'BI')")
    List<OracleUser> findNonSystemUsers();
    
    
    @Query("SELECT u FROM OracleUser u WHERE u.accountStatus = 'OPEN' AND u.username NOT IN " +
           "('SYS', 'SYSTEM', 'DBSNMP', 'SYSMAN', 'OUTLN', 'FLOWS_FILES', " +
           "'MDSYS', 'ORDSYS', 'EXFSYS', 'WMSYS', 'APPQOSSYS', 'APEX_030200', " +
           "'APEX_PUBLIC_USER', 'FLOWS_040100', 'OWBSYS', 'OWBSYS_AUDIT', " +
           "'ORDPLUGINS', 'ORDDATA', 'CTXSYS', 'ANONYMOUS', 'XDB', 'XS$NULL', " +
           "'APEX_040200', 'HR', 'OE', 'PM', 'IX', 'SH', 'BI')")
    List<OracleUser> findActiveNonSystemUsers();
    
    
    @Query("SELECT u FROM OracleUser u WHERE u.accountStatus = :status")
    List<OracleUser> findByAccountStatus(@Param("status") String status);
    
    
    @Query("SELECT u FROM OracleUser u WHERE u.defaultTablespace = :tablespace")
    List<OracleUser> findByDefaultTablespace(@Param("tablespace") String tablespace);
    
    
    @Query("SELECT u FROM OracleUser u WHERE UPPER(u.username) LIKE UPPER(CONCAT('%', :username, '%'))")
    List<OracleUser> findByUsernameContainingIgnoreCase(@Param("username") String username);
    
    
    @Query("SELECT COUNT(u) FROM OracleUser u WHERE u.accountStatus = 'OPEN'")
    long countActiveUsers();
    
    
    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM OracleUser u " +
           "WHERE u.username = :username AND u.accountStatus = 'OPEN'")
    boolean existsActiveUser(@Param("username") String username);
}