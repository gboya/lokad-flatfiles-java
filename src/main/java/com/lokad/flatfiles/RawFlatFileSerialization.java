package com.lokad.flatfiles;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public final class RawFlatFileSerialization
{

	private static final byte VersionNumber = 1;
	
	
	/**  Writes a raw flat-file to the output. 
	  
	 Assumes that <see cref="RawFlatFile.ThrowIfInconsistent"/> does not 
	 throw on <paramref name="rff"/>.
	 * @throws IOException 
	 
	*/
	public static void Write(DataOutputStream writer, RawFlatFile rff) throws IOException
	{
		
		
		// Header: version & size information
		//Write(writer, VersionNumber); 
		writer.writeByte(VersionNumber);
		writer.write(uInt16(rff.Columns));
		writer.write(uInt32(rff.getCells().size()));
		writer.write(uInt32(rff.Content.size()));
		
		// Cell data: integer references to indices in 'content'
		for (int cell : rff.getCells())
		{
			WriteInt(writer, cell);
		}
		
		
		// Content data: byte arrays of specific sizes.
		for (byte[] bytes : rff.Content)
		{
			WriteInt(writer, bytes.length);
			writer.write(bytes);
			
			// withoud this BufferedOutputStream drop a few bytes
			writer.flush();
		}
	}

	/**  Reads a raw flat-file written by <see cref="Write"/>. 
	 * @throws Exception 
	*/
	public static RawFlatFile ReadRawFlatFile(DataInputStream reader) throws Exception
	{
		// Header: version & size information
		byte version =reader.readByte();
		if (version != VersionNumber)
		{
			throw new Exception(String.format("Unknown version number %1$s.", version));
		}

		
		// ReadUInt16();
		int c1 = reader.read();
		int c2 = reader.read();
		int columns = (c1 << 8) | c2;

		
		int cellCount = (int)reader.read();//ReadUInt32();
		int contentCount = (int)reader.read(); //ReadUInt32();

		java.util.ArrayList<Integer> cells = new java.util.ArrayList<Integer>(cellCount);
		for (int i = 0; i < cellCount; ++i)
		{
			cells.add(ReadInt(reader));
		}

		List<byte[]> content = new ArrayList<byte[]>();
		for (int i = 0; i < contentCount; ++i)
		{
			byte[] arr = new byte[ReadInt(reader)];
			reader.read(arr, 0, ReadInt(reader));
			content.add(i, arr);
		}

		return new RawFlatFile(columns, cells, content);
	}

	/**  Writes an integer using just enough bytes. 
	 
	 Uses a single byte if 7 bits are enough. 
	 Uses two bytes if 14 bytes are enough.
	 Uses three bytes if 21 bytes are enough.
	 Uses four bytes if 28 bytes are enough.
	 Uses five bytes above that.
	 
	 <see cref="ReadInt"/>
	 * @throws IOException 
	*/
	
	
	
	private static void WriteInt(DataOutputStream writer, int value) throws IOException
	{
		final int topBit = 1 << 7;
		while (value >= topBit)
		{
			writer.writeByte((byte)(topBit + value % topBit));
			value = value >> 7;
		
		}

		writer.write(uByte((byte) value));
	}

	/**  Reads an integer written with <see cref="WriteInt"/>. 
	 * @throws IOException 
	*/
	private static int ReadInt(DataInputStream reader) throws IOException
	{
		final int topBit = 1 << 7;
		int b = topBit;
		int i = 0;

		for (int offset = 0; b >= topBit; offset += 7)
		{
			b = reader.readByte();
			i += (b % topBit) << offset;
		}

		return i;
	}
	
	public static byte[] uInt16(int value) {
		byte[] buffer = new byte[2];
		buffer[1] = (byte) ((value >>> 8) & 0xff);
        buffer[0] = (byte) ((value >>> 0) & 0xff);
        return buffer;
	}
	
	public static byte[] uInt32(int value) {
		byte[] buffer = new byte[4];
		buffer[3] = (byte) ((value >>> 24) & 0xff);
    	buffer[2] = (byte) ((value >>> 16) & 0xff);
        buffer[1] = (byte) ((value >>> 8) & 0xff);
        buffer[0] = (byte) ((value >>> 0) & 0xff);
		return buffer;
	}
	
	public static byte[] int32(int value) {
		byte[] buffer = new byte[4];
		buffer[3] = (byte) ((value >>> 24));
    	buffer[2] = (byte) ((value >>> 16));
        buffer[1] = (byte) ((value >>> 8));
        buffer[0] = (byte) ((value >>> 0));
		return buffer;
	}
	
	public static byte[] uByte(byte value ) {
		byte[] buffer = new byte[1];
		buffer[0] = (byte) ((value >>> 0) & 0xff);
		return buffer;
	}
}