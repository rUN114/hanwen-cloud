USE hw_course;
SET @hw_v13_zone = @@session.time_zone;
SET SESSION time_zone = '+08:00';
ALTER TABLE course_resource MODIFY COLUMN storage_name VARCHAR(60) NULL;
SET @hw_v13 = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema='hw_course' AND table_name='course_resource' AND column_name='removed_at')=0,
 'ALTER TABLE course_resource ADD COLUMN removed_at DATETIME(3) NULL','SELECT 1');
PREPARE hw_v13 FROM @hw_v13; EXECUTE hw_v13; DEALLOCATE PREPARE hw_v13;
UPDATE course_resource SET removed_at=NOW(3) WHERE status='REMOVED' AND removed_at IS NULL;
SET @hw_v13 = IF((SELECT COUNT(*) FROM information_schema.statistics
 WHERE table_schema='hw_course' AND table_name='course_resource' AND index_name='idx_resource_purge')=0,
 'CREATE INDEX idx_resource_purge ON course_resource(status,removed_at,id)','SELECT 1');
PREPARE hw_v13 FROM @hw_v13; EXECUTE hw_v13; DEALLOCATE PREPARE hw_v13;
SET SESSION time_zone = @hw_v13_zone;
