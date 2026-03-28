package com.rsargsyan.grabberr.main_ctx.core.domain.aggregate;

import jakarta.persistence.*;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"cached_file_id", "account_id"}))
public class CachedFileClaim extends AccountScopedAggregateRoot {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cached_file_id", nullable = false)
  private CachedFile cachedFile;

  @SuppressWarnings("unused")
  CachedFileClaim() {}

  public CachedFileClaim(Account account, CachedFile cachedFile) {
    super(account);
    this.cachedFile = cachedFile;
  }

  public CachedFile getCachedFile() {
    return cachedFile;
  }
}
