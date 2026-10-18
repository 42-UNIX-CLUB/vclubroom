package pt.vclubroom.media.application;

/** What the object store reports about an uploaded object. */
public record StoredObject(long sizeBytes, String contentType) {
}
