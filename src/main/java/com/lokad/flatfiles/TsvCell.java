package com.lokad.flatfiles;

import java.nio.charset.StandardCharsets;

/**
 * Represents a cell in a TSV file; used for error reporting.
 * 
 * 
 * Being part of the error reporting subsystem, this class is NOT optimized for
 * performance.
 */
public final class TsvCell
{
	/**
	 * The line on which the cell appears (zero-indexed).
	 */
	public int Line;

	/**
	 * The column on which the cell appears (zero-indexed).
	 */
	public int Column;

	/**
	 * If available, the name of the column. May be null.
	 */
	public String ColumnName;

	/**
	 * The contents of the cell, attempted as UTF8.
	 */
	public String Contents;


	public TsvCell(int line, int column, byte[] contents, String columnName)
	{
		Line = line;
		Column = column;
		Contents =  new String(contents, StandardCharsets.UTF_8);// c#  Encoding.UTF8.GetString(contents);
		ColumnName = columnName;
	}

	@Override
	public String toString()
	{
		return toString(true);
	}

	public String toString(boolean withValue)
	{
		if (ColumnName != null)
		{
			if (withValue)
			{
				return String.format("'%3$s' (column '%2$s', line %1$s)", Line + 1, ColumnName, Contents);
			}

			return String.format("Column '%2$s', line %1$s", Line + 1, ColumnName);
		}

		if (withValue)
		{
			return String.format("'%3$s' (column %2$s, line %1$s)", Line + 1, Column, Contents);
		}

		return String.format("Column %2$s, line %1$s", Line + 1, Column);
	}
}