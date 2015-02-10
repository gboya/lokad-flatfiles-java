package com.lokad.flatfiles;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/** 
 Stores a buffer of bytes read from the provided stream. 
 
 
 Enables high-throughput operations on the data by reducing
 the frequency of context switches required to read more
 data from the stream. 
 
*/
public final class InputBuffer
{
	/** 
	 The bytes read from the stream. Only bytes between <see cref="Start"/>
	 and <see cref="End"/> are valid.
	*/
	public byte[] Bytes;

	/** 
	 The index of the first valid byte inside <see cref="Bytes"/>.
	 The user of this class is explicitly allowed to increment this value
	 as more bytes are read.
	*/
	private int Start;
	public int getStart()
	{
		return Start;
	}
	public void setStart(int value)
	{
		Start = value;
	}

	/** 
	 The first invalid byte after <see cref="Start"/>.
	*/
	private int End;
	public int getEnd()
	{
		return End;
	}
	private void setEnd(int value)
	{
		End = value;
	}

	/** 
	 The number of valid bytes in <see cref="Bytes"/>.
	*/
	public int getLength()
	{
		return getEnd() - getStart();
	}

	/** 
	 True if the end of the input stream was reached and <see cref="Refill"/>
	 will not be able to read more bytes.
	*/
	private boolean AtEndOfStream;
	public boolean getAtEndOfStream()
	{
		return AtEndOfStream;
	}
	private void setAtEndOfStream(boolean value)
	{
		AtEndOfStream = value;
	}

	/** 
	 True if <see cref="Refill"/> has no effect, either because there is no
	 more data left in the stream or because there is no room in the buffer.
	*/
	public boolean getIsFull()
	{
		return getLength() == Bytes.length || getAtEndOfStream();
	}

	/** 
	 If a file encoding could be determined by reading the BOM (if any),
	 then the found encoding is stored here.
	 
	 
	 If set, then the buffer will always be encoded as UTF8 (this class
	 takes care of decoding).
	 
	*/
	public Charset FileEncoding;

	/** 
	 The stream from which data will be read.
	*/
	private InputStream _source;

	public InputBuffer(int size, InputStream input)
	{
		if (size < 4)
		{
			throw new IllegalArgumentException("InputBuffer size '" + size + "' is too small.");
		}

		Bytes = new byte[size];

		// Detect UTF-16 encodings or an UTF-8 BOM.
		// ========================================

		try {
			setEnd(input.read(Bytes, 0, 2));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		if (getEnd() == 2)
		{
			// Note: if a reencoding stream is used, the output data will be guaranteed to 
			// be UTF-8, but a performance hit will be incurred because of the translation
			// that takes place on every read. 

			if (Bytes[0] == 0xFF && Bytes[1] == 0xFE)
			{
				// little-endian UTF-16 encoding
				input = new ReencodingStream(input, StandardCharsets.UTF_16LE);

				setEnd(0);
			}
			else if (Bytes[0] == 0xFE && Bytes[1] == 0xFF)
			{
				// big-endian UTF-16 encoding
				input = new ReencodingStream(input, StandardCharsets.UTF_16BE);

				setEnd(0);
			}
			else if (Bytes[0] == 0xEF && Bytes[1] == 0xBB)
			{
				try {
					setEnd(getEnd() + input.read(Bytes, 2, 1));
				} catch (IOException e) {
					e.printStackTrace();
				}

				// Drop the UTF-8 BOM sequence "EF BB BF"
				if (getEnd() == 3 && Bytes[2] == 0xBF)
				{
					setEnd(0);
					FileEncoding = StandardCharsets.UTF_8;
				}
			}
		}

		_source = input;

		Refill();
	}

	/** 
	 Sets <see cref="Start"/> to 0, preserving both <see cref="Length"/>
	 and the segment of <see cref="Bytes"/> between <see cref="Start"/>
	 and <see cref="End"/>.
	*/
	private void MoveDataToFront()
	{
		if (getStart() == 0)
		{
			return;
		}
		if (getStart() == getEnd())
		{
			setStart(0);
			setEnd(0);
			return;
		}

		System.arraycopy(Bytes, getStart(), Bytes, 0, getLength());

		setEnd(getEnd() - getStart());
		setStart(0);
	}

	/** 
	 Read enough data to fill the entire buffer, without
	 discarding bytes between <see cref="Start"/> and <see cref="End"/>.
	*/
	public void Refill()
	{
		MoveDataToFront();
		while (Bytes.length > getEnd() && !getAtEndOfStream())
		{
			int length = Bytes.length - getEnd();
			int count = 0;
			try {
				count = _source.read(Bytes, getEnd(), length);
			} catch (IOException e) {
				e.printStackTrace();
			}
			setEnd(getEnd() + count);

			setAtEndOfStream((count < 0));
		}
	}

	/** 
	 A read-only stream used to convert from an UTF-16 text encoding to UTF-8.
	 Does not support writing or seeking. 
	*/
	private final static class ReencodingStream extends InputStream
	{
		/**  The underlying stream from which data is read. 
		*/
		private InputStream _stream;

		/**  The number of bytes to be read on each iteration. 
		*/
		private static final int ReadSize = 4096;

		/**  A buffer used for translation. 
		*/
		private final byte[] _buffer = new byte[2 * ReadSize];

		private int _bufferEnd;
		private int _bufferStart;

		/**  The encoding from which data is read. 
		*/
		private Charset _encoding;

		public ReencodingStream(InputStream input, Charset source)
		{
			_stream = input;
			_encoding = source;
		}

		@Override
		public int read(byte[] buffer, int offset, int count)
		{
			int oldCount = count;

			// First, send any data remaining in the buffer.
			if (_bufferEnd > _bufferStart)
			{
				byte length = (byte) Math.min(count, _bufferEnd - _bufferStart);
				System.arraycopy(_buffer, _bufferStart, buffer, offset, length);
				_bufferStart += length;
				offset += length;
				count -= length;
			}

			if (count == 0)
			{
				return oldCount;
			}

			// If we are still here, then the buffer is empty. It will be empty
			// every time this loop starts. 
			while (true)
			{
				// Read data in the input encoding into the buffer
				// ===============================================

				try {
					_bufferEnd = _stream.read(_buffer, 0, ReadSize);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if (_bufferEnd == 0)
				{
					return oldCount - count;
				}

				// Decode and re-encode into the buffer
				// ====================================

				ByteBuffer byteBuffer = ByteBuffer.wrap(_buffer, 0, _bufferEnd);
				String decoded = _encoding.decode(byteBuffer).toString();
				//String decoded = _encoding.GetString(_buffer, 0, _bufferEnd); 

				_bufferEnd = _encoding.encode(decoded).capacity();
				//_bufferEnd = Encoding.UTF8.GetBytes(decoded, 0, decoded.length(), _buffer, 0);

				// Copy a portion to the output
				// ============================

				byte length =(byte) Math.min(count, _bufferEnd);
				System.arraycopy(_buffer, 0, buffer, offset, length);

				_bufferStart = length;
				offset += length;
				count -= length;

				if (count == 0)
				{
					return oldCount;
				}
			}
		}

		@Override
		public int read() throws IOException {
			throw new UnsupportedOperationException("just use read(byte[] buffer, int offset, int count)");
		}

	}
}