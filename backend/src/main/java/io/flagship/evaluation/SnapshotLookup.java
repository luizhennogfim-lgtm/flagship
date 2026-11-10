package io.flagship.evaluation;

/** Result of looking a flag up: found, or why it was not. */
public record SnapshotLookup(Status status, FlagSnapshot snapshot) {

    public enum Status { FOUND, FLAG_NOT_FOUND, ENVIRONMENT_NOT_FOUND }

    public static SnapshotLookup found(FlagSnapshot snapshot) {
        return new SnapshotLookup(Status.FOUND, snapshot);
    }

    public static SnapshotLookup flagNotFound() {
        return new SnapshotLookup(Status.FLAG_NOT_FOUND, null);
    }

    public static SnapshotLookup environmentNotFound() {
        return new SnapshotLookup(Status.ENVIRONMENT_NOT_FOUND, null);
    }
}
