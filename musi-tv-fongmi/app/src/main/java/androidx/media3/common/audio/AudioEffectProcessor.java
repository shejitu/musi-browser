package androidx.media3.common.audio;
import java.nio.ByteBuffer;
public final class AudioEffectProcessor extends BaseAudioProcessor {
    @Override
    protected AudioFormat onConfigure(AudioFormat inputAudioFormat) { return inputAudioFormat; }
    @Override
    public void queueInput(ByteBuffer inputBuffer) { replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer); }
    public void setParameters(AudioEffectParameters config, boolean softwareEqualizer) {}
    public void resetParameters() {}
}