package androidx.media3.common;
public final class MediaEdition {
    public final String label; public final boolean selected; public final long durationUs;
    public MediaEdition(String label, boolean selected, long durationUs) { this.label = label; this.selected = selected; this.durationUs = durationUs; }
}