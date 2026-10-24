USE hw_assignment;
SET @hw_v12_zone = @@session.time_zone;
SET SESSION time_zone = '+08:00';
ALTER TABLE assignment_attachment MODIFY COLUMN storage_name VARCHAR(80) NULL;
SET @hw_v12 = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema='hw_assignment' AND table_name='assignment_attachment'
      AND column_name='removed_at')=0,
    'ALTER TABLE assignment_attachment ADD COLUMN removed_at DATETIME(3) NULL',
    'SELECT 1');
PREPARE hw_v12 FROM @hw_v12; EXECUTE hw_v12; DEALLOCATE PREPARE hw_v12;
-- 历史记录没有删除时刻，从升级时刻保守起算，重复运行不重置。
UPDATE assignment_attachment SET removed_at = NOW(3)
 WHERE status='REMOVED' AND removed_at IS NULL;
SET @hw_v12 = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema='hw_assignment' AND table_name='assignment_attachment'
      AND index_name='idx_attachment_purge')=0,
    'CREATE INDEX idx_attachment_purge ON assignment_attachment(status,removed_at,id)',
    'SELECT 1');
PREPARE hw_v12 FROM @hw_v12; EXECUTE hw_v12; DEALLOCATE PREPARE hw_v12;
SET SESSION time_zone = @hw_v12_zone;
