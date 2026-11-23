package com.n0hana.echoes_server.device.log;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceLogRepository
    extends JpaRepository<DeviceLogModel, UUID>, JpaSpecificationExecutor<DeviceLogModel> {

  @Query("""
      select l.seq from DeviceLogModel l
      where l.deviceId = :deviceId and l.bootId = :bootId and l.seq in :seqs
      """)
  List<Long> findExistingSeqs(@Param("deviceId") UUID deviceId, @Param("bootId") String bootId,
      @Param("seqs") Collection<Long> seqs);

  @Modifying
  @Transactional
  @Query("delete from DeviceLogModel l where l.occurredAt < :cutoff")
  int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
