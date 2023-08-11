package net.rakjarz.corn

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ReversedLinesFileReader
constructor(
    file: File,
    private val blockSize: Int = 4096,
    private val encoding: Charset = Charset.defaultCharset()
) : Closeable {
    private val randomAccessFile: RandomAccessFile
    private val totalByteLength: Long
    private var totalBlockCount: Long = 0
    private val newLineSequences: Array<ByteArray>
    private val avoidNewlineSplitBufferSize: Int
    private var byteDecrement = 0
    private var currentFilePart: FilePart?
    private var trailingNewlineOfFileSkipped = false

    init {
        val charset = encoding
        val charsetEncoder = charset.newEncoder()
        val maxBytesPerChar = charsetEncoder.maxBytesPerChar()
        byteDecrement = if (maxBytesPerChar == 1f) {
            // all one byte encodings are no problem
            1
        } else if (charset === StandardCharsets.UTF_8) {
            // UTF-8 works fine out of the box, for multibyte sequences a second UTF-8 byte can never be a newline byte
            // http://en.wikipedia.org/wiki/UTF-8
            1
        } else if (// Same as for UTF-8
        // http://www.herongyang.com/Unicode/JIS-Shift-JIS-Encoding.html
        // Windows code page 932 (Japanese)
        // Windows code page 949 (Korean)
        // Windows code page 936 (Simplified Chinese)
            charset === Charset.forName("Shift_JIS") || charset === Charset.forName("windows-31j") || charset === Charset.forName(
                "x-windows-949"
            ) || charset === Charset.forName("gbk") || charset === Charset.forName("x-windows-950")
        ) { // Windows code page 950 (Traditional Chinese)
            1
        } else if (charset === StandardCharsets.UTF_16BE || charset === StandardCharsets.UTF_16LE) {
            // UTF-16 new line sequences are not allowed as second tuple of four byte sequences,
            // however byte order has to be specified
            2
        } else if (charset === StandardCharsets.UTF_16) {
            throw UnsupportedEncodingException(
                "For UTF-16, you need to specify the byte order (use UTF-16BE or " +
                        "UTF-16LE)"
            )
        } else {
            throw UnsupportedEncodingException(
                "Encoding " + encoding + " is not supported yet (feel free to " +
                        "submit a patch)"
            )
        }

        // NOTE: The new line sequences are matched in the order given, so it is important that \r\n is BEFORE \n
        newLineSequences = arrayOf(
            "\r\n".toByteArray(encoding), "\n".toByteArray(
                encoding
            ), "\r".toByteArray(encoding)
        )
        avoidNewlineSplitBufferSize = newLineSequences[0].size

        // Open file
        randomAccessFile = RandomAccessFile(file, "r")
        totalByteLength = randomAccessFile.length()
        var lastBlockLength = (totalByteLength % blockSize).toInt()
        if (lastBlockLength > 0) {
            totalBlockCount = totalByteLength / blockSize + 1
        } else {
            totalBlockCount = totalByteLength / blockSize
            if (totalByteLength > 0) {
                lastBlockLength = blockSize
            }
        }
        currentFilePart = FilePart(totalBlockCount, lastBlockLength, null)
    }

    /**
     * Returns the lines of the file from bottom to top.
     *
     * @return the next line or null if the start of the file is reached
     * @throws IOException  if an I/O error occurs
     */
    @Throws(IOException::class)
    fun readLine(): String? {
        var line = currentFilePart?.readLine()

        while (line == null) {
            currentFilePart = currentFilePart?.rollOver()
            line = if (currentFilePart != null) {
                currentFilePart!!.readLine()
            } else {
                // no more fileparts: we're done, leave line set to null
                break
            }
        }

        // aligned behaviour with BufferedReader that doesn't return a last, empty line
        if ("" == line && !trailingNewlineOfFileSkipped) {
            trailingNewlineOfFileSkipped = true
            line = readLine()
        }
        return line
    }

    /**
     * Closes underlying resources.
     *
     * @throws IOException  if an I/O error occurs
     */
    @Throws(IOException::class)
    override fun close() {
        randomAccessFile.close()
    }

    private inner class FilePart(
        private val no: Long,
        length: Int,
        leftOverOfLastFilePart: ByteArray?
    ) {
        private val data: ByteArray
        private var leftOver: ByteArray?
        private var currentLastBytePos: Int

        init {
            val dataLength = length + (leftOverOfLastFilePart?.size ?: 0)
            data = ByteArray(dataLength)
            val off = (no - 1) * blockSize

            // read data
            if (no > 0 /* file not empty */) {
                randomAccessFile.seek(off)
                val countRead = randomAccessFile.read(data, 0, length)
                check(countRead == length) { "Count of requested bytes and actually read bytes don't match" }
            }
            // copy left over part into data arr
            if (leftOverOfLastFilePart != null) {
                System.arraycopy(
                    leftOverOfLastFilePart,
                    0,
                    data,
                    length,
                    leftOverOfLastFilePart.size
                )
            }
            currentLastBytePos = data.size - 1
            leftOver = null
        }

        /**
         * Handles block rollover
         *
         * @return the new FilePart or null
         * @throws IOException if there was a problem reading the file
         */
        @Throws(IOException::class)
        fun rollOver(): FilePart? {
            check(currentLastBytePos <= -1) {
                ("Current currentLastCharPos unexpectedly positive... "
                        + "last readLine() should have returned something! currentLastCharPos=" + currentLastBytePos)
            }
            return if (no > 1) {
                FilePart(no - 1, blockSize, leftOver)
            } else {
                // NO 1 was the last FilePart, we're finished
                check(leftOver == null) {
                    ("Unexpected leftover of the last block: leftOverOfThisFilePart=" + leftOver?.toString(encoding))
                }
                null
            }
        }

        /**
         * Reads a line.
         *
         * @return the line or null
         * @throws IOException if there is an error reading from the file
         */
        @Throws(IOException::class)
        fun readLine(): String? {
            var line: String? = null
            var newLineMatchByteCount: Int
            val isLastFilePart = no == 1L
            var i = currentLastBytePos
            while (i > -1) {
                if (!isLastFilePart && i < avoidNewlineSplitBufferSize) {
                    // avoidNewlineSplitBuffer: for all except the last file part we
                    // take a few bytes to the next file part to avoid splitting of newlines
                    createLeftOver()
                    break // skip last few bytes and leave it to the next file part
                }

                // --- check for newline ---
                if (getNewLineMatchByteCount(data, i).also {
                        newLineMatchByteCount = it
                    } > 0 /* found newline */) {
                    val lineStart = i + 1
                    val lineLengthBytes = currentLastBytePos - lineStart + 1
                    check(lineLengthBytes >= 0) { "Unexpected negative line length=$lineLengthBytes" }
                    val lineData = ByteArray(lineLengthBytes)
                    System.arraycopy(data, lineStart, lineData, 0, lineLengthBytes)
                    line = lineData.toString(encoding)
                    currentLastBytePos = i - newLineMatchByteCount
                    break // found line
                }

                // --- move cursor ---
                i -= byteDecrement

                // --- end of file part handling ---
                if (i < 0) {
                    createLeftOver()
                    break // end of file part
                }
            }

            // --- last file part handling ---
            if (isLastFilePart && leftOver != null) {
                // there will be no line break anymore, this is the first line of the file
                line = leftOver?.toString(encoding)
                leftOver = null
            }
            return line
        }

        /**
         * Creates the buffer containing any left over bytes.
         */
        private fun createLeftOver() {
            val lineLengthBytes = currentLastBytePos + 1
            if (lineLengthBytes > 0) {
                // create left over for next block
                leftOver = ByteArray(lineLengthBytes)
                System.arraycopy(data, 0, leftOver!!, 0, lineLengthBytes)
            } else {
                leftOver = null
            }
            currentLastBytePos = -1
        }

        /**
         * Finds the new-line sequence and return its length.
         *
         * @param data buffer to scan
         * @param i start offset in buffer
         * @return length of newline sequence or 0 if none found
         */
        private fun getNewLineMatchByteCount(data: ByteArray, i: Int): Int {
            for (newLineSequence in newLineSequences) {
                var match = true
                for (j in newLineSequence.indices.reversed()) {
                    val k = i + j - (newLineSequence.size - 1)
                    match = match and (k >= 0 && data[k] == newLineSequence[j])
                }
                if (match) {
                    return newLineSequence.size
                }
            }
            return 0
        }
    }
}