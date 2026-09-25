package com.frauddetection.api.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.UUID;
import java.util.List;

public interface AccountRepository extends JpaRepository<Account, UUID> {
	@Query("select a from Account a where lower(a.ownerName) like lower(concat('%', :query, '%')) or str(a.accountId) like concat('%', :query, '%') order by a.ownerName asc")
	List<Account> search(@Param("query") String query, Pageable pageable);
}
