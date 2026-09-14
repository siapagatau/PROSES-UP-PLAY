package com.kendang.realpads

import com.naman14.androidlame.LameBuilder
import java.io.OutputStream

// Ubah hasil rekaman (PCM stereo interleaved dari AudioEngine.nativeStopRecording())
// jadi file MP3 beneran, pake LAME encoder (via TAndroidLame). Selalu dipanggil dari
// background thread (Dispatchers.IO) karena walau cepat, ini tetap kerja CPU yang gak
// boleh nge-block UI thread buat rekaman yang agak panjang.
object Mp3Encoder {
    private const val SAMPLE_RATE = 48000
    private const val CHANNELS = 2
    private const val BITRATE_KBPS = 192
    private const val CHUNK_FRAMES = 8192 // 1 frame = 1 sample L + 1 sample R

    fun encodeToMp3(pcm: ShortArray, out: OutputStream) {
        if (pcm.isEmpty()) return

        val lame = LameBuilder()
            .setInSampleRate(SAMPLE_RATE)
            .setOutChannels(CHANNELS)
            .setOutSampleRate(SAMPLE_RATE)
            .setOutBitrate(BITRATE_KBPS)
            .setQuality(3)
            .build()

        // Ukuran buffer output yang direkomendasikan LAME: ~1.25x jumlah sample + 7200 byte,
        // dilebihin dikit lagi biar aman dari edge case.
        val mp3buf = ByteArray((7200 + CHUNK_FRAMES * CHANNELS * 1.25).toInt())
        val totalFrames = pcm.size / CHANNELS

        // Pakai encode(l, r, samples, mp3buf) -- bukan encodeBufferInterleaved -- karena
        // yang ini yang beneran ke-publish & kepake di AAR TAndroidLame:1.1 dari JitPack.
        // Jadi PCM interleaved (L,R,L,R,...) kita hasil rekaman perlu dipecah dulu ke
        // 2 array terpisah per chunk sebelum di-encode.
        val bufL = ShortArray(CHUNK_FRAMES)
        val bufR = ShortArray(CHUNK_FRAMES)

        var frame = 0
        while (frame < totalFrames) {
            val framesThisChunk = minOf(CHUNK_FRAMES, totalFrames - frame)
            for (i in 0 until framesThisChunk) {
                bufL[i] = pcm[(frame + i) * CHANNELS]
                bufR[i] = pcm[(frame + i) * CHANNELS + 1]
            }
            val bytesEncoded = lame.encode(bufL, bufR, framesThisChunk, mp3buf)
            if (bytesEncoded > 0) out.write(mp3buf, 0, bytesEncoded)
            frame += framesThisChunk
        }

        val flushed = lame.flush(mp3buf)
        if (flushed > 0) out.write(mp3buf, 0, flushed)
        lame.close()
    }
}
