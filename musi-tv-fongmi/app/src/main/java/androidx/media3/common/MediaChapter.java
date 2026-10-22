package androidx.media3.common;
public final class MediaChapter {
    public final String label; public final boolean selected; public final long timeUs;
    public MediaChapter(String label, boolean selected, long timeUs) { this.label = label; this.selected = selected; this.timeUs = timeUs; }
}