package com.lokad.flatfiles;

/** Options passed to the {@link RawFlatFile} parser. */
public final class ParserOptions
{
	private int _maxLineCount = Integer.MAX_VALUE;
	private int _maxCellCount = Integer.MAX_VALUE;

	/**
	 * The maximum number of lines to be read from the input. Does not include
	 * the header.
	 */
	public int getMaxLineCount()
	{
		return _maxLineCount;
	}
	public void setMaxLineCount(int value)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException("MaxLineCount should be >= 0");
		}
		_maxLineCount = value;
	}

	/**
	 * The maximum number of cells to be read from the input. Does not include
	 * the header.
	 */
	public int getMaxCellCount()
	{
		return _maxCellCount;
	}
	public void setMaxCellCount(int value)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException("MaxCellCount should be >= 0");
		}
		_maxCellCount = value;
	}

	private int _readBufferSize = 100 * 1024 * 1024;

	/**
	 * The size of the buffer used for reading. Default is 100MB. If
	 * {@link #getMaxLineCount()} is set, a recommended value is 2KB + 1KB per
	 * line.
	 */
	public int getReadBufferSize()
	{
		return _readBufferSize;
	}
	public void setReadBufferSize(int value)
	{
		if (value < 4096)
		{
			throw new IllegalArgumentException("ReadBufferSize should be >= 4096");
		}
		_readBufferSize = value;
	}
}