package io.quarkiverse.desktop.showcase.pages.sound;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletionStage;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiFileFormat;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.Port;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import jakarta.inject.Singleton;

import io.quarkiverse.desktop.showcase.core.Categories;
import io.quarkiverse.desktop.showcase.core.Check;
import io.quarkiverse.desktop.showcase.core.Checks;
import io.quarkiverse.desktop.showcase.core.ChecksView;
import io.quarkiverse.desktop.showcase.core.Edt;
import io.quarkiverse.desktop.showcase.core.FeaturePage;
import io.quarkiverse.desktop.showcase.core.Snapshots;
import io.quarkiverse.desktop.showcase.core.Ui;

/**
 * {@code javax.sound} without playing anything : generated tones written and read as WAVE, AU and AIFF (and float and
 * extensible WAVE), encoding, sample size, byte order, channel and sample rate conversions (the format conversion
 * providers : u-law, a-law, PCM, float), the mixers and lines of the machine (environment : looked up, a clip opened
 * but never started) ; MIDI sequences built with short, meta and system exclusive messages, written and read as
 * standard MIDI files of type 0 and 1, the sequencer and the software synthesizer (Gervill) without opening them, a
 * soundbank read from a WAVE file, and the offline rendering of the MIDI file to audio by the MIDI audio file reader.
 * <p>
 * Everything goes through the service providers of the {@code java.desktop} module (audio file readers and writers,
 * format conversion providers, mixer providers, MIDI file readers and writers, soundbank readers, device providers) :
 * a native executable needs them registered. {@code -Dshowcase.sideEffects=true} also plays the tone. AWT only.
 */
@Singleton
public class SoundPage implements FeaturePage {

    static final float RATE = 22050f;
    static final int FRAMES = 4410;
    private static final int WAVE_WIDTH = 1000;
    private static final int WAVE_HEIGHT = 110;

    private ChecksView devices;
    private ChecksView midi;
    private Container renderRow;

    @Override
    public String id() {
        return "sound";
    }

    @Override
    public String title() {
        return "Sampled audio and MIDI";
    }

    @Override
    public String category() {
        return Categories.SOUND;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Component build() throws Exception {
        short[] tone = tone();
        byte[] pcm = littleEndian(tone);
        List<Check> sampled = sampledChecks(tone, pcm);
        short[] ulaw = decode16(AudioSystem.getAudioInputStream(pcm16(),
                AudioSystem.getAudioInputStream(AudioFormat.Encoding.ULAW, stream(pcm, pcm16()))));
        devices = ChecksView.table("Mixers, lines and MIDI devices of this machine (environment)",
                List.of(Check.info("lookup", "pending")));
        midi = ChecksView.table("MIDI", List.of(Check.info("sequence", "pending")));
        renderRow = Ui.row(0, Ui.text("rendering...", 1000));
        return Ui.column(14,
                Ui.text("A generated chord (A4 + E5, 0.2 s, 22050 Hz, 16 bit) : its first 20 ms (blue) with the u-law "
                        + "round trip (orange), and the whole tone. Below the tables, the offline rendering of the "
                        + "generated MIDI file by the software synthesizer (left and right channels). Nothing is played.",
                        1000),
                Ui.image(waveforms(tone, ulaw)),
                ChecksView.table("Sampled audio (files, formats, conversions)", sampled),
                devices,
                midi,
                Ui.title("MIDI file rendered to audio (SoftMidiAudioFileReader)"),
                renderRow);
    }

    @Override
    public CompletionStage<?> ready(Component content) {
        ChecksView devicesView = devices;
        ChecksView midiView = midi;
        Container row = renderRow;
        return Edt.background(SoundPage::deviceChecks).thenAccept(devicesView::setChecks)
                .thenCompose(v -> Edt.background(SoundPage::midiChecks))
                .thenAccept(result -> {
                    midiView.setChecks(result.checks());
                    row.removeAll();
                    row.add(result.image() == null ? Ui.text("no rendering", 1000) : Ui.image(result.image()));
                    row.invalidate();
                });
    }

    @Override
    public void dispose(Component content) {
        devices = null;
        midi = null;
        renderRow = null;
    }

    // ------------------------------------------------------------------------------------------------ generation

    /**
     * The chord : A4 (440 Hz) and E5 (659.25 Hz), 10 ms fade in and out. {@link StrictMath} : the same samples on every
     * runtime (JIT, interpreter, native).
     */
    static short[] tone() {
        short[] samples = new short[FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            double t = i / (double) RATE;
            double envelope = Math.min(1.0, Math.min(i / 220.5, (FRAMES - 1 - i) / 220.5));
            double v = 0.5 * StrictMath.sin(2 * StrictMath.PI * 440 * t) + 0.3 * StrictMath.sin(2 * StrictMath.PI * 659.25 * t);
            samples[i] = (short) StrictMath.round(v * envelope * 32767 * 0.9);
        }
        return samples;
    }

    static AudioFormat pcm16() {
        return new AudioFormat(RATE, 16, 1, true, false);
    }

    static byte[] littleEndian(short[] samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(samples);
        return buffer.array();
    }

    static AudioInputStream stream(byte[] data, AudioFormat format) {
        return new AudioInputStream(new ByteArrayInputStream(data), format, data.length / format.getFrameSize());
    }

    /**
     * The 16 bit little endian mono samples of {@code in} (already in that format).
     */
    static short[] decode16(AudioInputStream in) throws IOException {
        byte[] bytes = in.readAllBytes();
        short[] samples = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples);
        return samples;
    }

    private static int maxError(short[] a, short[] b) {
        int max = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            max = Math.max(max, Math.abs(a[i] - b[i]));
        }
        return max;
    }

    private static String format(AudioFormat format) {
        return format.toString().replace("  ", " ").trim();
    }

    // ------------------------------------------------------------------------------------------------- sampled

    private static List<Check> sampledChecks(short[] tone, byte[] pcm) {
        List<Check> checks = new ArrayList<>();
        checks.add(Checks.expect("AudioFormat", "PCM_SIGNED 22050.0 Hz, 16 bit, mono, 2 bytes/frame, little-endian",
                () -> format(pcm16())));
        checks.add(Checks.expect("generated samples : count, peak, SHA-256 of the bytes", "4410, 23592, 8dc6cdda96c7bdc9",
                () -> tone.length + ", " + Arrays.stream(littleEndianToInts(pcm)).map(Math::abs).max().orElse(0) + ", "
                        + Checks.sha256(pcm)));
        checks.add(Checks.expect("AudioSystem.getAudioFileTypes()", "AIFF AU WAVE",
                () -> String.join(" ", new TreeSet<>(Arrays.stream(AudioSystem.getAudioFileTypes())
                        .map(AudioFileFormat.Type::toString).toList()))));
        for (AudioFileFormat.Type type : List.of(AudioFileFormat.Type.WAVE, AudioFileFormat.Type.AU,
                AudioFileFormat.Type.AIFF)) {
            checks.add(Checks.run(type + " : written, read back (type, format, frames), samples identical", () -> {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int written = AudioSystem.write(stream(pcm, pcm16()), type, out);
                byte[] file = out.toByteArray();
                AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(file));
                AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(file));
                short[] back = decode16(AudioSystem.getAudioInputStream(pcm16(), in));
                return written + " bytes (" + Checks.sha256(file) + "), " + fileFormat.getType() + ", "
                        + format(fileFormat.getFormat()) + ", " + fileFormat.getFrameLength() + " frames, "
                        + (Arrays.equals(back, tone) ? "identical" : "different");
            }));
        }
        checks.add(Checks.expect("AudioSystem.write(WAVE, File) : file size", "8864 bytes", () -> {
            Path file = Edt.tempDir().resolve("sound-tone.wav");
            AudioSystem.write(stream(pcm, pcm16()), AudioFileFormat.Type.WAVE, file.toFile());
            return Files.size(file) + " bytes";
        }));
        checks.add(Checks.expect("AudioSystem.getTargetEncodings(PCM_SIGNED)", "ALAW PCM_FLOAT PCM_SIGNED PCM_UNSIGNED ULAW",
                () -> String.join(" ", new TreeSet<>(Arrays.stream(AudioSystem.getTargetEncodings(
                        AudioFormat.Encoding.PCM_SIGNED)).map(AudioFormat.Encoding::toString).toList()))));
        checks.add(Checks.expect("isConversionSupported(ULAW / ALAW / PCM_FLOAT, 16 bit PCM)", "true / true / true",
                () -> AudioSystem.isConversionSupported(AudioFormat.Encoding.ULAW, pcm16()) + " / "
                        + AudioSystem.isConversionSupported(AudioFormat.Encoding.ALAW, pcm16()) + " / "
                        + AudioSystem.isConversionSupported(AudioFormat.Encoding.PCM_FLOAT, pcm16())));
        for (AudioFormat.Encoding encoding : List.of(AudioFormat.Encoding.ULAW, AudioFormat.Encoding.ALAW)) {
            checks.add(Checks.run(encoding + " : format, bytes, round trip max error", () -> {
                AudioInputStream encoded = AudioSystem.getAudioInputStream(encoding, stream(pcm, pcm16()));
                AudioFormat encodedFormat = encoded.getFormat();
                byte[] bytes = encoded.readAllBytes();
                short[] back = decode16(AudioSystem.getAudioInputStream(pcm16(),
                        AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED,
                                stream(bytes, encodedFormat))));
                return format(encodedFormat) + ", " + bytes.length + " bytes (" + Checks.sha256(bytes) + "), max error "
                        + maxError(tone, back);
            }));
        }
        checks.add(Checks.run("PCM_UNSIGNED 8 bit : bytes, round trip max error", () -> {
            AudioFormat unsigned = new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, RATE, 8, 1, 1, RATE, false);
            byte[] bytes = AudioSystem.getAudioInputStream(unsigned, stream(pcm, pcm16())).readAllBytes();
            short[] back = decode16(AudioSystem.getAudioInputStream(pcm16(), stream(bytes, unsigned)));
            return bytes.length + " bytes (" + Checks.sha256(bytes) + "), max error " + maxError(tone, back);
        }));
        checks.add(Checks.run("16 bit big endian : first sample bytes, bytes", () -> {
            AudioFormat big = new AudioFormat(RATE, 16, 1, true, true);
            byte[] bytes = AudioSystem.getAudioInputStream(big, stream(pcm, pcm16())).readAllBytes();
            return String.format("%02X %02X / %02X %02X (sample 100)", pcm[200], pcm[201], bytes[200], bytes[201]) + ", "
                    + Checks.sha256(bytes);
        }));
        checks.add(Checks.run("PCM_FLOAT 32 bit : float WAVE written and read back (float file reader and writer)", () -> {
            AudioFormat floats = new AudioFormat(AudioFormat.Encoding.PCM_FLOAT, RATE, 32, 1, 4, RATE, false);
            AudioInputStream converted = AudioSystem.getAudioInputStream(floats, stream(pcm, pcm16()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AudioSystem.write(converted, AudioFileFormat.Type.WAVE, out);
            AudioFileFormat read = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(out.toByteArray()));
            short[] back = decode16(AudioSystem.getAudioInputStream(pcm16(),
                    AudioSystem.getAudioInputStream(new ByteArrayInputStream(out.toByteArray()))));
            return out.size() + " bytes (" + Checks.sha256(out.toByteArray()) + "), " + format(read.getFormat())
                    + ", max error " + maxError(tone, back);
        }));
        checks.add(Checks.run("stereo 16 bit : frames, bytes", () -> {
            AudioFormat stereo = new AudioFormat(RATE, 16, 2, true, false);
            AudioInputStream converted = AudioSystem.getAudioInputStream(stereo, stream(pcm, pcm16()));
            byte[] bytes = converted.readAllBytes();
            return converted.getFormat().getChannels() + " channels, " + bytes.length / 4 + " frames ("
                    + Checks.sha256(bytes) + ")";
        }));
        checks.add(Checks.run("resampled to 44100 Hz : frames, bytes", () -> {
            AudioFormat rate = new AudioFormat(44100f, 16, 1, true, false);
            AudioInputStream converted = AudioSystem.getAudioInputStream(rate, stream(pcm, pcm16()));
            byte[] bytes = converted.readAllBytes();
            return converted.getFrameLength() + " / " + bytes.length / 2 + " frames (" + Checks.sha256(bytes) + ")";
        }));
        checks.add(Checks.run("WAVE_FORMAT_EXTENSIBLE (hand-made header) : format, samples", () -> {
            byte[] file = extensibleWave(pcm);
            AudioFileFormat read = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(file));
            short[] back = decode16(AudioSystem.getAudioInputStream(pcm16(),
                    AudioSystem.getAudioInputStream(new ByteArrayInputStream(file))));
            return read.getType() + ", " + format(read.getFormat()) + ", " + (Arrays.equals(back, tone) ? "identical"
                    : "different");
        }));
        checks.add(Checks.expect("AudioInputStream : skip(1000), available, mark / reset", "1000, 7820, true 7820",
                () -> {
                    AudioInputStream in = stream(pcm, pcm16());
                    long skipped = in.skip(1000);
                    int available = in.available();
                    in.mark(100);
                    in.read(new byte[10]);
                    in.reset();
                    return skipped + ", " + available + ", " + in.markSupported() + " " + in.available();
                }));
        checks.add(Checks.expect("AudioFormat properties, matches", "{bitrate=352800}, true, false", () -> {
            AudioFormat withProperties = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, RATE, 16, 1, 2, RATE, false,
                    Map.of("bitrate", 352800));
            return new TreeMap<>(withProperties.properties()) + ", " + withProperties.matches(pcm16()) + ", "
                    + pcm16().matches(new AudioFormat(RATE, 16, 1, true, true));
        }));
        checks.add(Checks.expect("AudioFileFormat of the WAVE : properties", "{}", () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            AudioSystem.write(stream(pcm, pcm16()), AudioFileFormat.Type.WAVE, out);
            return new TreeMap<>(AudioSystem.getAudioFileFormat(new ByteArrayInputStream(out.toByteArray())).properties());
        }));
        checks.add(Checks.expect("not an audio file", "javax.sound.sampled.UnsupportedAudioFileException", () -> {
            try {
                return AudioSystem.getAudioFileFormat(new ByteArrayInputStream(new byte[64])).toString();
            } catch (javax.sound.sampled.UnsupportedAudioFileException e) {
                return e.getClass().getName();
            }
        }));
        return checks;
    }

    private static int[] littleEndianToInts(byte[] pcm) {
        int[] values = new int[pcm.length / 2];
        for (int i = 0; i < values.length; i++) {
            values[i] = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
        }
        return values;
    }

    /**
     * A WAVE file with a {@code WAVE_FORMAT_EXTENSIBLE} format chunk (read by the extensible WAVE file reader).
     */
    static byte[] extensibleWave(byte[] pcm) {
        ByteBuffer b = ByteBuffer.allocate(12 + 8 + 40 + 8 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(4 + 8 + 40 + 8 + pcm.length);
        b.put("WAVE".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(40);
        b.putShort((short) 0xFFFE).putShort((short) 1).putInt((int) RATE).putInt((int) RATE * 2).putShort((short) 2)
                .putShort((short) 16);
        b.putShort((short) 22).putShort((short) 16).putInt(4);
        // KSDATAFORMAT_SUBTYPE_PCM 00000001-0000-0010-8000-00AA00389B71
        b.put(new byte[] { 1, 0, 0, 0, 0, 0, 0x10, 0, (byte) 0x80, 0, 0, (byte) 0xAA, 0, 0x38, (byte) 0x9B, 0x71 });
        b.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(pcm.length);
        b.put(pcm);
        return b.array();
    }

    // ------------------------------------------------------------------------------------------------- devices

    private static List<Check> deviceChecks() {
        List<Check> checks = new ArrayList<>();
        Mixer.Info[] mixers;
        try {
            mixers = AudioSystem.getMixerInfo();
        } catch (Throwable t) {
            return List.of(Check.fail("AudioSystem.getMixerInfo()", Checks.describe(t)));
        }
        Map<String, Integer> kinds = new TreeMap<>();
        for (Mixer.Info info : mixers) {
            kinds.merge(info.getDescription(), 1, Integer::sum);
        }
        checks.add(Check.info("AudioSystem.getMixerInfo()", mixers.length + " mixers " + kinds));
        checks.add(Checks.info("first mixers", () -> String.join(" | ", Arrays.stream(mixers).limit(4)
                .map(Mixer.Info::getName).toList())));
        checks.add(Checks.info("isLineSupported : Clip, SourceDataLine, TargetDataLine, Port.SPEAKER",
                () -> AudioSystem.isLineSupported(new DataLine.Info(Clip.class, pcm16())) + ", "
                        + AudioSystem.isLineSupported(new DataLine.Info(SourceDataLine.class, pcm16())) + ", "
                        + AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, pcm16())) + ", "
                        + AudioSystem.isLineSupported(Port.Info.SPEAKER)));
        checks.add(Checks.info("default mixer : source and target line kinds", () -> {
            Mixer mixer = AudioSystem.getMixer(null);
            return mixer.getMixerInfo().getName() + " : " + mixer.getSourceLineInfo().length + " source, "
                    + mixer.getTargetLineInfo().length + " target";
        }));
        // a clip opened (the audio device is opened) but never started : nothing is heard
        checks.add(Checks.info("Clip : open (frames, microseconds), controls, events", () -> {
            if (!AudioSystem.isLineSupported(new DataLine.Info(Clip.class, pcm16()))) {
                return "no clip line";
            }
            Clip clip = AudioSystem.getClip();
            List<String> events = Collections.synchronizedList(new ArrayList<>());
            clip.addLineListener(e -> events.add(e.getType().toString()));
            clip.open(stream(littleEndian(tone()), pcm16()));
            String result;
            try {
                result = clip.getFrameLength() + " frames, " + clip.getMicrosecondLength() + " us, controls "
                        + String.join(" ", Arrays.stream(clip.getControls()).map(c -> c.getType().toString()).toList());
                if (Boolean.getBoolean("showcase.sideEffects")) {
                    clip.start();
                    clip.drain();
                    result += ", played";
                }
            } finally {
                clip.close();
            }
            return result + ", events " + String.join(" ", events.stream().filter(e -> !e.equals(
                    LineEvent.Type.START.toString()) && !e.equals(LineEvent.Type.STOP.toString())).toList());
        }));
        checks.add(Checks.info("MidiSystem.getMidiDeviceInfo()", () -> String.join(" | ", Arrays.stream(
                MidiSystem.getMidiDeviceInfo()).map(MidiDevice.Info::getName).toList())));
        return checks;
    }

    // ---------------------------------------------------------------------------------------------------- MIDI

    private record MidiResult(List<Check> checks, BufferedImage image) {
    }

    /**
     * Three tracks at 480 ticks per quarter note : a conductor track (tempo 120 then 100 bpm, time signature, names,
     * GM reset system exclusive), a melody (channel 0, piano) and a bass (channel 1, with pitch bends). No reverb nor
     * chorus sends : the offline rendering is simpler.
     */
    static Sequence sequence() throws InvalidMidiDataException {
        Sequence sequence = new Sequence(Sequence.PPQ, 480);
        Track conductor = sequence.createTrack();
        conductor.add(new MidiEvent(new MetaMessage(0x03, "Showcase".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                8), 0));
        conductor.add(new MidiEvent(new MetaMessage(0x51, new byte[] { 0x07, (byte) 0xA1, 0x20 }, 3), 0));
        conductor.add(new MidiEvent(new MetaMessage(0x58, new byte[] { 4, 2, 24, 8 }, 4), 0));
        byte[] gmOn = { (byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7 };
        conductor.add(new MidiEvent(new SysexMessage(gmOn, gmOn.length), 0));
        conductor.add(new MidiEvent(new MetaMessage(0x01, "Generated by the showcase".getBytes(
                java.nio.charset.StandardCharsets.US_ASCII), 25), 0));
        conductor.add(new MidiEvent(new MetaMessage(0x51, new byte[] { 0x09, 0x27, (byte) 0xC0 }, 3), 1920));

        Track melody = sequence.createTrack();
        setUp(melody, 0, 0, 32);
        int[] notes = { 60, 64, 67, 72, 76 };
        for (int i = 0; i < notes.length; i++) {
            melody.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, notes[i], 90), i * 480L));
            melody.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, notes[i], 0), i * 480L + 440));
        }
        Track bass = sequence.createTrack();
        setUp(bass, 1, 32, 96);
        bass.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 1, 36, 100), 0));
        bass.add(new MidiEvent(new ShortMessage(ShortMessage.PITCH_BEND, 1, 0x00, 0x50), 480));
        bass.add(new MidiEvent(new ShortMessage(ShortMessage.PITCH_BEND, 1, 0x00, 0x40), 720));
        bass.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 1, 36, 64), 900));
        bass.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 1, 43, 100), 960));
        bass.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 1, 43, 64), 1860));
        return sequence;
    }

    private static void setUp(Track track, int channel, int program, int pan) throws InvalidMidiDataException {
        track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, channel, program, 0), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 7, 100), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 10, pan), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 91, 0), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.CONTROL_CHANGE, channel, 93, 0), 0));
    }

    private static String events(Sequence sequence) {
        List<String> counts = new ArrayList<>();
        for (Track track : sequence.getTracks()) {
            counts.add(String.valueOf(track.size()));
        }
        return String.join("/", counts);
    }

    /**
     * The status bytes and data of every event, in track order : equal for equal sequences.
     */
    private static String digest(Sequence sequence) {
        StringBuilder sb = new StringBuilder();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                sb.append(event.getTick()).append(':').append(java.util.HexFormat.of().formatHex(event.getMessage()
                        .getMessage())).append(' ');
            }
            sb.append('|');
        }
        return Checks.sha256(sb.toString());
    }

    private static MidiResult midiChecks() {
        List<Check> checks = new ArrayList<>();
        Sequence sequence;
        byte[] type1;
        try {
            sequence = sequence();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MidiSystem.write(sequence, 1, out);
            type1 = out.toByteArray();
        } catch (Throwable t) {
            return new MidiResult(List.of(Check.fail("sequence", Checks.describe(t))), null);
        }
        checks.add(Checks.expect("sequence : tracks, events per track, ticks, microseconds, resolution",
                "3, 7/16/12, 2360, 2550000, 480 PPQ", () -> sequence.getTracks().length + ", " + events(sequence) + ", "
                        + sequence.getTickLength() + ", " + sequence.getMicrosecondLength() + ", "
                        + sequence.getResolution() + (sequence.getDivisionType() == Sequence.PPQ ? " PPQ" : " SMPTE")));
        checks.add(Checks.expect("messages : note on, tempo meta, system exclusive, pitch bend",
                "144 0 60 90, 81 [7, -95, 32], 240 5, 224 1 0 80", () -> {
                    ShortMessage note = (ShortMessage) sequence.getTracks()[1].get(5).getMessage();
                    MetaMessage tempo = (MetaMessage) sequence.getTracks()[0].get(1).getMessage();
                    SysexMessage sysex = (SysexMessage) sequence.getTracks()[0].get(3).getMessage();
                    ShortMessage bend = (ShortMessage) sequence.getTracks()[2].get(6).getMessage();
                    return note.getCommand() + " " + note.getChannel() + " " + note.getData1() + " " + note.getData2() + ", "
                            + tempo.getType() + " " + Arrays.toString(tempo.getData()) + ", " + sysex.getStatus() + " "
                            + sysex.getData().length + ", " + bend.getCommand() + " " + bend.getChannel() + " "
                            + bend.getData1() + " " + bend.getData2();
                }));
        checks.add(Checks.expect("ShortMessage(NOTE_ON, channel 16)", "javax.sound.midi.InvalidMidiDataException", () -> {
            try {
                return new ShortMessage(ShortMessage.NOTE_ON, 16, 60, 90).toString();
            } catch (InvalidMidiDataException e) {
                return e.getClass().getName();
            }
        }));
        checks.add(Checks.expect("MidiSystem.getMidiFileTypes() / (sequence)", "[0, 1] / [1]",
                () -> Arrays.toString(MidiSystem.getMidiFileTypes()) + " / "
                        + Arrays.toString(MidiSystem.getMidiFileTypes(sequence))));
        checks.add(Check.pass("standard MIDI file type 1 : size, SHA-256", type1.length + " bytes, "
                + Checks.sha256(type1)));
        checks.add(Checks.expect("MidiSystem.getMidiFileFormat : type, resolution, bytes, microseconds",
                "1, 480, -1, -1", () -> {
                    MidiFileFormat format = MidiSystem.getMidiFileFormat(new ByteArrayInputStream(type1));
                    return format.getType() + ", " + format.getResolution() + ", " + format.getByteLength() + ", "
                            + format.getMicrosecondLength();
                }));
        checks.add(Checks.expect("MidiSystem.getSequence : same events, written again identical", "true, true", () -> {
            Sequence read = MidiSystem.getSequence(new ByteArrayInputStream(type1));
            ByteArrayOutputStream again = new ByteArrayOutputStream();
            MidiSystem.write(read, 1, again);
            return digest(read).equals(digest(sequence)) + ", " + Arrays.equals(again.toByteArray(), type1);
        }));
        checks.add(Checks.run("type 0 (tracks merged) : file types, size", () -> {
            Sequence merged = new Sequence(Sequence.PPQ, 480);
            Track single = merged.createTrack();
            for (Track track : sequence.getTracks()) {
                for (int i = 0; i < track.size(); i++) {
                    MidiMessage message = track.get(i).getMessage();
                    if (!(message instanceof MetaMessage meta && meta.getType() == 0x2F)) {
                        single.add(track.get(i));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MidiSystem.write(merged, 0, out);
            return Arrays.toString(MidiSystem.getMidiFileTypes(merged)) + ", " + out.size() + " bytes ("
                    + Checks.sha256(out.toByteArray()) + "), type " + MidiSystem.getMidiFileFormat(
                            new ByteArrayInputStream(out.toByteArray())).getType();
        }));
        checks.add(Checks.expect("Sequencer (not opened) : name, ticks, microseconds, tempo, open",
                "Real Time Sequencer, 2360, 2550000, 120.0, false", () -> {
                    Sequencer sequencer = MidiSystem.getSequencer(false);
                    sequencer.setSequence(sequence);
                    return sequencer.getDeviceInfo().getName() + ", " + sequencer.getTickLength() + ", "
                            + sequencer.getMicrosecondLength() + ", " + sequencer.getTempoInBPM() + ", "
                            + sequencer.isOpen();
                }));
        checks.add(Checks.expect("Synthesizer (not opened) : device, polyphony, latency, channels",
                "Gervill, OpenJDK, Software MIDI Synthesizer, 1.0, 64, 200000, 16", () -> {
                    Synthesizer synthesizer = MidiSystem.getSynthesizer();
                    MidiDevice.Info info = synthesizer.getDeviceInfo();
                    return info.getName() + ", " + info.getVendor() + ", " + info.getDescription() + ", "
                            + info.getVersion() + ", " + synthesizer.getMaxPolyphony() + ", " + synthesizer.getLatency()
                            + ", " + synthesizer.getChannels().length;
                }));
        // Windows : %SystemRoot%\system32\drivers\gm.dls ; otherwise a generated soundbank (cached in ~/.gervill)
        checks.add(Checks.info("Synthesizer.getDefaultSoundbank() : name, instruments", () -> {
            Soundbank soundbank = MidiSystem.getSynthesizer().getDefaultSoundbank();
            return soundbank == null ? "none" : soundbank.getName() + ", " + soundbank.getInstruments().length;
        }));
        checks.add(Checks.expect("MidiSystem.getSoundbank(WAVE file) : instruments, supported by the synthesizer",
                "1, true", () -> {
                    ByteArrayOutputStream wave = new ByteArrayOutputStream();
                    AudioSystem.write(stream(littleEndian(tone()), pcm16()), AudioFileFormat.Type.WAVE, wave);
                    Soundbank soundbank = MidiSystem.getSoundbank(new ByteArrayInputStream(wave.toByteArray()));
                    return soundbank.getInstruments().length + ", "
                            + MidiSystem.getSynthesizer().isSoundbankSupported(soundbank);
                }));

        // offline rendering : the MIDI audio file reader plays the file into a software synthesizer stream
        BufferedImage image = null;
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(type1));
            checks.add(Check.pass("AudioSystem.getAudioFileFormat(MIDI file) : type, frames", fileFormat.getType() + ", "
                    + fileFormat.getFrameLength()));
            AudioInputStream rendered = AudioSystem.getAudioInputStream(new ByteArrayInputStream(type1));
            AudioFormat format = rendered.getFormat();
            byte[] bytes = rendered.readAllBytes();
            checks.add(Checks.expect("rendered audio : format, frames",
                    "PCM_SIGNED 44100.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian, 264600", () -> format(format)
                            + ", " + bytes.length / 4));
            short[] left = new short[bytes.length / 4];
            short[] right = new short[bytes.length / 4];
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < left.length; i++) {
                left[i] = buffer.getShort();
                right[i] = buffer.getShort();
            }
            checks.add(Check.pass("rendered audio : left / right peak, RMS", stats(left) + " / " + stats(right)));
            checks.add(Check.pass("rendered audio : sound from / to (ms)", span(left, right)));
            checks.add(Check.pass("rendered audio : SHA-256", Checks.sha256(bytes)));
            image = envelope(left, right);
        } catch (Throwable t) {
            checks.add(Check.fail("offline rendering", Checks.describe(t)));
        }
        return new MidiResult(checks, image);
    }

    private static String stats(short[] samples) {
        int peak = 0;
        double sum = 0;
        for (short s : samples) {
            peak = Math.max(peak, Math.abs(s));
            sum += (double) s * s;
        }
        return peak + ", " + Math.round(Math.sqrt(sum / samples.length));
    }

    private static String span(short[] left, short[] right) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < left.length; i++) {
            if (Math.abs(left[i]) > 64 || Math.abs(right[i]) > 64) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        return first < 0 ? "silence" : first * 1000 / 44100 + " to " + last * 1000 / 44100;
    }

    // ------------------------------------------------------------------------------------------------- drawing

    private static BufferedImage waveforms(short[] tone, short[] ulaw) {
        return Snapshots.offscreen(WAVE_WIDTH, 2 * WAVE_HEIGHT + 10, g -> {
            prepare(g);
            // the first 20 ms (441 samples) : samples as a polyline, the u-law round trip over it
            frame(g, 0, "first 20 ms : samples (blue), u-law round trip (orange)");
            polyline(g, tone, 0, 441, 0, 0x1E88E5, 4f);
            polyline(g, ulaw, 0, 441, 0, 0xFB8C00, 1.2f);
            // the whole tone : min / max per column
            frame(g, WAVE_HEIGHT + 10, "whole tone (0.2 s) : min / max per pixel column");
            g.setColor(new Color(0x43A047));
            for (int x = 0; x < WAVE_WIDTH; x++) {
                int from = x * tone.length / WAVE_WIDTH;
                int to = Math.max(from + 1, (x + 1) * tone.length / WAVE_WIDTH);
                int min = 0;
                int max = 0;
                for (int i = from; i < to; i++) {
                    min = Math.min(min, tone[i]);
                    max = Math.max(max, tone[i]);
                }
                int mid = WAVE_HEIGHT + 10 + WAVE_HEIGHT / 2;
                g.drawLine(x, mid - max * (WAVE_HEIGHT / 2 - 4) / 32768, x, mid - min * (WAVE_HEIGHT / 2 - 4) / 32768);
            }
        });
    }

    private static BufferedImage envelope(short[] left, short[] right) {
        return Snapshots.offscreen(WAVE_WIDTH, 2 * WAVE_HEIGHT + 10, g -> {
            prepare(g);
            frame(g, 0, "left channel (melody panned left, bass panned right) : min / max per column, " + left.length
                    + " frames");
            frame(g, WAVE_HEIGHT + 10, "right channel");
            int[] colors = { 0x1E88E5, 0x8E24AA };
            short[][] channels = { left, right };
            for (int c = 0; c < 2; c++) {
                g.setColor(new Color(colors[c]));
                short[] samples = channels[c];
                int mid = c * (WAVE_HEIGHT + 10) + WAVE_HEIGHT / 2;
                for (int x = 0; x < WAVE_WIDTH; x++) {
                    int from = (int) ((long) x * samples.length / WAVE_WIDTH);
                    int to = (int) Math.max(from + 1, (long) (x + 1) * samples.length / WAVE_WIDTH);
                    int min = 0;
                    int max = 0;
                    for (int i = from; i < to; i++) {
                        min = Math.min(min, samples[i]);
                        max = Math.max(max, samples[i]);
                    }
                    g.drawLine(x, mid - max * (WAVE_HEIGHT / 2 - 4) / 32768, x, mid - min * (WAVE_HEIGHT / 2 - 4) / 32768);
                }
            }
        });
    }

    private static void prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static void frame(Graphics2D g, int y, String caption) {
        g.setColor(new Color(0xF7F9FC));
        g.fillRect(0, y, WAVE_WIDTH, WAVE_HEIGHT);
        g.setColor(new Color(0xCFD8DC));
        g.drawRect(0, y, WAVE_WIDTH - 1, WAVE_HEIGHT - 1);
        g.drawLine(0, y + WAVE_HEIGHT / 2, WAVE_WIDTH, y + WAVE_HEIGHT / 2);
        g.setColor(new Color(0x546E7A));
        g.setFont(new Font(Font.DIALOG, Font.PLAIN, 11));
        g.drawString(caption, 6, y + 14);
    }

    private static void polyline(Graphics2D g, short[] samples, int from, int count, int y, int rgb, float width) {
        Path2D.Float path = new Path2D.Float();
        for (int i = 0; i < count; i++) {
            float px = i * (WAVE_WIDTH - 1f) / (count - 1);
            float py = y + WAVE_HEIGHT / 2f - samples[from + i] * (WAVE_HEIGHT / 2f - 4) / 32768f;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        g.setColor(new Color(rgb));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
    }
}
