/*-----------------------------------------------------------------------------
 *                 @@@           @@                  @@
 *                @@@@@          @@   @@             @@
 *                @@@@@          @@                  @@
 *       .@@@@@.  @@@@@      @@@ @@   @@     @@@@    @@     @@@       @@@
 *     @@@@@@   @@@@@@@    @@   @@@   @@        @@   @@   @@   @@   @@   @@
 *    @@@@@    @@@@@@@@    @@    @@   @@    @@@@@@   @@   @@   @@   @@   @@
 *   @@@@@@     @@@@@@@    @@    @@   @@   @@   @@   @@   @@   @@   @@   @@
 *   @@@@@@@@     @@@@@    @@   @@@   @@   @@   @@   @@   @@   @@   @@   @@
 *   @@@@@@@@@@@    @@@     @@@@ @@   @@    @@@@@    @@     @@@       @@@@@
 *    @@@@@@@@@@@  @@@@                                                  @@
 *     @@@@@@@@@@@@@@@@                                                  @@
 *       "@@@@@"  @@@@@    S  E  M  I  C  O  N  D  U  C  T  O  R     @@@@@
 *
 *
 * Copyright (C) 2014 Dialog Semiconductor GmbH and its Affiliates, unpublished
 * work. This computer program includes Confidential, Proprietary Information
 * and is a Trade Secret of Dialog Semiconductor GmbH and its Affiliates. All
 * use, disclosure, and/or  reproduction is prohibited unless authorized in
 * writing. All Rights Reserved.
 *
 * Filename: AudioGraph.java
 * Purpose : Visualize Audio Data
 * Created : 08-2014
 * By      : Johannes Steensma, Taronga Technology Inc.
 * Country : USA
 *
 *-----------------------------------------------------------------------------
 *
 * WavFile
 * Simple WavFile read/write. Simplified for 16Khz Mono PCM
 * https://ccrma.stanford.edu/courses/422/projects/WaveFormat/
 *
 * NOTE that this class supports Either READ or WRITE.
 *
 *-----------------------------------------------------------------------------
 */

package com.diasemi.bleremote.audio;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Created by johannes on 10/8/2014.
 */
public class WavFile {

    private final static String TAG = "WavFile";

    @SuppressWarnings("resource")
    private static void closeWaveHeader(final String fileName, final int nSamples) {
        int nbytes = nSamples * 2;
        try {
            RandomAccessFile f = new RandomAccessFile(fileName, "rw");
            f.seek(4);
            f.writeInt(nbytes + 36);
            f.seek(40);
            f.writeInt(nbytes);
            f.close();
        } catch (IOException e) {
            // TODO
            e.printStackTrace();
        }
    }

    private static byte[] getWavHeader() {
        ByteBuffer hdr = ByteBuffer.allocate(44);
        hdr.putInt(0x52494646); // RIFF
        hdr.putInt(0x24080000); // ChunkSize= nbytes+36 -> should be modified
                                // later..
        hdr.putInt(0x57415645); // WAVE
        hdr.putInt(0x666d7420); // fmt
        hdr.putInt(0x10000000); // Subchunksize=16
        hdr.putInt(0x01000100); // PCM, Mono
        hdr.putInt(0x803E0000); // Samplerate=16000 hz
        hdr.putInt(0x007D0000); // ByteRate=2*SampleRate*NChannels
        hdr.putInt(0x02001000); // BlockAlign, BitsPerSample,
        hdr.putInt(0x64617461); // data
        hdr.putInt(0x00080000); // SubChunk2size = nbytes -> should be modified
                                // later..
        return (hdr.array());
    }

    private String mCurrentFileName = null;
    private int mNbytes = 0;
    private FileOutputStream mWAVOutStream = null;
    private FileInputStream mWAVInStream = null;

    /**
     * Constructor
     */
    public WavFile() {
        //
    }

    public void close() {
        if (this.mWAVOutStream != null) {
            try {
                this.mWAVOutStream.close();
                closeWaveHeader(this.mCurrentFileName, this.mNbytes);
                Log.i(TAG, "Close wav file, nbytes is  " + this.mNbytes);
                this.mCurrentFileName = null;
            } catch (IOException e) {
                // TODO
                e.printStackTrace();
            }
            this.mWAVOutStream = null;
        }
        if (this.mWAVInStream != null) {
            try {
                this.mWAVInStream.close();
                this.mCurrentFileName = null;
                Log.i(TAG, "Close wav file, nbytes is  " + this.mNbytes);
            } catch (IOException e) {
                // TODO
                e.printStackTrace();
            }
            this.mWAVInStream = null;
        }
        this.mCurrentFileName = null;
    }

    public void openr(final String fileName) {
        if (fileName == null) {
            return;
        }
        try {
            if (this.mCurrentFileName != null) {
                close();
            }
            this.mCurrentFileName = fileName;
            this.mNbytes = 0; // Clear the buffer...
            this.mWAVInStream = new FileInputStream(fileName);
            byte[] wavHdr = new byte[44];
            this.mWAVInStream.read(wavHdr, 0, 44);
            Log.i(TAG, "Opened file for reading " + this.mCurrentFileName);
        } catch (IOException e) {
            // TODO
            e.printStackTrace();
        }
    }

    public void openw(final String fileName) {
        try {
            if (this.mCurrentFileName != null) {
                close();
            }
            this.mCurrentFileName = fileName;
            this.mNbytes = 0; // Clear the buffer...
            this.mWAVOutStream = new FileOutputStream(this.mCurrentFileName);
            byte[] wavHdr = getWavHeader();
            this.mWAVOutStream.write(wavHdr, 0, 44);
            Log.i(TAG, "Start received, Start collection, writing " + this.mCurrentFileName);
        } catch (IOException e) {
            // TODO
            e.printStackTrace();
        }
    }

    public int read(final byte[] data, final int length) {
        int res = 0;
        if (this.mWAVInStream != null) {
            try {
                res = this.mWAVInStream.read(data, 0, length);
            } catch (IOException e) {
                // TODO
                e.printStackTrace();
            }
        }
        return res;
    }

    public void write(final short[] values) {
        if (this.mWAVOutStream == null) {
            return;
        }
        int i;
        byte[] wavBuffer = new byte[values.length * 2];
        for (i = 0; i < values.length; i++) {
            wavBuffer[2 * i] = (byte) (values[i] & 0x00FF);
            wavBuffer[2 * i + 1] = (byte) ((values[i] >> 8) & 0x00FF);
        }
        this.mNbytes += 2 * values.length;
        try {
            this.mWAVOutStream.write(wavBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}