CREATE SEQUENCE CongSuatCommandRevision;
CREATE TABLE CongSuatOutbox (
    MaCong varchar(20) PRIMARY KEY REFERENCES CongSac(MaCong) ON DELETE CASCADE,
    Revision bigint NOT NULL,
    CongSuatKw numeric(12,2) NOT NULL CHECK (CongSuatKw >= 0),
    TraceParent varchar(55),
    DaAck boolean NOT NULL DEFAULT false
);
INSERT INTO CongSuatOutbox(MaCong, Revision, CongSuatKw)
SELECT MaCong, nextval('CongSuatCommandRevision'), CongSuatDuocCap FROM CongSac;
