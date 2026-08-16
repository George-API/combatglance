package com.combatglance;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

/**
 * Soft synthesized metronome tick — smooth sine with a gentle envelope (not a mechanical click).
 * Playback goes through RuneLite {@link AudioPlayer} (Plugin Hub forbids direct javax.sound use).
 *
 * <p>Same sound-generation logic as TickFlow's {@code TickMetronome} (copied verbatim per
 * implementation brief §1 — approach *and* artifact here, since the whole point is an identical
 * sound). The difference is entirely in how it's controlled: TickFlow adds clickable mute/volume
 * buttons drawn directly on the overlay; Combat Glance deliberately does not — the overlay is
 * meant to stay a calm, passive readout (see overview §7.2), so both {@code tickSound} and
 * {@code tickSoundVolume} live only in the RuneLite config panel, same as every other setting.
 */
@Slf4j
@Singleton
class TickMetronome
{
	private static final float SAMPLE_RATE = 22050f;
	private static final double DURATION_SEC = 0.055;
	private static final double FREQ_HZ = 660.0;
	private static final double PEAK = 0.22;

	private final AudioPlayer audioPlayer;
	private final AtomicBoolean ready = new AtomicBoolean(false);
	private volatile byte[] pcmWav;
	private volatile float gainDb = -10f;

	@Inject
	TickMetronome(AudioPlayer audioPlayer)
	{
		this.audioPlayer = audioPlayer;
	}

	synchronized void start()
	{
		if (ready.get())
		{
			return;
		}
		pcmWav = buildSoftTickWav();
		ready.set(true);
	}

	synchronized void stop()
	{
		ready.set(false);
		pcmWav = null;
	}

	void setVolumePercent(int percent)
	{
		int clamped = Math.max(5, Math.min(100, percent));
		// Map 5..100% to roughly -24dB .. -2dB (quiet, but floor still audible).
		gainDb = -24f + (clamped - 5) * (22f / 95f);
	}

	void play()
	{
		byte[] wav = pcmWav;
		if (!ready.get() || wav == null)
		{
			return;
		}
		try
		{
			audioPlayer.play(new ByteArrayInputStream(wav), gainDb);
		}
		catch (Exception ex)
		{
			// AudioPlayer may throw IO / sound-line failures; keep tick path quiet.
			log.debug("Tick sound play failed", ex);
		}
	}

	/**
	 * Soft modern blip: sine tone with raised-cosine attack and exponential release.
	 */
	static byte[] buildSoftTickWav()
	{
		int sampleCount = (int) (SAMPLE_RATE * DURATION_SEC);
		byte[] pcm = new byte[sampleCount * 2];
		for (int i = 0; i < sampleCount; i++)
		{
			double t = i / SAMPLE_RATE;
			double env;
			double attack = 0.008;
			double releaseStart = DURATION_SEC * 0.35;
			if (t < attack)
			{
				// Smooth attack (raised cosine)
				env = 0.5 - 0.5 * Math.cos(Math.PI * (t / attack));
			}
			else if (t < releaseStart)
			{
				env = 1.0;
			}
			else
			{
				double u = (t - releaseStart) / (DURATION_SEC - releaseStart);
				env = Math.exp(-3.8 * u) * (1.0 - 0.15 * u);
			}

			// Primary soft tone + quiet lower harmonic for warmth (not buzzy).
			double sample = Math.sin(2 * Math.PI * FREQ_HZ * t) * 0.85
				+ Math.sin(2 * Math.PI * (FREQ_HZ * 0.5) * t) * 0.15;
			sample *= env * PEAK;

			short value = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(sample * Short.MAX_VALUE)));
			pcm[i * 2] = (byte) (value & 0xff);
			pcm[i * 2 + 1] = (byte) ((value >> 8) & 0xff);
		}
		return wrapWav(pcm, (int) SAMPLE_RATE);
	}

	private static byte[] wrapWav(byte[] pcm, int sampleRate)
	{
		int dataSize = pcm.length;
		byte[] wav = new byte[44 + dataSize];
		// RIFF header
		writeAscii(wav, 0, "RIFF");
		writeIntLE(wav, 4, 36 + dataSize);
		writeAscii(wav, 8, "WAVE");
		writeAscii(wav, 12, "fmt ");
		writeIntLE(wav, 16, 16);
		writeShortLE(wav, 20, (short) 1); // PCM
		writeShortLE(wav, 22, (short) 1); // mono
		writeIntLE(wav, 24, sampleRate);
		writeIntLE(wav, 28, sampleRate * 2);
		writeShortLE(wav, 32, (short) 2);
		writeShortLE(wav, 34, (short) 16);
		writeAscii(wav, 36, "data");
		writeIntLE(wav, 40, dataSize);
		System.arraycopy(pcm, 0, wav, 44, dataSize);
		return wav;
	}

	private static void writeAscii(byte[] out, int off, String text)
	{
		for (int i = 0; i < text.length(); i++)
		{
			out[off + i] = (byte) text.charAt(i);
		}
	}

	private static void writeIntLE(byte[] out, int off, int value)
	{
		out[off] = (byte) (value & 0xff);
		out[off + 1] = (byte) ((value >> 8) & 0xff);
		out[off + 2] = (byte) ((value >> 16) & 0xff);
		out[off + 3] = (byte) ((value >> 24) & 0xff);
	}

	private static void writeShortLE(byte[] out, int off, short value)
	{
		out[off] = (byte) (value & 0xff);
		out[off + 1] = (byte) ((value >> 8) & 0xff);
	}
}
