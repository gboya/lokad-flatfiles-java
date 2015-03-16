package com.lokad.flatfiles;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/** 
 Reads a flat file into a compact in-memory representation.
*/
public final class RawFlatFile
{
	/** 
	 The number of columns in this file.
	*/
	public int Columns;

	/** 
	 A matrix of cells. Line X, column Y can be found at index (X * Columns + Y).
	 The actual contents of a cell are found in <see cref="Content"/>.
	*/
	public List<Integer> getCells()
	{
		return Collections.unmodifiableList(_cells);
	}

	/** <see cref="Cells"/>
	  Only mutable during parsing. 
	*/
	private IntArrayList _cells = new IntArrayList();

	/** 
	 A list of non-empty cells that were beyond the last column on a line.
	*/
	public List<TsvCell> getUnexpectedCells()
	{
		return Collections.unmodifiableList(_unexpectedCells);
	}

	/** <see cref="UnexpectedCells"/>
	  Only mutable during parsing. 
	*/
	private final List<TsvCell> _unexpectedCells = new ArrayList<TsvCell>();

	/** 
	 The byte contents of the cells referenced by <see cref="Cells"/>.
	*/
	protected List<byte[]> Content;

	/** 
	 The separator used for parsing the input file. 
	*/
	public byte Separator;

	/**  Was the file truncated ? 
	  
	 Occurs if <see cref="ParserOptions.MaxCellCount"/> or
	 <see cref="ParserOptions.MaxLineCount"/> caused data to be discarded.
	 
	*/
	public boolean IsTruncated;

	/** 
	 The encoding that was actually found in the file. Not all encodings can be
	 detected by this class, so this value may be null.
	 
	 
	 If a file encoding was detected, the <see cref="Content"/> cells will have 
	 been re-encoded to UTF-8.
	 
	*/
	public Charset FileEncoding;

	/**  The trie used to compute the int-to-byte[] mapping. 
	  Only used during parsing, nulled afterwards. 
	*/
	private Trie _trie;

	/** 
	 Attempt to guess the separator by reading the first line of the buffer.
	 
	  Called during parsing only. 
	*/
	private static byte GuessSeparator(InputBuffer buffer, tangible.RefObject<Integer> columns)
	{
		final byte lf = 0x0A; // \n
		final byte cr = 0x0D; // \r
		final byte space = 0x20; // whitespace

		// Skip to the first non-whitespace, non-newline character (if any)

		for (int i = buffer.getStart(); i < buffer.getEnd(); ++i)
		{
			byte b = buffer.Bytes[i];
			if (b == lf || b == cr || b == space)
			{
				continue;
			}

			buffer.setStart(i);
			break;
		}

		// Count the number of occurences of each candidate on the first line

		byte[] candidates = new byte[] {0x09, 0x3B, 0x2C, 0x7C, 0x20};

		int[] counts = new int[candidates.length];

		for (int i = buffer.getStart(); i < buffer.getEnd(); ++i)
		{
			byte b = buffer.Bytes[i];

			if (b == lf || b == cr)
			{
				break;
			}

			for (int c = 0; c < candidates.length; ++c)
			{
				if (candidates[c] == b)
				{
					++counts[c];
				}
			}
		}

		// Determine the first candidate (in order of priority defined above)
		// that appeared

		for (int c = 0; c < candidates.length; ++c)
		{
			if (counts[c] > 0)
			{
				columns.argValue = counts[c] + 1;
				return candidates[c];
			}
		}

		// If no candidate is found, assume 'tab'... warnings will be issued later.

		columns.argValue = 1;
		return 0x09;
	}

	/**  Create a raw flat file from external values. 
	 
	 
	 No consistency checks are performed. 
	 You may call <see cref="ThrowIfInconsistent"/> yourself.
	 
	 For performance reasons, <paramref name="cells"/> and <paramref name="content"/>
	 are not copied.
	 
	 
	*/
	public RawFlatFile(int columns, java.util.ArrayList<Integer> cells, List<byte[]> content)
	{
		Columns = columns;
		_cells = new IntArrayList(cells);
		Content = content;

		// Use default values for diagnosis fields
		FileEncoding = null;
		_trie = null;
		Separator = 0x09;
		IsTruncated = false;
	}

	/**  Throws if the internal state is inconsistent.         
	 
	 The parsing constructor will never lead to an inconsistent state.
	 Manually calling the <see cref="RawFlatFile(int, List{int}, IReadOnlyList{byte[]})"/>
	 constructor, however, may cause inconsistency unless special care is taken
	 to ensure the following invariants: 
	 
	  - all values in <see name="Cells"/> are valid indices into <see name="Content"/>
	  - cell 0 in <see name="Content"/> is <c>new byte[0]</c>
	  - length of <see name="Cells"/> is a multiple of <see name="Columns"/>
	  - a value X > 0 may only appear in <see cref="Cells"/> at a higher index than value X-1.
	 
	*/
	public void ThrowIfInconsistent()
	{
		if (Content.get(0).length != 0)
		{
			throw new RuntimeException("Content[0] should be a new byte[0].");
		}

		if (Columns == 0)
		{
			if (getCells().size() > 0)
			{
				throw new RuntimeException("No cells allowed if Columns = 0");
			}

			if (Content.size() > 1) // '1' here because of Content[0] == new byte[0]
			{
				throw new RuntimeException("No content allowed if Columns == 0");
			}

			return;
		}

		if (getCells().size() % Columns != 0)
		{
			throw new RuntimeException(String.format("Cells.Count = %1$s should be a multiple of Columns = %2$s.", getCells().size(), Columns));
		}

		int nextNew = 1;
		for (int i = 0; i < getCells().size(); ++i)
		{
			int cell = getCells().get(i);

			if (cell < 0)
			{
				throw new RuntimeException(String.format("Cells[%1$s] = %2$s < 0.", i, cell));
			}

			if (cell > nextNew)
			{
				throw new RuntimeException(String.format("Cells[%1$s] = %2$s when %3$s has not appeared yet.", i, cell, nextNew));
			}

			if (cell == nextNew)
			{
				nextNew++;
				if (cell >= Content.size())
				{
					throw new RuntimeException(String.format("Cells[%1$s] = %2$s >= Content.Count = %3$s.", i, cell, Content.size()));
				}
			}
		}
	}

	/**  Parses the input file with the provided options. 
	*/

	public RawFlatFile(InputStream file)
	{
		this(file, null);
	}

	public RawFlatFile(InputStream file, ParserOptions options)
	{
		options = (options != null) ? options : new ParserOptions();

		_trie = new Trie();

		int bufferSize = options.getReadBufferSize();

		final byte quote = 0x22; // "
		final byte lf = 0x0A; // \n
		final byte cr = 0x0D; // \r

		// Load a bunch of source data into a large buffer

		InputBuffer buffer = new InputBuffer(bufferSize, file);
		byte[] bytes = buffer.Bytes;

		FileEncoding = buffer.FileEncoding;

		// If separator is space, the actual separator should be a tab
		// (it means the data provided used whitespace for headers mistakenly).
		// In that case, use the guessed separator for the first line, then
		// revert to tabs.

		tangible.RefObject<Integer> tempRef_Columns = new tangible.RefObject<Integer>(Columns);
		byte separator = GuessSeparator(buffer, tempRef_Columns);
		Columns = tempRef_Columns.argValue;
		SpaceSeparatedHeaders = separator == 0x20;
		Separator = SpaceSeparatedHeaders ? (byte)0x09 : separator;

		int maxCellCountFromLines = options.getMaxLineCount() >= Integer.MAX_VALUE / Columns - 1 ? Integer.MAX_VALUE : Columns * (options.getMaxLineCount() + 1); // Include header line

		int maxCellCount = options.getMaxCellCount() >= Integer.MAX_VALUE - Columns ? Integer.MAX_VALUE : options.getMaxCellCount() + Columns; //Include header line

		maxCellCount = Math.min(maxCellCount, maxCellCountFromLines);

		// Each iteration of this loop attempts to read one cell starting at buffer.Start            
		// It ends when there is no more data available in the buffer or the max cell 
		// count is reached.       
		while ((!buffer.getAtEndOfStream() || buffer.getLength() > 0) && _cells.size() < maxCellCount)
		{
			boolean inQuote = false;
			int nQuotes = 0; // The number of opening quotes in the cell

			// This loop scans the stream forward, looking for a cell terminator.
			for (int i = buffer.getStart(); ; ++i)
			{
				if (i >= buffer.getEnd())
				{
					// We have reached the end of the buffer. 
					// The typical behaviour is to abort reading this token and refill the buffer.
					// BUT if the buffer is already filled, we are dealing with a token that is
					// way too long: read it. 
					if (buffer.getIsFull())
					{
						ExtractCell(bytes, buffer.getStart(), buffer.getEnd(), nQuotes);

						buffer.setStart(buffer.getEnd());
					}

					buffer.Refill();

					break;
				}

				byte b = bytes[i];

				// Quote management
				if (b == quote)
				{
					if (i == buffer.getStart())
					{
						++nQuotes;
						inQuote = true;
					}
					else if (inQuote)
					{
						if (i < buffer.getEnd() && bytes[i + 1] == quote)
						{
							++i;
							++nQuotes;
						}
						else
						{
							inQuote = false;
						}
					}
				}

				if (inQuote)
				{
					continue;
				}

				// End of line
				if (b == cr || b == lf)
				{
					ExtractCell(bytes, buffer.getStart(), i, nQuotes);
					EndLine();

					separator = Separator;

					buffer.setStart(i + 1);
					break;
				}

				// Separators
				if (b == separator)
				{
					ExtractCell(bytes, buffer.getStart(), i, nQuotes);
					buffer.setStart(i + 1);
					break;
				}
			}
		}

		// Just in case no "endline" was found, end the line
		EndLine();

		// If the file was empty, fix the number of columns
		if (_cells.isEmpty())
		{
			Columns = 0;
		}

		IsTruncated = (_cells.size() >= maxCellCount);

		Content = _trie.Values;

		// Drop the trie: we don't want to keep the memory contents around
		// so we let the GC take care of it.
		_trie = null;
	}

	/** 
	 Extracts a cell reference, inserts it into the cell matrix
	 while keeping track of line sizes and end-of-lines.
	 
	  Called during parsing only. 
	*/
	private void ExtractCell(byte[] source, int start, int end, int nQuotes)
	{

		final byte space = 0x20; // whitespace
		final byte quote = 0x22; // "

		if (nQuotes > 0)
		{
			// Only treat a cell as quoted if the last character is the 
			// closing quote. Otherwise, it's an ill-formatted quote that
			// should be treated as non-quoted.
			if (source[end - 1] == quote)
			{
				start++;
				end--;

				// If inner quotes are present, the trie will choke on the buffer
				// (because double quotes must turn to single quotes), so we 
				// rewrite the cell in-memory.
				if (nQuotes > 1)
				{
					int j = start;

					// Skip to after the first double-quote...
					while (source[j] != quote)
					{
						j++;
					}
					j++;

					// ... and start copying
					for (int i = j + 1; i < end; ++i, ++j)
					{
						source[j] = source[i];
						if (source[i] == quote)
						{
							++i;
						}
					}

					end = j;
				}
			}
		}

		while (start < end && source[start] == space)
		{
			++start;
		}
		while (start < end && source[end - 1] == space)
		{
			--end;
		}

		int cell = _trie.Hash(source, start, end);

		if (cell == 0)
		{
			if (_lineSize == 0)
			{
				++_emptyCellsSinceLineStart;
			}
			else
			{
				if (_lineSize < Columns)
				{
					_cells.add(0);
				}
				++_lineSize;
			}
		}
		else
		{
			while (_emptyCellsSinceLineStart-- > 0)
			{
				if (_lineSize < Columns)
				{
					_cells.add(0);
				}
				++_lineSize;
			}

			if (_lineSize < Columns)
			{
				_cells.add(cell);
			}
			else
			{
				_unexpectedCells.add(new TsvCell((_cells.size() / Columns) - 1, _lineSize, _trie.Values.get(cell), null));
			}

			++_lineSize;
		}
	}

	/** 
	 End the current line. If not all columns have values,
	 adds empty cells to fill the line. If the line only contains
	 empty cells, it is discarded.
	 
	  Called during parsing only. 
	*/
	private void EndLine()
	{
		if (_lineSize > 0)
		{
			while (_lineSize++ < Columns)
			{
				_cells.add(0);
			}
		}

		_lineSize = 0;
		_emptyCellsSinceLineStart = 0;
	}

	/** 
	 The length of the *unbroken* empty cell streak since
	 the last call to <see cref="EndLine"/> (or the start of processing).
	 Zero if a non-empty cell was encountered.
	 
	  Used during parsing only. 
	*/
	private int _emptyCellsSinceLineStart;

	/** 
	 The number of cells on the current line, assuming that there is at least
	 one non-empty cell found so far.
	 
	  Used during parsing only. 
	*/
	private int _lineSize;

	/**  The number of lines, including the header. 
	*/
	public int getLines()
	{
		return Columns == 0 ? 0 : _cells.size() / Columns;
	}

	/**  The number of lines, not counting the header. 
	*/
	public int getContentLines()
	{
		return Math.max(0, getLines() - 1);
	}

	/**  Returns the bytes in the specified cell. 
	*/
	public byte[] getItem(int line, int column)
	{
		return Content.get(_cells.getInt(line * Columns + column));
	}

	/** 
	 Were headers separated by whitespace (0x20) instead of <see cref="Separator"/> ? 
	*/
	public boolean SpaceSeparatedHeaders;

	/**  The maximum number of bytes allowed in a cell. 
	*/
	public static final int MaximalValueLength = 4096;
}