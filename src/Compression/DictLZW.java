package Compression;

import java.io.Serializable;
import java.util.Map;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Base64;
import java.lang.StringBuilder;
import java.lang.Math;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.nio.ByteBuffer;
import Compression.AbstractCompressor;

/**
 * LWZ dictionary compressing
 * @author Hang Yuan
 */
public class DictLZW extends AbstractCompressor {

    /**
     * dictionary record
     */
    class DictRecord {
        int encode;
        int len;
        String word = null;
        public DictRecord(int encode, int len, String word) {
            this.encode = encode;
            this.len = len;
            this.word = word;
        }
    }

    /**
     * constructor
     * @param fileName file to be compressed
     */
    public DictLZW(String pathName) {
        super(pathName, "lzw");
    }

    /**
     * compress a file
     * | original size | encode length |encoded bytes |
     * |   8 bytes     |    4 byte     |   ... ...    |
     * @return encoded size
     */
    @Override
    public long compress() {
        if (mode() != AbstractCompressor.Mode.Compress) {
            System.err.println("Error: Not in Compress mode");
            return 0;
        }

        // begin compressing
        System.out.println("Start Compressing ...");
        File file = new File(fileName);
        try {
            // start encoding
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(fileName));

            // read the orginal file and build the dictionary
            HashMap<String, DictRecord> map = new HashMap<String, DictRecord>();
            ArrayList<Integer> output = new ArrayList<Integer>();
            StringBuilder word = new StringBuilder();
            int nextNo = 255;
            byte nextByte = (byte)ins.read();
            word.append((char)nextByte);
            //System.out.println("read byte: " + (byte)nextByte);
            for (int i = 0; i < fileSize; i++) {
                byte crtByte = nextByte;
                // process successive bytes
                if (i < fileSize - 1) {
                    nextByte = (byte)ins.read();
                    //System.out.println("read byte: " + (byte)nextByte);
                    String oldWord = word.toString();
                    word.append((char)nextByte);
                    if (!map.containsKey(word.toString())) {
                        // write word to output
                        if (oldWord.length() == 1) {
                            output.add(((int)crtByte)&0xFF);
                        }
                        else {
                            output.add(map.get(oldWord).encode);
                        }

                        // add current word as a new record into dictionary
                        map.put(word.toString(), 
                                new DictRecord(++nextNo, word.length(), word.toString()));
                        // System.out.printf("adding %d, {", nextNo);
                        // for (char b: word.toString().toCharArray()) {
                        //     System.out.printf("%d ", (byte)b);
                        // }
                        // System.out.println("\b}");
                        word.setLength(0);
                        word.append((char)nextByte);
                    }
                }
                else {
                    // reach EOF
                    if (word.length() == 1) {
                        output.add(((int)crtByte)&0xFF);
                    }
                    else {
                        output.add(map.get(word.toString()).encode);
                    }
                }
            }

            // begin writing zip file
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(zipFileName));

            // compute encode word length
            int wordLen = (int)Math.ceil(Math.log(map.size() + 256) / Math.log(2));
            wordLen = (1 + (wordLen - 1) / 4) * 4;

            /* | original size | encode length |encoded bytes |
             * |   8 bytes     |    4 byte     |   ... ...    |*/
            WordStream ws = new WordStream(wordLen, outs);
            // write original file size
            ws.dumpLong(fileSize);
            // write word length
            ws.dumpInt(wordLen);
            // dump encoded bytes
            ws.dumpBytes(output);

            compressedSize = (int)(output.size() * wordLen / 8.0);
            System.out.println("wordLen = " + wordLen);
            System.out.println("Compressed Size = " + compressedSize);
            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }

        // go to decompress mode
        setDecompressMode();
        setCompressRate(((double)compressedSize) / fileSize);
        System.out.println("Done: Compression Rate = " + 
                           String.format("%.2f", getCompressRate()*100) + "%\n");
        return compressedSize;
    }

    /**
     * decompress file
     * @return original file size
     */
    @Override
    public long decompress() {
        // check file ending with ".huf"
        if (mode() != AbstractCompressor.Mode.Decompress) {
            System.err.println("Error: Not in Decompress mode");
            return 0;
        }

        try {
            System.out.println("Start Decompressing ...");
            BufferedInputStream ins = 
                new BufferedInputStream(new FileInputStream(zipFileName));

            WordStream ws = new WordStream(0, ins);

            // read original file size
            fileSize = ws.readLong();
            File file = new File(zipFileName);
            compressedSize = file.length();
            setCompressRate(((double)compressedSize) / fileSize);

            // read encode length
            int wordLen = ws.readInt();
            ws.setWordLen(wordLen);
            
            // output: decompressed file
            BufferedOutputStream outs = 
                new BufferedOutputStream(new FileOutputStream(fileName + ".out"));

            // byte encoder and decoder
            Base64.Encoder encoder = Base64.getEncoder();
            Base64.Decoder decoder = Base64.getDecoder();

            // initialize byte count and prob distribution
            int crtSize = 0;
            ArrayList<DictRecord> table = new ArrayList<DictRecord>();
            byte[] lastEntry = null;
            
            while (crtSize < fileSize) {
                int encode = ws.readNextWord();
                //System.out.println("\ndumping: " + encode);

                byte[] entry = null;
                if (encode < 256) {
                    outs.write((byte)encode);
                    //System.out.println("writing: " + (byte)encode);
                    entry = new byte[1];
                    entry[0] = (byte)encode;
                    crtSize++;
                }
                else {
                    if (encode - 256 >= table.size()) {
                        entry = new byte[lastEntry.length + 1];
                        System.arraycopy(lastEntry, 0, entry, 0, lastEntry.length);
                        entry[lastEntry.length] = lastEntry[0];
                    }
                    else {
                        entry = decoder.decode(table.get(encode - 256).word.getBytes());
                    }
                    // for (byte b: entry) {
                    //     System.out.println("writing: " + b);
                    // }
                    outs.write(entry, 0, entry.length);
                    crtSize += entry.length;
                }
                if (lastEntry != null) {
                    // add (lastEntry + entry[0]) to dict
                    byte[] newWord = new byte[lastEntry.length + 1];
                    System.arraycopy(lastEntry, 0, newWord, 0, lastEntry.length);
                    newWord[lastEntry.length] = entry[0];
                    // System.out.printf("adding %d, {", table.size() + 256);
                    // for (byte b: newWord) {
                    //     System.out.printf("%d ", (byte)b);
                    // }
                    // System.out.println("\b}");
                    table.add(new DictRecord(table.size(), newWord.length, 
                                             new String(encoder.encode(newWord))) );
                }
                lastEntry = entry;
            }
            

            ins.close();
            outs.close();
        }
        catch (IOException e) {
           e.printStackTrace();
        }

        System.out.println("Done: Decompressed File Size = " + fileSize + " bytes");
        return fileSize;
    }
    

    /**
     *  WordStream: to read and write to file with word of given length
     */
    class WordStream {
        
        int wordLen; // word length must be a multiple of 4
        OutputStream outs;
        InputStream ins;

        int index;
        byte crtByte;
        int mask;

        public WordStream(int wordLen, OutputStream outs) {
            this.wordLen = wordLen;
            this.outs = outs;
            reset();
        }

        public WordStream(int wordLen, InputStream ins) {
            this.wordLen = wordLen;
            this.ins = ins;
            reset();
        }

        public void reset() {
            index = 0;
            crtByte = 0;
            mask = (1<<wordLen) - 1;
        }

        /**
         * dump a long int
         */
        public void dumpLong(long num) throws IOException {
            ByteBuffer bBuf = ByteBuffer.allocate(8);
            bBuf.putLong(num); // orignal file size
            outs.write(bBuf.array(), 0, 8);
        }

        /**
         * dump an int
         */
        public void dumpInt(int num) throws IOException {
            ByteBuffer bBuf = ByteBuffer.allocate(4);
            bBuf.putInt(num); // orignal file size
            outs.write(bBuf.array(), 0, 4);
        }

        /**
         * read a long int
         */
        public long readLong() throws IOException {
            byte[] barray = new byte[8];
            ins.read(barray, 0, 8);
            return ByteBuffer.wrap(barray).getLong();
        }

        /**
         * read an int
         */
        public int readInt() throws IOException {
            byte[] barray = new byte[4];
            ins.read(barray, 0, 4);
            return ByteBuffer.wrap(barray).getInt();
        }

        /**
         * dump output as bytes with given word length
         */
        public void dumpNextWord(int code) throws IOException
        {
            //System.out.println("\ndumping: " + code);
            if (wordLen%8 == 0) {
                for (int i = 0; i < wordLen / 8; i++) {
                    crtByte = (byte)((code>>(i*8))&0xFF);
                    outs.write(crtByte);
                    //System.out.println("1 writing: " + crtByte);
                }
            }
            else {
                if (index == 4) {
                    crtByte = (byte)(crtByte | ((byte)((code&0xF)<<4)));
                    outs.write(crtByte);
                    //System.out.println("2 writing: " + crtByte);
                    for (int i = 0; i < wordLen / 8; i++) {
                        crtByte = (byte)((code>>(i*8+index))&0xFF);
                        outs.write(crtByte);
                        //System.out.println("3 writing: " + crtByte);
                    }
                    index = 0;
                }
                else {
                    int i = 0;
                    for (i = 0; i < wordLen / 8; i++) {
                        crtByte = (byte)((code>>(i*8+index))&0xFF);
                        outs.write(crtByte);
                        //System.out.println("3 writing: " + crtByte);
                    }
                    crtByte = (byte)((code>>(i*8))&0xF);
                    //System.out.println("2 crtByte = " + crtByte);
                    index = 4;
                }
            }
        }

        public void finishDump() throws IOException {
            if (index == 4) {
                //System.out.println("4 writing: " + crtByte);
                outs.write(crtByte);
            }
        }

        public void dumpBytes(ArrayList<Integer> output) throws IOException {
            for (Integer code: output) {
                dumpNextWord(code);
            }
            finishDump();
            reset();
        }

        /**
         *  read next word from input stream
         */
        public int readNextWord() {
            try{
                return readWord();
            }
            catch (IOException e) {
               e.printStackTrace();
            }
            return -1;
        }

        private int readWord() throws IOException {
            int encode = 0;
            if (wordLen%8 == 0) {
                encode = 0;
                for (int i = 0; i < wordLen/8; i++) {
                    crtByte = (byte)ins.read();
                    encode = encode | ((((int)crtByte)&0xFF)<<(i*8));
                }
            }
            else {
                if (index == 0) {
                    int i = 0;
                    encode = 0;
                    for (i = 0; i < wordLen/8; i++) {
                        crtByte = (byte)ins.read();
                        encode = encode | ((((int)crtByte)&0xFF)<<(i*8+index));
                    }
                    crtByte = (byte)ins.read();
                    encode = encode | ((((int)crtByte)&0xF)<<(i*8));
                    index = 4;
                }
                else {
                    encode = (((int)crtByte)>>4)&0xF;
                    for (int i = 0; i < wordLen/8; i++) {
                        crtByte = (byte)ins.read();
                        encode = encode | ((((int)crtByte)&0xFF)<<(i*8+index));
                    }
                    index = 0;
                }
            }
            return encode;
        }

        public void setWordLen(int wordLen) {
            this.wordLen = wordLen;
        }
    }

}