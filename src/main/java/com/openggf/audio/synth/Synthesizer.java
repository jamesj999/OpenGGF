package com.openggf.audio.synth;

import com.openggf.audio.smps.DacData;

public interface Synthesizer {
    void writeFm(Object source, int port, int reg, int val);
    void writePsg(Object source, int val);
    void setInstrument(Object source, int channelId, byte[] voice);
    void playDac(Object source, int note);
    void stopDac(Object source);
    void setDacData(DacData data);
    void setFmMute(int channel, boolean mute);
    void setPsgMute(int channel, boolean mute);
    void setDacInterpolate(boolean interpolate);
    void silenceAll();
}
