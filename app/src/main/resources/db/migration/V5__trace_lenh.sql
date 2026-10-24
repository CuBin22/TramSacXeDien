-- Preserve request ancestry across scheduler retries and process restarts.
ALTER TABLE LenhDieuKhien ADD COLUMN TraceParent varchar(55);
