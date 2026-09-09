-- Batch uploads create one queued task per file. Application-level locking still
-- prevents concurrent non-batch imports; the partial unique index blocked batches.
DROP INDEX IF EXISTS idx_import_active;
